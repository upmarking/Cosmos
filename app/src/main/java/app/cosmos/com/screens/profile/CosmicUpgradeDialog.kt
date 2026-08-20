package app.cosmos.com.screens.profile

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.cosmos.com.data.model.GiftCard
import app.cosmos.com.data.model.GiftCardApplyResult
import app.cosmos.com.data.model.MembershipTier
import app.cosmos.com.data.payment.MembershipAnalytics
import app.cosmos.com.data.payment.PaymentManager
import app.cosmos.com.data.payment.RazorpayPaymentHelper
import app.cosmos.com.data.repository.ServiceLocator
import app.cosmos.com.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Checkout flow states for the cosmic upgrade dialog.
 */
private enum class CosmicCheckoutStep {
    REVIEW,       // Order summary with differential pricing & gift card
    CREATING,     // Creating Razorpay order or processing zero-payment redemption
    PROCESSING,   // Razorpay SDK launched, awaiting callback
    VERIFYING,    // Verifying payment with server
    SUCCESS,      // Payment confirmed, membership upgraded
    ERROR         // Payment/order failed — retry available
}

/**
 * Premium upgrade confirmation modal with differential pricing breakdown and Gift Card currency support.
 */
@Composable
fun CosmicUpgradeDialog(
    currentTier: MembershipTier,
    targetTier: MembershipTier,
    onDismiss: () -> Unit,
    onPaymentSuccess: (newTierName: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val upgradeAmount = PaymentManager.calculateUpgradeAmount(currentTier, targetTier)
    val featureGains = PaymentManager.getFeatureGains(currentTier, targetTier)

    // States
    var step by remember { mutableStateOf(CosmicCheckoutStep.REVIEW) }
    var errorMessage by remember { mutableStateOf("") }
    var termsAccepted by remember { mutableStateOf(false) }

    // Gift Card State
    var appliedGiftCard by remember { mutableStateOf<GiftCard?>(null) }
    var giftCardCodeInput by remember { mutableStateOf("") }
    var isCheckingGiftCard by remember { mutableStateOf(false) }
    var giftCardFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var isGiftCardError by remember { mutableStateOf(false) }

    // Calculate dynamic differential and preserved balances
    val applyResult: GiftCardApplyResult? = remember(appliedGiftCard, upgradeAmount) {
        appliedGiftCard?.let { card ->
            ServiceLocator.giftCardRepository.calculateApplication(card, upgradeAmount)
        }
    }

    val differentialAmount = applyResult?.differentialAmountToPay ?: upgradeAmount
    val giftCardDiscount = applyResult?.appliedDiscount ?: 0
    val preservedCardBalance = applyResult?.preservedRemainingBalance ?: 0
    val isZeroPayment = applyResult?.isFullyCovered == true

    // Order details from server
    var serverOrderId by remember { mutableStateOf("") }
    var serverKeyId by remember { mutableStateOf("") }
    var serverAmount by remember { mutableStateOf(0) }
    var finalPreservedBalance by remember { mutableStateOf(0) }

    // Fetch parent activity for Razorpay
    val activity = remember(context) {
        var currentContext = context
        while (currentContext is android.content.ContextWrapper) {
            if (currentContext is Activity) break
            currentContext = currentContext.baseContext
        }
        currentContext as? Activity
    }

    val currentUserState by ServiceLocator.authRepository.currentUser.collectAsState(initial = null)
    val userEmail = currentUserState?.email ?: ""
    val userId = currentUserState?.id ?: ""

    // Register Razorpay callbacks for differential payments
    DisposableEffect(Unit) {
        RazorpayPaymentHelper.registerCallbacks(
            onSuccess = { paymentId, orderId, signature ->
                // Payment succeeded — verify with server and complete redemption
                step = CosmicCheckoutStep.VERIFYING
                coroutineScope.launch {
                    val result = ServiceLocator.membershipRepository.verifyPayment(
                        userId = userId,
                        orderId = serverOrderId.ifBlank { orderId },
                        paymentId = paymentId,
                        signature = signature,
                        targetTier = targetTier,
                        giftCardCode = appliedGiftCard?.code,
                        giftCardDiscount = giftCardDiscount
                    )
                    result.fold(
                        onSuccess = { verification ->
                            MembershipAnalytics.paymentSuccessful(
                                currentTier.name, targetTier.name, differentialAmount, paymentId
                            )
                            MembershipAnalytics.membershipUpgraded(
                                currentTier.name, verification.newTier, verification.amount
                            )
                            finalPreservedBalance = verification.preservedBalance
                            step = CosmicCheckoutStep.SUCCESS
                            delay(3000)
                            onPaymentSuccess(verification.newTier)
                        },
                        onFailure = { error ->
                            errorMessage = error.message ?: "Payment verification failed"
                            step = CosmicCheckoutStep.ERROR
                        }
                    )
                }
            },
            onError = { code, message ->
                MembershipAnalytics.paymentFailed(targetTier.name, code, message)
                errorMessage = RazorpayPaymentHelper.getReadableError(code, message)
                step = if (code == 1) {
                    CosmicCheckoutStep.REVIEW
                } else {
                    CosmicCheckoutStep.ERROR
                }
            }
        )
        onDispose {
            RazorpayPaymentHelper.clearCallbacks()
        }
    }

    val tierColor = Color(targetTier.color)

    Dialog(
        onDismissRequest = { if (step == CosmicCheckoutStep.REVIEW) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .systemBarsPadding()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(CosmosCosmicDeep, Color(0xFF0D0D2B))
                    )
                )
                .border(
                    1.dp,
                    CosmosOutlineVariant.copy(alpha = 0.2f),
                    RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Header ───────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(listOf(tierColor, tierColor.copy(alpha = 0.7f)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.RocketLaunch,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                "UPGRADE YOUR UNIVERSE",
                                style = MaterialTheme.typography.titleMedium,
                                color = CosmosStarWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                when (step) {
                                    CosmicCheckoutStep.REVIEW -> "REVIEW & DIFFERENTIAL PAYMENT"
                                    CosmicCheckoutStep.CREATING -> if (isZeroPayment) "REDEEMING GIFT CARD" else "PREPARING ORDER"
                                    CosmicCheckoutStep.PROCESSING -> "PROCESSING PAYMENT"
                                    CosmicCheckoutStep.VERIFYING -> "CONFIRMING WITH COSMOS"
                                    CosmicCheckoutStep.SUCCESS -> "MEMBERSHIP UNLOCKED"
                                    CosmicCheckoutStep.ERROR -> "TRANSACTION FAILED"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = when (step) {
                                    CosmicCheckoutStep.SUCCESS -> CosmosSuccess
                                    CosmicCheckoutStep.ERROR -> CosmosError
                                    else -> tierColor
                                },
                                letterSpacing = 1.2.sp
                            )
                        }
                    }
                    if (step == CosmicCheckoutStep.REVIEW || step == CosmicCheckoutStep.ERROR) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close", tint = CosmosOnSurfaceVariant)
                        }
                    }
                }

                HorizontalDivider(color = CosmosOutlineVariant.copy(alpha = 0.1f), thickness = 1.dp)

                // ── Content ──────────────────────────────────────────────
                Box(modifier = Modifier.weight(1f)) {
                    when (step) {
                        CosmicCheckoutStep.REVIEW -> CosmicReviewStep(
                            currentTier = currentTier,
                            targetTier = targetTier,
                            upgradeAmount = upgradeAmount,
                            differentialAmount = differentialAmount,
                            giftCardDiscount = giftCardDiscount,
                            preservedCardBalance = preservedCardBalance,
                            appliedGiftCard = appliedGiftCard,
                            giftCardCodeInput = giftCardCodeInput,
                            isCheckingGiftCard = isCheckingGiftCard,
                            giftCardFeedbackMessage = giftCardFeedbackMessage,
                            isGiftCardError = isGiftCardError,
                            onGiftCardCodeChange = {
                                giftCardCodeInput = it
                                giftCardFeedbackMessage = null
                            },
                            onApplyGiftCard = {
                                val code = giftCardCodeInput.trim().uppercase()
                                if (code.isBlank()) {
                                    giftCardFeedbackMessage = "Please enter a voucher code"
                                    isGiftCardError = true
                                    return@CosmicReviewStep
                                }
                                isCheckingGiftCard = true
                                isGiftCardError = false
                                giftCardFeedbackMessage = null
                                coroutineScope.launch {
                                    val res = ServiceLocator.giftCardRepository.validateGiftCard(code)
                                    isCheckingGiftCard = false
                                    res.fold(
                                        onSuccess = { card ->
                                            appliedGiftCard = card
                                            val calc = ServiceLocator.giftCardRepository.calculateApplication(card, upgradeAmount)
                                            isGiftCardError = false
                                            giftCardFeedbackMessage = if (calc.isFullyCovered) {
                                                "✨ Card covers ₹${calc.appliedDiscount}! Preserved Balance: ₹${calc.preservedRemainingBalance}"
                                            } else {
                                                "✅ Applied ₹${calc.appliedDiscount}. Differential to pay: ₹${calc.differentialAmountToPay}"
                                            }
                                        },
                                        onFailure = { err ->
                                            appliedGiftCard = null
                                            isGiftCardError = true
                                            giftCardFeedbackMessage = err.message ?: "Invalid gift card code"
                                        }
                                    )
                                }
                            },
                            onRemoveGiftCard = {
                                appliedGiftCard = null
                                giftCardCodeInput = ""
                                giftCardFeedbackMessage = null
                                isGiftCardError = false
                            },
                            featureGains = featureGains,
                            termsAccepted = termsAccepted,
                            onTermsToggle = { termsAccepted = it },
                            onProceed = {
                                step = CosmicCheckoutStep.CREATING
                                coroutineScope.launch {
                                    // 1. Direct Zero-Payment Upgrade Path (Bypasses Razorpay entirely)
                                    if (differentialAmount == 0 && appliedGiftCard != null) {
                                        val localOrderId = "order_gc_${UUID.randomUUID().toString().replace("-", "").substring(0, 12)}"
                                        val redeemRes = ServiceLocator.membershipRepository.redeemZeroAmountOrder(
                                            userId = userId,
                                            orderId = localOrderId,
                                            targetTier = targetTier,
                                            giftCardCode = appliedGiftCard!!.code,
                                            amountDeducted = giftCardDiscount.takeIf { it > 0 } ?: upgradeAmount
                                        )
                                        redeemRes.fold(
                                            onSuccess = { vResult ->
                                                MembershipAnalytics.paymentSuccessful(
                                                    currentTier.name, targetTier.name, 0, vResult.paymentId
                                                )
                                                MembershipAnalytics.membershipUpgraded(
                                                    currentTier.name, vResult.newTier, vResult.amount
                                                )
                                                finalPreservedBalance = vResult.preservedBalance
                                                step = CosmicCheckoutStep.SUCCESS
                                                delay(2500)
                                                onPaymentSuccess(vResult.newTier)
                                            },
                                            onFailure = { err ->
                                                errorMessage = err.message ?: "Failed to redeem gift card"
                                                step = CosmicCheckoutStep.ERROR
                                            }
                                        )
                                        return@launch
                                    }

                                    // 2. Differential Payment Path (via Razorpay for remaining non-zero balance)
                                    val orderRes = ServiceLocator.membershipRepository.createUpgradeOrder(
                                        userId = userId,
                                        targetTier = targetTier,
                                        giftCardCode = appliedGiftCard?.code
                                    )

                                    orderRes.fold(
                                        onSuccess = { order ->
                                            serverOrderId = order.orderId
                                            serverKeyId = order.keyId
                                            serverAmount = order.amount
                                            finalPreservedBalance = order.preservedBalance

                                            // If ₹0 differential (fully covered by gift card), instant completion!
                                            if (order.isFreeOrder || order.differentialAmount == 0) {
                                                val redeemRes = ServiceLocator.membershipRepository.redeemZeroAmountOrder(
                                                    userId = userId,
                                                    orderId = order.orderId,
                                                    targetTier = targetTier,
                                                    giftCardCode = appliedGiftCard!!.code,
                                                    amountDeducted = order.giftCardDiscount.takeIf { it > 0 } ?: upgradeAmount
                                                )
                                                redeemRes.fold(
                                                    onSuccess = { vResult ->
                                                        MembershipAnalytics.paymentSuccessful(
                                                            currentTier.name, targetTier.name, 0, vResult.paymentId
                                                        )
                                                        MembershipAnalytics.membershipUpgraded(
                                                            currentTier.name, vResult.newTier, vResult.amount
                                                        )
                                                        finalPreservedBalance = vResult.preservedBalance
                                                        step = CosmicCheckoutStep.SUCCESS
                                                        delay(2500)
                                                        onPaymentSuccess(vResult.newTier)
                                                    },
                                                    onFailure = { err ->
                                                        errorMessage = err.message ?: "Failed to redeem gift card"
                                                        step = CosmicCheckoutStep.ERROR
                                                    }
                                                )
                                            } else {
                                                // Differential payment required via Razorpay
                                                if (activity == null) {
                                                    Toast.makeText(context, "Activity context not found", Toast.LENGTH_SHORT).show()
                                                    step = CosmicCheckoutStep.REVIEW
                                                    return@launch
                                                }
                                                step = CosmicCheckoutStep.PROCESSING
                                                MembershipAnalytics.razorpayCheckoutOpened(targetTier.name, order.differentialAmount)
                                                RazorpayPaymentHelper.startPaymentWithOrder(
                                                    activity = activity,
                                                    orderId = order.orderId,
                                                    amountInInr = order.differentialAmount,
                                                    keyId = order.keyId,
                                                    tierName = targetTier.label,
                                                    userEmail = userEmail,
                                                    userContact = "",
                                                    userId = userId,
                                                    currentTier = currentTier.name
                                                )
                                            }
                                        },
                                        onFailure = { err ->
                                            errorMessage = err.message ?: "Failed to create order"
                                            step = CosmicCheckoutStep.ERROR
                                        }
                                    )
                                }
                            }
                        )

                        CosmicCheckoutStep.CREATING -> CosmicLoadingStep(
                            message = if (isZeroPayment) "Redeeming your Gift Card..." else "Preparing your cosmic upgrade...",
                            submessage = if (isZeroPayment) "Deducting stored balance & preserving remainder" else "Calculating differential amount & creating secure order"
                        )

                        CosmicCheckoutStep.PROCESSING -> CosmicLoadingStep(
                            message = "Completing differential payment...",
                            submessage = "Please complete ₹${differentialAmount} in the Razorpay window"
                        )

                        CosmicCheckoutStep.VERIFYING -> CosmicLoadingStep(
                            message = "Verifying payment & updating universe...",
                            submessage = "Deducting gift card balance and upgrading membership"
                        )

                        CosmicCheckoutStep.SUCCESS -> CosmicCheckoutSuccessStep(
                            targetTier = targetTier,
                            amountPaid = differentialAmount,
                            giftCardDiscount = giftCardDiscount,
                            preservedBalance = finalPreservedBalance.takeIf { it > 0 } ?: preservedCardBalance,
                            giftCardCode = appliedGiftCard?.code
                        )

                        CosmicCheckoutStep.ERROR -> CosmicErrorStep(
                            message = errorMessage,
                            onRetry = { step = CosmicCheckoutStep.REVIEW },
                            onCancel = onDismiss
                        )
                    }
                }
            }
        }
    }
}

// ── Review Step ─────────────────────────────────────────────────────────────

@Composable
private fun CosmicReviewStep(
    currentTier: MembershipTier,
    targetTier: MembershipTier,
    upgradeAmount: Int,
    differentialAmount: Int,
    giftCardDiscount: Int,
    preservedCardBalance: Int,
    appliedGiftCard: GiftCard?,
    giftCardCodeInput: String,
    isCheckingGiftCard: Boolean,
    giftCardFeedbackMessage: String?,
    isGiftCardError: Boolean,
    onGiftCardCodeChange: (String) -> Unit,
    onApplyGiftCard: () -> Unit,
    onRemoveGiftCard: () -> Unit,
    featureGains: List<String>,
    termsAccepted: Boolean,
    onTermsToggle: (Boolean) -> Unit,
    onProceed: () -> Unit
) {
    val currentColor = Color(currentTier.color)
    val targetColor = Color(targetTier.color)
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // ── Tier Progression Visual ────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(currentColor.copy(alpha = 0.08f), targetColor.copy(alpha = 0.12f))
                    )
                )
                .border(1.dp, targetColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // From tier
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(currentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when (currentTier) {
                                MembershipTier.ASTEROID -> "☄️"
                                MembershipTier.MOON -> "🌙"
                                MembershipTier.EARTH -> "🌍"
                                MembershipTier.SUN -> "☀️"
                            },
                            fontSize = 22.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        currentTier.label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = CosmosOnSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "Current",
                        style = MaterialTheme.typography.labelSmall,
                        color = CosmosOnSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // Arrow with rocket
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🚀", fontSize = 18.sp)
                    Spacer(Modifier.height(2.dp))
                    Icon(
                        Icons.Default.ArrowForward,
                        null,
                        tint = targetColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        "UPGRADE",
                        style = MaterialTheme.typography.labelSmall,
                        color = targetColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // To tier
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(targetColor, targetColor.copy(alpha = 0.7f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when (targetTier) {
                                MembershipTier.ASTEROID -> "☄️"
                                MembershipTier.MOON -> "🌙"
                                MembershipTier.EARTH -> "🌍"
                                MembershipTier.SUN -> "☀️"
                            },
                            fontSize = 22.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        targetTier.label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = CosmosStarWhite,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "Destination",
                        style = MaterialTheme.typography.labelSmall,
                        color = targetColor.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Gift Card Voucher Section ──────────────────────────────────
        Text(
            "COSMIC GIFT CARD & VOUCHER",
            style = MaterialTheme.typography.labelMedium,
            color = CosmosPrimary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            CosmosPrimary.copy(alpha = 0.08f),
                            CosmosCosmicPurple.copy(alpha = 0.04f)
                        )
                    )
                )
                .border(
                    1.dp,
                    if (appliedGiftCard != null) CosmosSuccess.copy(alpha = 0.5f) else CosmosPrimary.copy(alpha = 0.25f),
                    RoundedCornerShape(14.dp)
                )
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (appliedGiftCard == null) {
                    // Input Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = giftCardCodeInput,
                            onValueChange = onGiftCardCodeChange,
                            placeholder = {
                                Text("Enter Gift Card Code", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f), fontSize = 13.sp)
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.CardGiftcard, contentDescription = null, tint = CosmosPrimary, modifier = Modifier.size(20.dp))
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    onApplyGiftCard()
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CosmosStarWhite,
                                unfocusedTextColor = CosmosStarWhite,
                                focusedBorderColor = CosmosPrimary,
                                unfocusedBorderColor = CosmosOutlineVariant.copy(alpha = 0.4f),
                                cursorColor = CosmosPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onApplyGiftCard()
                            },
                            enabled = !isCheckingGiftCard && giftCardCodeInput.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CosmosPrimary,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.height(52.dp)
                        ) {
                            if (isCheckingGiftCard) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Apply", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    // Applied Gift Card Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CosmosSuccess.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = CosmosSuccess,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    appliedGiftCard.code,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CosmosStarWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Balance: ${appliedGiftCard.formattedBalance} • Currency Credit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CosmosSuccess
                                )
                            }
                        }

                        TextButton(onClick = onRemoveGiftCard) {
                            Text("Remove", color = CosmosError, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Balance preservation notice
                    if (preservedCardBalance > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CosmosSuccess.copy(alpha = 0.08f))
                                .border(1.dp, CosmosSuccess.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("💎", fontSize = 13.sp)
                                Text(
                                    "Surplus balance of ${PaymentManager.formatIndianPrice(preservedCardBalance)} will be preserved on this gift card for next time!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CosmosSuccess
                                )
                            }
                        }
                    }
                }

                // Feedback message (error or status)
                if (!giftCardFeedbackMessage.isNullOrBlank()) {
                    Text(
                        giftCardFeedbackMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isGiftCardError) CosmosError else CosmosSuccess,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Billing Breakdown ──────────────────────────────────────────
        Text(
            "UPGRADE BILLING BREAKDOWN",
            style = MaterialTheme.typography.labelMedium,
            color = CosmosOnSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CosmosGlass)
                .border(1.dp, CosmosGlassBorder, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Target tier full price
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "${targetTier.label} Lifetime Access",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CosmosOnSurfaceVariant
                    )
                    Text(
                        PaymentManager.formatIndianPrice(targetTier.lifetimePrice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CosmosStarWhite
                    )
                }

                // Current membership credit
                if (currentTier.lifetimePrice > 0) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Current ${currentTier.label} Credit",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CosmosSuccess
                        )
                        Text(
                            "− ${PaymentManager.formatIndianPrice(currentTier.lifetimePrice)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CosmosSuccess,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Upgrade subtotal
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Upgrade Subtotal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CosmosOnSurfaceVariant
                    )
                    Text(
                        PaymentManager.formatIndianPrice(upgradeAmount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CosmosStarWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Applied Gift Card
                if (giftCardDiscount > 0) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("🎁", fontSize = 13.sp)
                            Text(
                                "Gift Card (${appliedGiftCard?.code})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CosmosPrimary
                            )
                        }
                        Text(
                            "− ${PaymentManager.formatIndianPrice(giftCardDiscount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CosmosPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = CosmosOutlineVariant.copy(alpha = 0.2f), thickness = 0.5.dp)

                // Differential Amount To Pay
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (differentialAmount == 0) "Total to Pay" else "Differential Amount to Pay",
                            style = MaterialTheme.typography.titleMedium,
                            color = CosmosStarWhite,
                            fontWeight = FontWeight.Bold
                        )
                        if (differentialAmount == 0) {
                            Text(
                                "Fully covered by Gift Card",
                                style = MaterialTheme.typography.labelSmall,
                                color = CosmosSuccess
                            )
                        }
                    }
                    Text(
                        if (differentialAmount == 0) "₹0 (Free)" else PaymentManager.formatIndianPrice(differentialAmount),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (differentialAmount == 0) CosmosSuccess else targetColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ONE-TIME badge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(targetColor.copy(alpha = 0.08f))
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "ONE-TIME LIFETIME UPGRADE  •  NO RECURRING FEES",
                style = MaterialTheme.typography.labelSmall,
                color = targetColor,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(Modifier.height(18.dp))

        // ── New Features ───────────────────────────────────────────────
        if (featureGains.isNotEmpty()) {
            Text(
                "NEW FEATURES YOU UNLOCK",
                style = MaterialTheme.typography.labelMedium,
                color = CosmosOnSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CosmosGlass)
                    .border(1.dp, CosmosGlassBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    featureGains.forEach { feature ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(CosmosSuccess.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    tint = CosmosSuccess,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Text(
                                feature,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CosmosStarWhite
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        // ── Payment Method ─────────────────────────────────────────────
        if (differentialAmount > 0) {
            Text(
                "PAYMENT METHOD",
                style = MaterialTheme.typography.labelMedium,
                color = CosmosOnSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CosmosGlass)
                    .border(1.dp, CosmosGlassBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CosmosPrimary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CreditCard, null, tint = CosmosPrimary, modifier = Modifier.size(20.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "UPI • Cards • Netbanking • Wallets",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CosmosStarWhite,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Secured by Razorpay • 256-bit encryption",
                            style = MaterialTheme.typography.labelSmall,
                            color = CosmosSuccess
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        // ── Terms ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onTermsToggle(!termsAccepted) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(
                checked = termsAccepted,
                onCheckedChange = onTermsToggle,
                colors = CheckboxDefaults.colors(
                    checkedColor = targetColor,
                    uncheckedColor = CosmosOnSurfaceVariant
                )
            )
            Text(
                "I agree to the Terms of Service. This is a one-time lifetime payment. Any remaining gift card balance is safely preserved.",
                style = MaterialTheme.typography.bodySmall,
                color = CosmosOnSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Launch / Redeem Button ─────────────────────────────────────
        Button(
            onClick = onProceed,
            enabled = termsAccepted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = targetColor,
                disabledContainerColor = targetColor.copy(alpha = 0.3f)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(if (differentialAmount == 0) "✨" else "🚀", fontSize = 18.sp)
                Text(
                    if (differentialAmount == 0) {
                        "Redeem Gift Card & Launch to ${targetTier.label} (₹0)"
                    } else {
                        "Pay Differential ${PaymentManager.formatIndianPrice(differentialAmount)} & Launch"
                    },
                    color = if (targetTier == MembershipTier.SUN) Color(0xFF1A0A00) else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── Loading Step ────────────────────────────────────────────────────────────

@Composable
private fun CosmicLoadingStep(message: String, submessage: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_pulse"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(CosmosPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = CosmosPrimary,
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            message,
            style = MaterialTheme.typography.titleMedium,
            color = CosmosStarWhite,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            submessage,
            style = MaterialTheme.typography.bodySmall,
            color = CosmosOnSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, start = 32.dp, end = 32.dp),
            textAlign = TextAlign.Center
        )
    }
}

// ── Success Step ────────────────────────────────────────────────────────────

@Composable
private fun CosmicCheckoutSuccessStep(
    targetTier: MembershipTier,
    amountPaid: Int,
    giftCardDiscount: Int = 0,
    preservedBalance: Int = 0,
    giftCardCode: String? = null
) {
    val tierColor = Color(targetTier.color)
    val scale = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) {
        scale.animateTo(
            1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Success icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(scale.value)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            CosmosSuccess.copy(alpha = 0.3f),
                            CosmosSuccess.copy(alpha = 0.05f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = CosmosSuccess,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            if (amountPaid == 0) "GIFT CARD REDEEMED! 🎁" else "UPGRADE SUCCESSFUL! 🚀",
            style = MaterialTheme.typography.labelMedium,
            color = CosmosSuccess,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Welcome to ${targetTier.label}",
            style = MaterialTheme.typography.headlineSmall,
            color = CosmosStarWhite,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Your COSMOS universe has expanded. Lifetime access unlocked.",
            style = MaterialTheme.typography.bodyMedium,
            color = CosmosOnSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(18.dp))

        // Tier badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(tierColor.copy(alpha = 0.2f), tierColor.copy(alpha = 0.05f))
                    )
                )
                .border(1.dp, tierColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    when (targetTier) {
                        MembershipTier.ASTEROID -> "☄️"
                        MembershipTier.MOON -> "🌙"
                        MembershipTier.EARTH -> "🌍"
                        MembershipTier.SUN -> "☀️"
                    },
                    fontSize = 16.sp
                )
                Text(
                    "${targetTier.badge} • Lifetime",
                    style = MaterialTheme.typography.labelLarge,
                    color = tierColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (preservedBalance > 0 && giftCardCode != null) {
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CosmosGlass)
                    .border(1.dp, CosmosSuccess.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "💎 Preserved Gift Card Balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = CosmosSuccess,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Card $giftCardCode still has ${PaymentManager.formatIndianPrice(preservedBalance)} available for future use.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosStarWhite,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ── Error Step ─────────────────────────────────────────────────────────────

@Composable
private fun CosmicErrorStep(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(CosmosError.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                null,
                tint = CosmosError,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "TRANSACTION ERROR",
            style = MaterialTheme.typography.labelMedium,
            color = CosmosError,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message.ifBlank { "Something went wrong during checkout. Please try again." },
            style = MaterialTheme.typography.bodyMedium,
            color = CosmosStarWhite,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", color = CosmosOnSurfaceVariant)
            }
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CosmosPrimary)
            ) {
                Text("Retry", fontWeight = FontWeight.Bold)
            }
        }
    }
}
