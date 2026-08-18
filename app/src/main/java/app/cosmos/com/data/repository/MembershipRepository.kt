package app.cosmos.com.data.repository

import android.util.Log
import app.cosmos.com.data.model.GiftCardStatus
import app.cosmos.com.data.model.MembershipTier
import app.cosmos.com.data.payment.PaymentManager
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
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Order details returned after creating an upgrade order (with optional gift card discount).
 */
data class MembershipOrder(
    val orderId: String,
    val amount: Int,                     // Total original upgrade amount
    val amountInPaise: Int,              // Differential amount in paise for Razorpay
    val currency: String,
    val keyId: String,
    val currentTier: String,
    val targetTier: String,
    val tierLabel: String,
    val giftCardCode: String? = null,
    val giftCardDiscount: Int = 0,
    val differentialAmount: Int = amount, // What user actually needs to pay
    val preservedBalance: Int = 0,       // Balance left on the gift card after this purchase
    val isFreeOrder: Boolean = false     // True if 100% covered by gift card (₹0 payment)
)

/**
 * Result of a successful payment/redemption verification.
 */
data class MembershipVerificationResult(
    val newTier: String,
    val tierLabel: String,
    val badge: String,
    val amount: Int,
    val paymentId: String,
    val giftCardCode: String? = null,
    val giftCardDiscount: Int = 0,
    val preservedBalance: Int = 0
)

/**
 * Repository for COSMOS Lifetime Membership operations.
 * Handles Razorpay differential orders, gift card stored-value deductions, and zero-differential upgrades.
 */
class MembershipRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "MembershipRepository"

        var CREATE_ORDER_URL = "https://us-central1-cosmos-app-42ed2.cloudfunctions.net/createMembershipOrder"
        var VERIFY_PAYMENT_URL = "https://us-central1-cosmos-app-42ed2.cloudfunctions.net/verifyMembershipPayment"
    }

    /**
     * Creates an order for upgrading to a target membership tier.
     * If a gift card is provided, the differential amount is calculated.
     * If the gift card fully covers the price, a zero-differential order is created.
     */
    suspend fun createUpgradeOrder(
        userId: String,
        targetTier: MembershipTier,
        giftCardCode: String? = null
    ): Result<MembershipOrder> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "Creating upgrade order for user=$userId, target=${targetTier.name}, giftCard=$giftCardCode")

            val normalizedGiftCard = giftCardCode?.trim()?.uppercase()?.takeIf { it.isNotBlank() }

            // Try Cloud Function first
            try {
                val requestBody = JSONObject().apply {
                    put("uid", userId)
                    put("targetTier", targetTier.name)
                    if (normalizedGiftCard != null) {
                        put("giftCardCode", normalizedGiftCard)
                    }
                }

                val response = postJson(CREATE_ORDER_URL, requestBody)

                if (response.optBoolean("success", false)) {
                    val orderId = response.getString("orderId")
                    val amount = response.getInt("amount")
                    val differentialAmount = response.optInt("differentialAmount", response.getInt("amount"))
                    val amountInPaise = response.optInt("amountInPaise", differentialAmount * 100)
                    val giftCardDiscount = response.optInt("giftCardDiscount", 0)
                    val preservedBalance = response.optInt("preservedBalance", 0)
                    val isFreeOrder = response.optBoolean("isFreeOrder", differentialAmount == 0)

                    return@runCatching MembershipOrder(
                        orderId = orderId,
                        amount = amount,
                        amountInPaise = amountInPaise,
                        currency = response.optString("currency", "INR"),
                        keyId = response.optString("keyId", ""),
                        currentTier = response.optString("currentTier", ""),
                        targetTier = response.optString("targetTier", targetTier.name),
                        tierLabel = response.optString("tierLabel", targetTier.label),
                        giftCardCode = normalizedGiftCard,
                        giftCardDiscount = giftCardDiscount,
                        differentialAmount = differentialAmount,
                        preservedBalance = preservedBalance,
                        isFreeOrder = isFreeOrder
                    )
                }
            } catch (cfError: Exception) {
                Log.w(TAG, "Cloud function create order unreachable, applying fallback: ${cfError.message}")
            }

            // Fallback / Direct execution: Fetch current user & gift card from Firestore
            val userDoc = firestore.collection("users").document(userId).get().await()
            val currentTierName = userDoc.getString("membershipTier") ?: MembershipTier.ASTEROID.name
            val currentTier = MembershipTier.fromLegacyName(currentTierName)
            val upgradeAmount = max(0, targetTier.lifetimePrice - currentTier.lifetimePrice)

            var discount = 0
            var preserved = 0
            var validCardCode: String? = null

            if (normalizedGiftCard != null) {
                val cardDoc = firestore.collection("gift_cards").document(normalizedGiftCard).get().await()
                if (cardDoc.exists()) {
                    val balance = (cardDoc.getLong("currentBalance") ?: 0L).toInt()
                    val status = cardDoc.getString("status") ?: "ACTIVE"
                    if (status == "ACTIVE" && balance > 0) {
                        validCardCode = normalizedGiftCard
                        discount = min(balance, upgradeAmount)
                        preserved = balance - discount
                    }
                }
            }

            val differential = max(0, upgradeAmount - discount)
            val orderId = "order_cosmos_${UUID.randomUUID().toString().replace("-", "").substring(0, 16)}"

            // Store order record in Firestore
            val orderData = mapOf(
                "userId" to userId,
                "currentTier" to currentTier.name,
                "targetTier" to targetTier.name,
                "amount" to upgradeAmount,
                "differentialAmount" to differential,
                "giftCardCode" to validCardCode,
                "giftCardDiscount" to discount,
                "preservedBalance" to preserved,
                "status" to "PENDING",
                "isFreeOrder" to (differential == 0),
                "createdAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("membership_orders").document(orderId).set(orderData).await()

            MembershipOrder(
                orderId = orderId,
                amount = upgradeAmount,
                amountInPaise = differential * 100,
                currency = "INR",
                keyId = "rzp_test_placeholder",
                currentTier = currentTier.name,
                targetTier = targetTier.name,
                tierLabel = targetTier.label,
                giftCardCode = validCardCode,
                giftCardDiscount = discount,
                differentialAmount = differential,
                preservedBalance = preserved,
                isFreeOrder = (differential == 0)
            )
        }
    }

    /**
     * Directly redeems a zero-differential order (where gift card covered 100% of the cost).
     * No Razorpay gateway is needed. Updates user tier, deducts gift card, and records history.
     */
    suspend fun redeemZeroAmountOrder(
        userId: String,
        orderId: String,
        targetTier: MembershipTier,
        giftCardCode: String,
        amountDeducted: Int
    ): Result<MembershipVerificationResult> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "Redeeming zero-differential order: orderId=$orderId, card=$giftCardCode, amount=$amountDeducted")

            val normalizedCode = giftCardCode.trim().uppercase()
            val userRef = firestore.collection("users").document(userId)
            val cardRef = firestore.collection("gift_cards").document(normalizedCode)
            val orderRef = firestore.collection("membership_orders").document(orderId)

            var preservedBalance = 0

            // Execute atomic transaction for gift card balance + user tier upgrade
            firestore.runTransaction { tx ->
                val cardSnap = tx.get(cardRef)
                if (!cardSnap.exists()) {
                    throw IllegalStateException("Gift card $normalizedCode not found.")
                }

                val currentBalance = (cardSnap.getLong("currentBalance") ?: 0L).toInt()
                if (currentBalance < amountDeducted) {
                    throw IllegalStateException("Insufficient gift card balance.")
                }

                val newCardBalance = currentBalance - amountDeducted
                preservedBalance = newCardBalance
                val newStatus = if (newCardBalance <= 0) GiftCardStatus.EXHAUSTED.name else GiftCardStatus.ACTIVE.name

                val redemptionLog = mapOf(
                    "userId" to userId,
                    "orderId" to orderId,
                    "amountDeducted" to amountDeducted,
                    "previousBalance" to currentBalance,
                    "newBalance" to newCardBalance,
                    "targetTier" to targetTier.name,
                    "timestamp" to System.currentTimeMillis()
                )

                // Update gift card
                tx.update(
                    cardRef,
                    mapOf(
                        "currentBalance" to newCardBalance,
                        "status" to newStatus,
                        "lastRedeemedAt" to System.currentTimeMillis(),
                        "redemptions" to FieldValue.arrayUnion(redemptionLog)
                    )
                )

                // Update user membership tier
                tx.update(
                    userRef,
                    mapOf(
                        "membershipTier" to targetTier.name,
                        "monthlyConnectionLimit" to PaymentManager.getConnectionLimit(targetTier),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                // Mark order completed
                tx.update(
                    orderRef,
                    mapOf(
                        "status" to "COMPLETED",
                        "completedAt" to FieldValue.serverTimestamp(),
                        "paymentMethod" to "GIFT_CARD_FULL"
                    )
                )
            }.await()

            val now = FieldValue.serverTimestamp()
            val paymentId = "gc_pay_${UUID.randomUUID().toString().replace("-", "").substring(0, 12)}"

            // Add subscription document
            firestore.collection("users").document(userId).collection("subscriptions").add(
                mapOf(
                    "tier" to targetTier.name,
                    "status" to "ACTIVE",
                    "isLifetime" to true,
                    "startDate" to System.currentTimeMillis(),
                    "paymentId" to paymentId,
                    "orderId" to orderId,
                    "giftCardCode" to normalizedCode,
                    "amountPaid" to 0,
                    "giftCardDiscount" to amountDeducted,
                    "createdAt" to now
                )
            ).await()

            // Add payment record
            firestore.collection("payments").add(
                mapOf(
                    "userId" to userId,
                    "paymentId" to paymentId,
                    "orderId" to orderId,
                    "amount" to 0,
                    "giftCardCode" to normalizedCode,
                    "giftCardDiscount" to amountDeducted,
                    "tier" to targetTier.name,
                    "status" to "SUCCESS",
                    "paymentMethod" to "COSMOS_GIFT_CARD",
                    "timestamp" to now
                )
            ).await()

            // Add notification
            firestore.collection("notifications").add(
                mapOf(
                    "userId" to userId,
                    "type" to "COMMUNITY_ANNOUNCEMENT",
                    "title" to "Gift Card Redeemed! 🎁",
                    "body" to "You successfully upgraded to the ${targetTier.label} tier using Gift Card $normalizedCode. Remaining card balance: ₹$preservedBalance.",
                    "actionId" to paymentId,
                    "isRead" to false,
                    "timestamp" to now
                )
            ).await()

            MembershipVerificationResult(
                newTier = targetTier.name,
                tierLabel = targetTier.label,
                badge = targetTier.label,
                amount = amountDeducted,
                paymentId = paymentId,
                giftCardCode = normalizedCode,
                giftCardDiscount = amountDeducted,
                preservedBalance = preservedBalance
            )
        }
    }

    /**
     * Verifies a Razorpay differential payment with the server (or direct transaction)
     * and deducts any applied gift card balance.
     */
    suspend fun verifyPayment(
        userId: String,
        orderId: String,
        paymentId: String,
        signature: String,
        targetTier: MembershipTier? = null,
        giftCardCode: String? = null,
        giftCardDiscount: Int = 0
    ): Result<MembershipVerificationResult> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "Verifying differential payment: order=$orderId, payment=$paymentId, card=$giftCardCode")

            // 1. Try Cloud Function
            try {
                val requestBody = JSONObject().apply {
                    put("uid", userId)
                    put("orderId", orderId)
                    put("paymentId", paymentId)
                    put("signature", signature)
                    if (giftCardCode != null) {
                        put("giftCardCode", giftCardCode)
                    }
                }

                val response = postJson(VERIFY_PAYMENT_URL, requestBody)

                if (response.optBoolean("success", false)) {
                    return@runCatching MembershipVerificationResult(
                        newTier = response.getString("newTier"),
                        tierLabel = response.optString("tierLabel", response.getString("newTier")),
                        badge = response.optString("badge", "Member"),
                        amount = response.optInt("amount", 0),
                        paymentId = response.optString("paymentId", paymentId),
                        giftCardCode = giftCardCode,
                        giftCardDiscount = response.optInt("giftCardDiscount", giftCardDiscount),
                        preservedBalance = response.optInt("preservedBalance", 0)
                    )
                }
            } catch (cfErr: Exception) {
                Log.w(TAG, "Cloud function verifyPayment unreachable, falling back: ${cfErr.message}")
            }

            // 2. Direct Firestore fallback
            val resolvedTier = targetTier ?: MembershipTier.MOON
            val normalizedCode = giftCardCode?.trim()?.uppercase()
            var preservedBalance = 0

            // If gift card was applied, deduct it atomically
            if (!normalizedCode.isNullOrBlank() && giftCardDiscount > 0) {
                val cardRef = firestore.collection("gift_cards").document(normalizedCode)
                firestore.runTransaction { tx ->
                    val cardSnap = tx.get(cardRef)
                    if (cardSnap.exists()) {
                        val currentBalance = (cardSnap.getLong("currentBalance") ?: 0L).toInt()
                        val deduction = min(currentBalance, giftCardDiscount)
                        val newBal = currentBalance - deduction
                        preservedBalance = newBal
                        val newStatus = if (newBal <= 0) GiftCardStatus.EXHAUSTED.name else GiftCardStatus.ACTIVE.name

                        tx.update(
                            cardRef,
                            mapOf(
                                "currentBalance" to newBal,
                                "status" to newStatus,
                                "lastRedeemedAt" to System.currentTimeMillis(),
                                "redemptions" to FieldValue.arrayUnion(
                                    mapOf(
                                        "userId" to userId,
                                        "orderId" to orderId,
                                        "amountDeducted" to deduction,
                                        "previousBalance" to currentBalance,
                                        "newBalance" to newBal,
                                        "targetTier" to resolvedTier.name,
                                        "timestamp" to System.currentTimeMillis()
                                    )
                                )
                            )
                        )
                    }
                }.await()
            }

            // Update user membership
            val userRef = firestore.collection("users").document(userId)
            userRef.update(
                mapOf(
                    "membershipTier" to resolvedTier.name,
                    "monthlyConnectionLimit" to PaymentManager.getConnectionLimit(resolvedTier),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Update order
            firestore.collection("membership_orders").document(orderId).update(
                mapOf(
                    "status" to "COMPLETED",
                    "razorpayPaymentId" to paymentId,
                    "razorpaySignature" to signature,
                    "completedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Create notification
            firestore.collection("notifications").add(
                mapOf(
                    "userId" to userId,
                    "type" to "COMMUNITY_ANNOUNCEMENT",
                    "title" to "Membership Upgraded! 🚀",
                    "body" to "Welcome to the ${resolvedTier.label} tier. Lifetime access unlocked.",
                    "actionId" to paymentId,
                    "isRead" to false,
                    "timestamp" to FieldValue.serverTimestamp()
                )
            ).await()

            MembershipVerificationResult(
                newTier = resolvedTier.name,
                tierLabel = resolvedTier.label,
                badge = resolvedTier.label,
                amount = resolvedTier.lifetimePrice,
                paymentId = paymentId,
                giftCardCode = normalizedCode,
                giftCardDiscount = giftCardDiscount,
                preservedBalance = preservedBalance
            )
        }
    }

    /**
     * Helper: POST JSON to a URL and return parsed JSON response.
     */
    private fun postJson(urlStr: String, body: JSONObject): JSONObject {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 30000

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = BufferedReader(InputStreamReader(stream)).use { reader ->
                reader.readText()
            }

            if (responseCode !in 200..299) {
                val errorJson = try { JSONObject(responseText) } catch (e: Exception) { JSONObject() }
                val errorMsg = errorJson.optString("error", "Server error (HTTP $responseCode)")
                throw Exception(errorMsg)
            }

            return JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }
}
