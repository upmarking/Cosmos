package app.cosmos.com.data.payment

import app.cosmos.com.data.model.MembershipTier
import app.cosmos.com.data.model.SubscriptionPlan

/**
 * Breakdown details for a membership purchase transaction.
 */
data class PaymentDetails(
    val subtotal: Double,
    val gst: Double,
    val grandTotal: Double
)

/**
 * PaymentManager is the single source of truth for subscription plans,
 * pricing, feature lists, and upgrade logic.
 */
object PaymentManager {

    // ── Plan Definitions ─────────────────────────────────────────────────────

    private val plans = listOf(
        SubscriptionPlan(
            tier = MembershipTier.EXPLORER,
            monthlyPriceInr = 0.0,
            features = listOf(
                "Up to 3 connections/month",
                "Basic discovery deck",
                "Community access",
                "Profile creation",
                "Event browsing"
            )
        ),
        SubscriptionPlan(
            tier = MembershipTier.MEMBER,
            monthlyPriceInr = 2407.0, // ~$29 USD
            features = listOf(
                "10 connections/month",
                "Priority discovery deck",
                "All events access",
                "AI meeting summaries",
                "Warm intro requests",
                "CRM labels & notes",
                "Follow-up reminders",
                "Community posting"
            ),
        ),
        SubscriptionPlan(
            tier = MembershipTier.INNER_CIRCLE,
            monthlyPriceInr = 8217.0, // ~$99 USD
            isPopular = true,
            features = listOf(
                "Unlimited connections",
                "Priority AI matching",
                "Exclusive invite-only events",
                "Advanced AI summaries & insights",
                "Unlimited warm intros",
                "Featured profile badge",
                "Priority support",
                "Networking analytics dashboard",
                "Early access to new features"
            )
        ),
        SubscriptionPlan(
            tier = MembershipTier.FOUNDER,
            monthlyPriceInr = 0.0,
            isInviteOnly = true,
            features = listOf(
                "Everything in Inner Circle",
                "Full platform access",
                "Dedicated relationship advisor",
                "Custom event creation",
                "Founder spotlight profile",
                "Direct team access",
                "White-glove onboarding",
                "Strategic intro curation"
            )
        )
    )

    /**
     * Returns the subscription plan for a specific tier.
     */
    fun getPlan(tier: MembershipTier): SubscriptionPlan {
        return plans.first { it.tier == tier }
    }

    /**
     * Returns all subscription plans.
     */
    fun getAllPlans(): List<SubscriptionPlan> = plans

    /**
     * Returns only the plans the user can upgrade to from their current tier.
     * Enforces strict hierarchy: can only go UP, and Founder is invite-only.
     */
    fun getAvailableUpgrades(currentTier: MembershipTier): List<SubscriptionPlan> {
        return plans.filter { plan ->
            currentTier.canUpgradeTo(plan.tier)
        }
    }

    /**
     * Validates that upgrading from [from] to [to] is allowed.
     */
    fun canUpgrade(from: MembershipTier, to: MembershipTier): Boolean {
        return from.canUpgradeTo(to)
    }

    /**
     * Returns the new features the user will unlock when upgrading.
     */
    fun getFeatureGains(from: MembershipTier, to: MembershipTier): List<String> {
        val currentFeatures = getPlan(from).features.toSet()
        val newFeatures = getPlan(to).features
        return newFeatures.filter { it !in currentFeatures }
    }

    /**
     * Compute the billing breakdown (18% GST applied).
     */
    fun calculatePayment(price: Double): PaymentDetails {
        val subtotal = price
        val gst = if (subtotal > 0) subtotal * 0.18 else 0.0
        val grandTotal = subtotal + gst
        return PaymentDetails(subtotal, gst, grandTotal)
    }

    /**
     * Returns the connection limit for a given tier.
     */
    fun getConnectionLimit(tier: MembershipTier): Int {
        return when (tier) {
            MembershipTier.EXPLORER -> 3
            MembershipTier.MEMBER -> 10
            MembershipTier.INNER_CIRCLE -> 999
            MembershipTier.FOUNDER -> 999
        }
    }
}
