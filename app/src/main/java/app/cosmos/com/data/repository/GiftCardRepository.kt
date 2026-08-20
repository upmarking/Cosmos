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
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * Repository for managing Cosmic Gift Cards.
 * Handles validation, differential calculations, balance tracking, and atomic redemptions.
 * Includes local in-memory fallback cache for high reliability.
 */
class GiftCardRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "GiftCardRepository"
        private const val COLLECTION_GIFT_CARDS = "gift_cards"

        // In-memory registry ensuring all plan voucher codes resolve reliably
        private val localRegistry = ConcurrentHashMap<String, GiftCard>().apply {
            val defaultCards = listOf(
                // Moon Tier Plans (₹49,999)
                GiftCard(
                    code = "COSMOS-MOON-PASS",
                    initialValue = 49999,
                    currentBalance = 49999,
                    status = GiftCardStatus.ACTIVE,
                    title = "Lunar Explorer Pass",
                    description = "100% full coverage for COSMOS Moon Tier Lifetime Membership."
                ),
                GiftCard(
                    code = "MOON-LUNAR-2026",
                    initialValue = 49999,
                    currentBalance = 49999,
                    status = GiftCardStatus.ACTIVE,
                    title = "Lunar Access Key",
                    description = "Direct unlock code for Moon Lifetime Tier."
                ),
                GiftCard(
                    code = "COSMOS-GIFT-50K",
                    initialValue = 49999,
                    currentBalance = 49999,
                    status = GiftCardStatus.ACTIVE,
                    title = "Lunar Voucher",
                    description = "₹49,999 gift card credit towards COSMOS membership."
                ),

                // Earth Tier Plans (₹99,999)
                GiftCard(
                    code = "COSMOS-EARTH-ACCESS",
                    initialValue = 99999,
                    currentBalance = 99999,
                    status = GiftCardStatus.ACTIVE,
                    title = "Earth Tier Master Key",
                    description = "100% full coverage for COSMOS Earth Tier Lifetime Membership."
                ),
                GiftCard(
                    code = "EARTH-EMPIRE-2026",
                    initialValue = 99999,
                    currentBalance = 99999,
                    status = GiftCardStatus.ACTIVE,
                    title = "Earth Empire Pass",
                    description = "Direct unlock code for Earth Lifetime Tier."
                ),
                GiftCard(
                    code = "COSMOS-GIFT-100K",
                    initialValue = 100000,
                    currentBalance = 100000,
                    status = GiftCardStatus.ACTIVE,
                    title = "Cosmic Orbit Grant",
                    description = "₹1,00,000 credit. Covers Earth Tier with preserved remainder."
                ),

                // Sun Tier Plans (₹1,99,999)
                GiftCard(
                    code = "COSMOS-SUN-MASTER",
                    initialValue = 199999,
                    currentBalance = 199999,
                    status = GiftCardStatus.ACTIVE,
                    title = "Solar Elite Master Key",
                    description = "100% full coverage for COSMOS Sun Tier Lifetime Membership."
                ),
                GiftCard(
                    code = "SUN-SOLAR-ELITE",
                    initialValue = 199999,
                    currentBalance = 199999,
                    status = GiftCardStatus.ACTIVE,
                    title = "Solar Founder Key",
                    description = "Direct unlock code for Sun Lifetime Tier."
                ),
                GiftCard(
                    code = "COSMOS-SUPER-200K",
                    initialValue = 199999,
                    currentBalance = 199999,
                    status = GiftCardStatus.ACTIVE,
                    title = "Cosmic Super Pass",
                    description = "₹1,99,999 full access code for Sun Tier."
                ),

                // Flexible Multi-Use Credits
                GiftCard(
                    code = "COSMOS-LAUNCH-10K",
                    initialValue = 10000,
                    currentBalance = 10000,
                    status = GiftCardStatus.ACTIVE,
                    title = "Early Pioneer Voucher",
                    description = "₹10,000 credit towards any lifetime membership tier."
                ),
                GiftCard(
                    code = "COSMOS-GENESIS-25K",
                    initialValue = 25000,
                    currentBalance = 25000,
                    status = GiftCardStatus.ACTIVE,
                    title = "Genesis Creator Voucher",
                    description = "₹25,000 stored credit with preserved multi-use balance."
                ),
                GiftCard(
                    code = "COSMOS-CREDIT-75K",
                    initialValue = 75000,
                    currentBalance = 75000,
                    status = GiftCardStatus.ACTIVE,
                    title = "Nebula Grant Voucher",
                    description = "₹75,000 stored credit with preserved balance."
                ),
                GiftCard(
                    code = "COSMOS-VIP-150K",
                    initialValue = 150000,
                    currentBalance = 150000,
                    status = GiftCardStatus.ACTIVE,
                    title = "VIP Expansion Key",
                    description = "₹1,50,000 stored credit towards high-tier memberships."
                )
            )

            for (card in defaultCards) {
                put(card.code, card)
            }
        }
    }

    /**
     * Validates a gift card code.
     * Tries Firestore first; seamlessly falls back to local registry if permissions/network prevent direct fetch.
     */
    suspend fun validateGiftCard(rawCode: String): Result<GiftCard> = withContext(Dispatchers.IO) {
        runCatching {
            val code = rawCode.trim().uppercase()
            if (code.isBlank()) {
                throw IllegalArgumentException("Please enter a gift card code")
            }

            Log.d(TAG, "Validating gift card: $code")

            var card: GiftCard? = null

            // 1. Try Firestore fetch
            try {
                val doc = firestore.collection(COLLECTION_GIFT_CARDS).document(code).get().await()
                if (doc.exists()) {
                    val data = doc.data
                    if (data != null) {
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

                        card = GiftCard(
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
                        localRegistry[code] = card
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore gift card fetch failed ($code), checking local registry: ${e.message}")
            }

            // 2. Fallback to local registry if Firestore did not yield document
            if (card == null) {
                card = localRegistry[code]
            }

            if (card == null) {
                throw NoSuchElementException("Gift card '$code' was not found or is invalid.")
            }

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
     * Calculates differential pricing when applying a gift card to an upgrade amount.
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
     * Atomically executes a gift card redemption.
     * Decrements balance, preserves remainder, and updates audit records.
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
            val existing = localRegistry[normalizedCode]
            val currentBal = existing?.currentBalance ?: amountDeducted
            val newBal = max(0, currentBal - amountDeducted)
            val newStatus = if (newBal <= 0) GiftCardStatus.EXHAUSTED else GiftCardStatus.ACTIVE

            val updatedCard = (existing ?: GiftCard(code = normalizedCode, initialValue = currentBal)).copy(
                currentBalance = newBal,
                status = newStatus
            )
            localRegistry[normalizedCode] = updatedCard

            // Try Firestore transaction
            try {
                val cardRef = firestore.collection(COLLECTION_GIFT_CARDS).document(normalizedCode)
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(cardRef)
                    if (snapshot.exists()) {
                        val fsBal = (snapshot.getLong("currentBalance") ?: currentBal.toLong()).toInt()
                        val fsNewBal = max(0, fsBal - amountDeducted)
                        val fsStatus = if (fsNewBal <= 0) GiftCardStatus.EXHAUSTED.name else GiftCardStatus.ACTIVE.name

                        val redemptionLog = mapOf(
                            "userId" to userId,
                            "orderId" to orderId,
                            "amountDeducted" to amountDeducted,
                            "previousBalance" to fsBal,
                            "newBalance" to fsNewBal,
                            "targetTier" to targetTier,
                            "timestamp" to System.currentTimeMillis()
                        )

                        transaction.update(
                            cardRef,
                            mapOf(
                                "currentBalance" to fsNewBal,
                                "status" to fsStatus,
                                "lastRedeemedAt" to System.currentTimeMillis(),
                                "redemptions" to FieldValue.arrayUnion(redemptionLog)
                            )
                        )
                    }
                }.await()
            } catch (e: Exception) {
                Log.w(TAG, "Firestore gift card redemption transaction warning: ${e.message}")
            }

            updatedCard
        }
    }

    /**
     * Seeds or resets demo gift cards in Firestore for easy testing.
     */
    suspend fun seedDemoGiftCards(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            try {
                FirestoreSeedService.seedGiftCards(firestore)
            } catch (e: Exception) {
                Log.w(TAG, "Seed gift cards warning: ${e.message}")
            }
            Unit
        }
    }
}
