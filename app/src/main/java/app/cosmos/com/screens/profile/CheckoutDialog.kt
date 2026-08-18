package app.cosmos.com.screens.profile

import androidx.compose.runtime.Composable
import app.cosmos.com.data.model.MembershipTier

/**
 * CheckoutDialog now delegates to the cinematic [CosmicUpgradeDialog]
 * for lifetime differential payment processing.
 */
@Composable
fun CheckoutDialog(
    tierFrom: MembershipTier,
    tierTo: MembershipTier,
    onDismiss: () -> Unit,
    onPaymentSuccess: (transactionId: String, methodUsed: String) -> Unit
) {
    CosmicUpgradeDialog(
        currentTier = tierFrom,
        targetTier = tierTo,
        onDismiss = onDismiss,
        onPaymentSuccess = { newTierName ->
            onPaymentSuccess(newTierName, "Razorpay")
        }
    )
}
