package app.cosmos.com.data.payment

import android.app.Activity
import android.util.Log
import com.razorpay.Checkout
import org.json.JSONObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object RazorpayPaymentHelper {
    private const val TAG = "RazorpayPaymentHelper"

    // Razorpay credentials — replace with your actual keys
    var razorpayKeyId: String = "rzp_test_YOUR_KEY_HERE"
    var razorpayKeySecret: String = ""

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
     * Initiates Razorpay payment with enhanced configuration.
     * Includes notes for tracking, better prefill, and retry support.
     */
    fun startPayment(
        activity: Activity,
        amountInInr: Double,
        tierName: String,
        userEmail: String,
        userContact: String,
        userId: String = "",
        currentTier: String = ""
    ) {
        val amountInPaise = (amountInInr * 100).toLong()

        if (razorpayKeyId.isBlank() || razorpayKeyId == "rzp_test_YOUR_KEY_HERE") {
            Log.w(TAG, "Razorpay Key ID is not configured!")
            activity.runOnUiThread {
                android.widget.Toast.makeText(
                    activity,
                    "Razorpay Key ID is not configured. Please set your key in RazorpayPaymentHelper.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
            onPaymentError?.invoke(-1, "Razorpay Key ID is not configured. Please replace it in RazorpayPaymentHelper.kt.")
            return
        }

        val checkout = Checkout()
        checkout.setKeyID(razorpayKeyId)

        try {
            val options = JSONObject()
            options.put("name", "Cosmos Premium")
            options.put("description", "$tierName Membership • Monthly")
            options.put("image", "https://s3.amazonaws.com/rzp-mobile/images/rzp.png")
            options.put("currency", "INR")
            options.put("amount", amountInPaise)

            // Theme customization
            val theme = JSONObject()
            theme.put("color", "#6C63FF")
            theme.put("backdrop_color", "#0D0D1A")
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
            notes.put("plan_type", "monthly")
            notes.put("source", "cosmos_android_app")
            options.put("notes", notes)

            // Retry configuration
            val retry = JSONObject()
            retry.put("enabled", true)
            retry.put("max_count", 3)
            options.put("retry", retry)

            Log.d(TAG, "Launching Razorpay Checkout for $tierName — ₹$amountInInr (${amountInPaise}p)")
            checkout.open(activity, options)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching Razorpay Checkout SDK", e)
            onPaymentError?.invoke(-1, e.message ?: "Failed to initialize Razorpay Checkout SDK")
        }
    }

    /**
     * Computes the HMAC-SHA256 signature for verification.
     */
    fun generateSignature(orderId: String, paymentId: String, secret: String): String? {
        return try {
            val data = "$orderId|$paymentId"
            val secretKeySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(secretKeySpec)
            val rawHmac = mac.doFinal(data.toByteArray())
            rawHmac.joinToString("") { String.format("%02x", it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating signature", e)
            null
        }
    }

    /**
     * Verifies the payment signature.
     */
    fun verifyPaymentSignature(orderId: String, paymentId: String, signature: String): Boolean {
        val generatedSig = generateSignature(orderId, paymentId, razorpayKeySecret)
        return generatedSig != null && generatedSig == signature
    }
}
