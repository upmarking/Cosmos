package app.cosmos.com.data.model

/**
 * Status of a Cosmic Gift Card.
 */
enum class GiftCardStatus {
    ACTIVE,
    EXHAUSTED,
    EXPIRED,
    DISABLED;

    val label: String
        get() = when (this) {
            ACTIVE -> "Active"
            EXHAUSTED -> "Fully Redeemed"
            EXPIRED -> "Expired"
            DISABLED -> "Disabled"
        }
}

/**
 * Audit record of a gift card redemption event.
 */
data class GiftCardRedemption(
    val userId: String = "",
    val orderId: String = "",
    val amountDeducted: Int = 0,
    val previousBalance: Int = 0,
    val newBalance: Int = 0,
    val targetTier: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Cosmic Gift Card data model representing a stored-value currency balance.
 */
data class GiftCard(
    val code: String = "",
    val initialValue: Int = 0,
    val currentBalance: Int = 0,
    val currency: String = "INR",
    val status: GiftCardStatus = GiftCardStatus.ACTIVE,
    val title: String = "Cosmic Gift Voucher",
    val description: String = "Redeemable towards any COSMOS Lifetime Membership tier.",
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val createdBy: String = "system",
    val redemptions: List<GiftCardRedemption> = emptyList()
) {
    val isRedeemable: Boolean
        get() = status == GiftCardStatus.ACTIVE && currentBalance > 0 && (expiresAt == null || expiresAt > System.currentTimeMillis())

    val formattedBalance: String
        get() = formatInr(currentBalance)

    val formattedInitialValue: String
        get() = formatInr(initialValue)

    companion object {
        fun formatInr(amount: Int): String {
            val format = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "IN"))
            format.maximumFractionDigits = 0
            return format.format(amount)
        }
    }
}

/**
 * Detailed calculation result when applying a gift card to an upgrade amount.
 */
data class GiftCardApplyResult(
    val card: GiftCard,
    val originalUpgradeAmount: Int,
    val appliedDiscount: Int,
    val differentialAmountToPay: Int,
    val preservedRemainingBalance: Int,
    val isFullyCovered: Boolean
)
