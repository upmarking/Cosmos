package app.cosmos.com.data.payment

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * MembershipAnalytics — Firebase Analytics event tracking for the
 * COSMOS Lifetime Membership journey.
 *
 * Tracks the full funnel: page view → tier view → CTA click →
 * checkout → payment success/failure → upgrade completion.
 */
object MembershipAnalytics {

    private val analytics: FirebaseAnalytics by lazy { Firebase.analytics }

    fun membershipPageViewed() {
        analytics.logEvent("membership_page_viewed", null)
    }

    fun tierViewed(tierName: String) {
        analytics.logEvent("tier_viewed", Bundle().apply {
            putString("tier_name", tierName)
        })
    }

    fun upgradeCtaClicked(fromTier: String, toTier: String, amount: Int) {
        analytics.logEvent("upgrade_cta_clicked", Bundle().apply {
            putString("from_tier", fromTier)
            putString("to_tier", toTier)
            putInt("upgrade_amount", amount)
        })
    }

    fun razorpayCheckoutOpened(toTier: String, amount: Int) {
        analytics.logEvent("razorpay_checkout_opened", Bundle().apply {
            putString("to_tier", toTier)
            putInt("amount", amount)
        })
    }

    fun paymentSuccessful(fromTier: String, toTier: String, amount: Int, paymentId: String) {
        analytics.logEvent("payment_successful", Bundle().apply {
            putString("from_tier", fromTier)
            putString("to_tier", toTier)
            putInt("amount", amount)
            putString("payment_id", paymentId)
        })
    }

    fun paymentFailed(toTier: String, errorCode: Int, errorMessage: String) {
        analytics.logEvent("payment_failed", Bundle().apply {
            putString("to_tier", toTier)
            putInt("error_code", errorCode)
            putString("error_message", errorMessage.take(100))
        })
    }

    fun membershipUpgraded(fromTier: String, toTier: String, amount: Int) {
        analytics.logEvent("membership_upgraded", Bundle().apply {
            putString("from_tier", fromTier)
            putString("to_tier", toTier)
            putInt("amount", amount)
        })
    }

    fun tierProgression(fromTier: String, toTier: String) {
        analytics.logEvent("tier_progression", Bundle().apply {
            putString("from_tier", fromTier)
            putString("to_tier", toTier)
        })
    }
}
