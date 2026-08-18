package app.cosmos.com.data.payment

import app.cosmos.com.data.model.MembershipTier

/**
 * FeatureGate — Reusable entitlement checker for COSMOS Lifetime Membership.
 *
 * Every premium feature declares which tier is required. The gate checks
 * whether the user's current tier meets or exceeds the requirement.
 *
 * Usage:
 *   if (FeatureGate.hasAccess(userTier, MembershipTier.EARTH)) { ... }
 *   FeatureGate.getLockedMessage(MembershipTier.SUN) // "Unlock with Sun"
 */
object FeatureGate {

    /**
     * Returns true if the user's tier grants access to a feature
     * that requires [requiredTier].
     *
     * A higher tier always includes all lower-tier features.
     */
    fun hasAccess(userTier: MembershipTier, requiredTier: MembershipTier): Boolean {
        return userTier.tierLevel >= requiredTier.tierLevel
    }

    /**
     * Returns the display label for the required tier.
     */
    fun getRequiredTierLabel(requiredTier: MembershipTier): String {
        return requiredTier.label
    }

    /**
     * Returns a user-friendly locked message.
     * Example: "Unlock with Earth"
     */
    fun getLockedMessage(requiredTier: MembershipTier): String {
        return "Unlock with ${requiredTier.label}"
    }

    /**
     * Returns the emoji/icon hint for a locked feature.
     */
    fun getLockedIcon(requiredTier: MembershipTier): String {
        return when (requiredTier) {
            MembershipTier.ASTEROID -> "☄️"
            MembershipTier.MOON -> "🌙"
            MembershipTier.EARTH -> "🌍"
            MembershipTier.SUN -> "☀️"
        }
    }

    /**
     * Checks whether a feature at the given tier is locked for the user.
     * Convenience inverse of [hasAccess].
     */
    fun isLocked(userTier: MembershipTier, requiredTier: MembershipTier): Boolean {
        return !hasAccess(userTier, requiredTier)
    }
}
