package app.cosmos.com.data.payment

import android.app.Activity
import android.util.Log
import com.razorpay.Checkout
import org.json.JSONObject

object RazorpayPaymentHelper {
    private const val TAG = "RazorpayPaymentHelper"

    // Dynamic callbacks for the active UI checkout session
    var onPaymentSuccess: ((paymentId: String, orderId: String, signature: String) -> Unit)? = null
    var onPaymentError: ((code: Int, message: String) -> Unit)? = null

    fun registerCallbacks(
        onSuccess: (paymentId: String, orderId: String, signature: String) -> Unit,
        onError: (code: Int, message: String) -> Unit
    ) {
        onPaymentSuccess = onSuccess
        onPaymentError = onError
    }

    fun clearCallbacks() {
        onPaymentSuccess = null
        onPaymentError = null
    }

    /**
     * Maps Razorpay error codes to user-friendly messages.
     */
    fun getReadableError(code: Int, rawMessage: String): String {
        return when (code) {
            0 -> "Network error. Please check your internet connection and try again."
            1 -> "Payment was cancelled. No amount has been charged."
            2 -> "Payment processing failed. Please try a different payment method."
            3 -> "Payment timed out. Please try again."
            4 -> "Your bank declined the transaction. Please contact your bank or try another method."
            else -> if (rawMessage.isNotBlank()) rawMessage else "Something went wrong. Please try again."
        }
    }

    /**
     * Helper to start payment without an explicit orderId (for demo/fallback).
     * In production, always use startPaymentWithOrder with a server-generated order ID.
     */
    fun startPayment(
        activity: Activity,
        amountInInr: Int,
        tierName: String,
        userEmail: String,
        userContact: String,
        userId: String = "",
        currentTier: String = ""
    ) {
        // For development/demo purposes, we use a placeholder order ID.
        // In a real production environment, you would fetch this from your backend.
        startPaymentWithOrder(
            activity = activity,
            orderId = "order_demo_${System.currentTimeMillis()}",
            amountInInr = amountInInr,
            keyId = "rzp_test_placeholder", // Replace with your actual Razorpay Key ID
            tierName = tierName,
            userEmail = userEmail,
            userContact = userContact,
            userId = userId,
            currentTier = currentTier
        )
    }

    /**
     * Initiates Razorpay payment using a server-created order.
     * This is the SECURE flow — the order (with correct amount) is created by the
     * Cloud Function, not by the client.
     *
     * @param activity The Android Activity for Razorpay SDK
     * @param orderId The Razorpay Order ID from createMembershipOrder Cloud Function
     * @param amountInInr The amount in INR (for display purposes only — server controls actual amount)
     * @param keyId The Razorpay Key ID from the server
     * @param tierName Target tier name for display
     * @param userEmail User's email for prefill
     * @param userContact User's contact for prefill
     * @param userId User ID for tracking
     * @param currentTier Current tier for tracking
     */
    fun startPaymentWithOrder(
        activity: Activity,
        orderId: String,
        amountInInr: Int,
        keyId: String,
        tierName: String,
        userEmail: String,
        userContact: String,
        userId: String = "",
        currentTier: String = ""
    ) {
        if (keyId.isBlank()) {
            Log.w(TAG, "Razorpay Key ID is empty!")
            onPaymentError?.invoke(-1, "Payment configuration error. Please try again later.")
            return
        }

        if (orderId.isBlank()) {
            Log.w(TAG, "Razorpay Order ID is empty!")
            onPaymentError?.invoke(-1, "Order creation failed. Please try again.")
            return
        }

        val amountInPaise = amountInInr.toLong() * 100

        val checkout = Checkout()
        checkout.setKeyID(keyId)

        try {
            val options = JSONObject()
            options.put("name", "COSMOS")
            options.put("description", "$tierName — Lifetime Membership")
            options.put("image", "https://s3.amazonaws.com/rzp-mobile/images/rzp.png")
            options.put("currency", "INR")
            options.put("amount", amountInPaise)
            options.put("order_id", orderId)

            // Theme customization — cosmic dark theme
            val theme = JSONObject()
            theme.put("color", "#6C63FF")
            theme.put("backdrop_color", "#0A0A1E")
            options.put("theme", theme)

            // Prefill user details
            val prefill = JSONObject()
            prefill.put("email", userEmail.ifBlank { "user@cosmos.app" })
            prefill.put("contact", userContact.ifBlank { "" })
            options.put("prefill", prefill)

            // Notes for tracking in Razorpay dashboard
            val notes = JSONObject()
            notes.put("tier_name", tierName)
            notes.put("user_id", userId)
            notes.put("upgrade_from", currentTier)
            notes.put("plan_type", "lifetime")
            notes.put("source", "cosmos_android_app")
            options.put("notes", notes)

            // Retry configuration
            val retry = JSONObject()
            retry.put("enabled", true)
            retry.put("max_count", 3)
            options.put("retry", retry)

            Log.d(TAG, "Launching Razorpay Checkout for $tierName — ₹$amountInInr (order: $orderId)")
            checkout.open(activity, options)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching Razorpay Checkout SDK", e)
            onPaymentError?.invoke(-1, e.message ?: "Failed to initialize Razorpay Checkout SDK")
        }
    }
}
