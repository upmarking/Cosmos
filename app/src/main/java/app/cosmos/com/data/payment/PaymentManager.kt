package app.cosmos.com.data.payment

import app.cosmos.com.data.model.MembershipTier
import app.cosmos.com.data.model.SubscriptionPlan
import java.text.DecimalFormat

/**
 * Simple data class to hold breakdown of payment.
 */
data class PaymentDetails(
    val subtotal: Double,
    val gst: Double,
    val grandTotal: Int
)

/**
 * PaymentManager is the single source of truth for lifetime membership plans,
 * pricing, feature lists, and upgrade logic.
 *
 * COSMOS Lifetime Membership uses a cosmic journey metaphor:
 * ASTEROID → MOON → EARTH → SUN
 *
 * All pricing is one-time. No monthly or annual billing.
 * Upgrades use differential pricing: upgradeAmount = targetTierPrice - currentTierPrice
 */
object PaymentManager {

    // ── Plan Definitions ─────────────────────────────────────────────────────

    private val plans = listOf(
        SubscriptionPlan(
            tier = MembershipTier.ASTEROID,
            lifetimePriceInr = 0,
            features = listOf(
                "Up to 3 connections/month",
                "Basic discovery deck",
                "Community access",
                "Profile creation",
                "Event browsing"
            )
        ),
        SubscriptionPlan(
            tier = MembershipTier.MOON,
            lifetimePriceInr = 49_999,
            features = listOf(
                "10 connections/month",
                "Priority discovery deck",
                "All events access",
                "AI meeting summaries",
                "Warm intro requests",
                "CRM labels & notes",
                "Follow-up reminders",
                "Community posting"
            )
        ),
        SubscriptionPlan(
            tier = MembershipTier.EARTH,
            lifetimePriceInr = 99_999,
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
            tier = MembershipTier.SUN,
            lifetimePriceInr = 199_999,
            features = listOf(
                "Everything in Earth",
                "Full platform access",
                "Dedicated relationship advisor",
                "Custom event creation",
                "Solar Elite spotlight profile",
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
     * Enforces strict hierarchy: can only go UP.
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
     * Calculates the differential upgrade amount.
     * Users only pay the difference between their current tier and the target tier.
     *
     * Example: MOON (₹49,999) → EARTH (₹99,999) = ₹50,000
     */
    fun calculateUpgradeAmount(from: MembershipTier, to: MembershipTier): Int {
        return to.lifetimePrice - from.lifetimePrice
    }

    /**
     * Calculates the payment breakdown (Subtotal, GST, Total) for a base amount.
     */
    fun calculatePayment(baseAmount: Int): PaymentDetails {
        val gstRate = 0.18
        val subtotal = baseAmount.toDouble()
        val gst = subtotal * gstRate
        val total = (subtotal + gst).toInt()
        return PaymentDetails(subtotal, gst, total)
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
     * Formats an amount in Indian number system with ₹ prefix.
     * Examples: 49999 → "₹49,999", 199999 → "₹1,99,999"
     */
    fun formatIndianPrice(amount: Int): String {
        if (amount == 0) return "₹0"
        val isNegative = amount < 0
        val absAmount = if (isNegative) -amount else amount
        val amountStr = absAmount.toString()
        val prefix = if (isNegative) "-₹" else "₹"

        if (amountStr.length <= 3) {
            return "$prefix$amountStr"
        }

        val lastThree = amountStr.substring(amountStr.length - 3)
        val rest = amountStr.substring(0, amountStr.length - 3)

        var remaining = rest
        val parts = mutableListOf<String>()
        while (remaining.length > 2) {
            parts.add(0, remaining.substring(remaining.length - 2))
            remaining = remaining.substring(0, remaining.length - 2)
        }
        if (remaining.isNotEmpty()) {
            parts.add(0, remaining)
        }

        val formattedRest = parts.joinToString(",")
        return "$prefix$formattedRest,$lastThree"
    }

    /**
     * Returns the connection limit for a given tier.
     */
    fun getConnectionLimit(tier: MembershipTier): Int {
        return when (tier) {
            MembershipTier.ASTEROID -> 3
            MembershipTier.MOON -> 10
            MembershipTier.EARTH -> 999
            MembershipTier.SUN -> 999
        }
    }

    /**
     * Returns creative description for a tier.
     */
    fun getTierDescription(tier: MembershipTier): String {
        return when (tier) {
            MembershipTier.ASTEROID -> "A small rock in the vast cosmos — but every supernova started as dust."
            MembershipTier.MOON -> "The Moon revolves around Earth, always present, always watching. Your first leap into the cosmos."
            MembershipTier.EARTH -> "Civilizations rise here. The blue marble where ambition meets gravity."
            MembershipTier.SUN -> "Everything orbits the Sun. Unlimited power. Unlimited light. The ultimate COSMOS experience."
        }
    }

    /**
     * Returns the short cosmic metaphor word for a tier.
     */
    fun getTierMetaphor(tier: MembershipTier): String {
        return when (tier) {
            MembershipTier.ASTEROID -> "START"
            MembershipTier.MOON -> "EXPAND"
            MembershipTier.EARTH -> "BUILD"
            MembershipTier.SUN -> "MASTER"
        }
    }
}
