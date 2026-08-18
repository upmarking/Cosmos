package app.cosmos.com.data.repository

import android.util.Log
import app.cosmos.com.data.model.GiftCard
import app.cosmos.com.data.model.GiftCardApplyResult
import app.cosmos.com.data.model.GiftCardRedemption
import app.cosmos.com.data.model.GiftCardStatus
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min

/**
 * Repository for managing Cosmic Gift Cards.
 * Handles validation, differential calculations, balance tracking, and atomic redemptions.
 */
class GiftCardRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "GiftCardRepository"
        private const val COLLECTION_GIFT_CARDS = "gift_cards"

        var VALIDATE_GIFT_CARD_URL = "https://us-central1-cosmos-app-42ed2.cloudfunctions.net/validateGiftCard"
    }

    /**
     * Validates a gift card code by checking Firestore.
     * Normalizes the code (trimmed, uppercase).
     */
    suspend fun validateGiftCard(rawCode: String): Result<GiftCard> = withContext(Dispatchers.IO) {
        runCatching {
            val code = rawCode.trim().uppercase()
            if (code.isBlank()) {
                throw IllegalArgumentException("Please enter a gift card code")
            }

            Log.d(TAG, "Validating gift card: $code")

            // 1. First attempt direct Firestore fetch
            val doc = firestore.collection(COLLECTION_GIFT_CARDS).document(code).get().await()

            if (!doc.exists()) {
                throw NoSuchElementException("Gift card '$code' was not found or is invalid.")
            }

            val data = doc.data ?: throw IllegalStateException("Empty gift card record")

            val rawStatus = data["status"] as? String ?: "ACTIVE"
            val status = try {
                GiftCardStatus.valueOf(rawStatus)
            } catch (e: Exception) {
                GiftCardStatus.ACTIVE
            }

            val initialValue = (data["initialValue"] as? Number)?.toInt() ?: 0
            val currentBalance = (data["currentBalance"] as? Number)?.toInt() ?: 0
            val currency = data["currency"] as? String ?: "INR"
            val title = data["title"] as? String ?: "Cosmic Gift Voucher"
            val description = data["description"] as? String ?: "Redeemable towards COSMOS membership"
            val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            val expiresAt = (data["expiresAt"] as? Number)?.toLong()

            val card = GiftCard(
                code = code,
                initialValue = initialValue,
                currentBalance = currentBalance,
                currency = currency,
                status = status,
                title = title,
                description = description,
                createdAt = createdAt,
                expiresAt = expiresAt
            )

            if (!card.isRedeemable) {
                when {
                    card.currentBalance <= 0 -> throw IllegalStateException("This gift card has already been fully redeemed (Balance: ₹0).")
                    card.status == GiftCardStatus.EXHAUSTED -> throw IllegalStateException("This gift card is exhausted.")
                    card.status == GiftCardStatus.EXPIRED -> throw IllegalStateException("This gift card has expired.")
                    card.status == GiftCardStatus.DISABLED -> throw IllegalStateException("This gift card is currently disabled.")
                    card.expiresAt != null && card.expiresAt < System.currentTimeMillis() -> throw IllegalStateException("This gift card has expired.")
                    else -> throw IllegalStateException("This gift card cannot be redeemed at this time.")
                }
            }

            card
        }
    }

    /**
     * Calculates the differential pricing when applying a gift card to an upgrade amount.
     *
     * Rules:
     * 1. If GiftCard balance >= upgradeAmount:
     *    - appliedDiscount = upgradeAmount
     *    - differentialAmountToPay = 0
     *    - preservedRemainingBalance = currentBalance - upgradeAmount
     *    - isFullyCovered = true
     * 2. If GiftCard balance < upgradeAmount:
     *    - appliedDiscount = currentBalance
     *    - differentialAmountToPay = upgradeAmount - currentBalance
     *    - preservedRemainingBalance = 0
     *    - isFullyCovered = false
     */
    fun calculateApplication(card: GiftCard, upgradeAmount: Int): GiftCardApplyResult {
        val discount = min(card.currentBalance, upgradeAmount)
        val differential = max(0, upgradeAmount - discount)
        val preserved = max(0, card.currentBalance - discount)
        val fullyCovered = differential == 0

        return GiftCardApplyResult(
            card = card,
            originalUpgradeAmount = upgradeAmount,
            appliedDiscount = discount,
            differentialAmountToPay = differential,
            preservedRemainingBalance = preserved,
            isFullyCovered = fullyCovered
        )
    }

    /**
     * Atomically executes a gift card redemption on Firestore.
     * Decrements the card's balance by amountDeducted, preserves the remainder,
     * updates status to EXHAUSTED if balance reaches 0, and records the redemption audit log.
     */
    suspend fun redeemGiftCard(
        code: String,
        userId: String,
        amountDeducted: Int,
        orderId: String,
        targetTier: String
    ): Result<GiftCard> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedCode = code.trim().uppercase()
            val cardRef = firestore.collection(COLLECTION_GIFT_CARDS).document(normalizedCode)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(cardRef)
                if (!snapshot.exists()) {
                    throw IllegalStateException("Gift card $normalizedCode not found.")
                }

                val currentBalance = (snapshot.getLong("currentBalance") ?: 0L).toInt()
                if (currentBalance < amountDeducted) {
                    throw IllegalStateException("Insufficient gift card balance. Available: ₹$currentBalance, Attempted: ₹$amountDeducted")
                }

                val newBalance = currentBalance - amountDeducted
                val newStatus = if (newBalance <= 0) GiftCardStatus.EXHAUSTED.name else GiftCardStatus.ACTIVE.name

                val redemptionLog = mapOf(
                    "userId" to userId,
                    "orderId" to orderId,
                    "amountDeducted" to amountDeducted,
                    "previousBalance" to currentBalance,
                    "newBalance" to newBalance,
                    "targetTier" to targetTier,
                    "timestamp" to System.currentTimeMillis()
                )

                transaction.update(
                    cardRef,
                    mapOf(
                        "currentBalance" to newBalance,
                        "status" to newStatus,
                        "lastRedeemedAt" to System.currentTimeMillis(),
                        "redemptions" to FieldValue.arrayUnion(redemptionLog)
                    )
                )

                GiftCard(
                    code = normalizedCode,
                    initialValue = (snapshot.getLong("initialValue") ?: currentBalance.toLong()).toInt(),
                    currentBalance = newBalance,
                    currency = snapshot.getString("currency") ?: "INR",
                    status = if (newBalance <= 0) GiftCardStatus.EXHAUSTED else GiftCardStatus.ACTIVE,
                    title = snapshot.getString("title") ?: "Cosmic Gift Voucher",
                    description = snapshot.getString("description") ?: ""
                )
            }.await()
        }
    }

    /**
     * Seeds or resets demo gift cards in Firestore for easy testing.
     */
    suspend fun seedDemoGiftCards(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            FirestoreSeedService.seedGiftCards(firestore)
        }
    }
}
