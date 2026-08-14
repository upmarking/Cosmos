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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.cosmos.com.data.model.MembershipTier
import app.cosmos.com.data.model.SubscriptionStatus
import app.cosmos.com.data.model.UserSubscription
import app.cosmos.com.data.payment.PaymentManager
import app.cosmos.com.data.payment.RazorpayPaymentHelper
import app.cosmos.com.ui.components.CosmosSectionHeader
import app.cosmos.com.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Checkout states — streamlined for Razorpay integration.
 */
enum class CheckoutStep {
    REVIEW,       // Order summary + feature gains
    PROCESSING,   // Razorpay SDK launched, awaiting callback
    SUCCESS,      // Payment confirmed
    ERROR         // Payment failed — retry available
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckoutDialog(
    tierFrom: MembershipTier,
    tierTo: MembershipTier,
    onDismiss: () -> Unit,
    onPaymentSuccess: (transactionId: String, methodUsed: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val plan = PaymentManager.getPlan(tierTo)
    val billDetails = PaymentManager.calculatePayment(plan.monthlyPriceInr)
    val featureGains = PaymentManager.getFeatureGains(tierFrom, tierTo)

    // States
    var step by remember { mutableStateOf(CheckoutStep.REVIEW) }
    var generatedTxId by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var termsAccepted by remember { mutableStateOf(false) }

    // Fetch parent activity for Razorpay
    val activity = remember(context) {
        var currentContext = context
        while (currentContext is android.content.ContextWrapper) {
            if (currentContext is Activity) break
            currentContext = currentContext.baseContext
        }
        currentContext as? Activity
    }

    val currentUserState by app.cosmos.com.data.repository.ServiceLocator.authRepository.currentUser.collectAsState(initial = null)
    val userEmail = currentUserState?.email ?: ""
    val userId = currentUserState?.id ?: ""

    // Register Razorpay callbacks
    DisposableEffect(Unit) {
        RazorpayPaymentHelper.registerCallbacks(
            onSuccess = { paymentId, _, _ ->
                generatedTxId = paymentId
                step = CheckoutStep.SUCCESS
            },
            onError = { code, message ->
                errorMessage = RazorpayPaymentHelper.getReadableError(code, message)
                step = if (code == 1) {
                    // User cancelled — go back to review
                    CheckoutStep.REVIEW
                } else {
                    CheckoutStep.ERROR
                }
            }
        )
        onDispose {
            RazorpayPaymentHelper.clearCallbacks()
        }
    }

    // Auto-close on success after showing animation
    LaunchedEffect(step) {
        if (step == CheckoutStep.SUCCESS) {
            delay(3000)
            onPaymentSuccess(generatedTxId, "Razorpay")
        }
    }

    Dialog(
        onDismissRequest = { if (step == CheckoutStep.REVIEW) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .systemBarsPadding()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(CosmosBackground)
                .border(
                    1.dp,
                    CosmosOutlineVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Header ───────────────────────────────────────────────────
                CheckoutHeader(
                    step = step,
                    onClose = { if (step == CheckoutStep.REVIEW || step == CheckoutStep.ERROR) onDismiss() }
                )

                HorizontalDivider(color = CosmosOutlineVariant.copy(alpha = 0.15f), thickness = 1.dp)

                // ── Content ──────────────────────────────────────────────────
                Box(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = step,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(200))
                        },
                        label = "checkout_step"
                    ) { currentStep ->
                        when (currentStep) {
                            CheckoutStep.REVIEW -> ReviewStep(
                                tierFrom = tierFrom,
                                tierTo = tierTo,
                                featureGains = featureGains,
                                billDetails = billDetails,
                                termsAccepted = termsAccepted,
                                onTermsToggle = { termsAccepted = it },
                                onProceed = {
                                    if (activity == null) {
                                        Toast.makeText(context, "Activity context not found", Toast.LENGTH_SHORT).show()
                                        return@ReviewStep
                                    }
                                    step = CheckoutStep.PROCESSING
                                    RazorpayPaymentHelper.startPayment(
                                        activity = activity,
                                        amountInInr = billDetails.grandTotal,
                                        tierName = tierTo.label,
                                        userEmail = userEmail,
                                        userContact = "",
                                        userId = userId,
                                        currentTier = tierFrom.label
                                    )
                                }
                            )

                            CheckoutStep.PROCESSING -> ProcessingStep(tierTo = tierTo)

                            CheckoutStep.SUCCESS -> SuccessStep(
                                tierTo = tierTo,
                                transactionId = generatedTxId,
                                amountPaid = billDetails.grandTotal
                            )

                            CheckoutStep.ERROR -> ErrorStep(
                                message = errorMessage,
                                onRetry = {
                                    if (activity != null) {
                                        step = CheckoutStep.PROCESSING
                                        RazorpayPaymentHelper.startPayment(
                                            activity = activity,
                                            amountInInr = billDetails.grandTotal,
                                            tierName = tierTo.label,
                                            userEmail = userEmail,
                                            userContact = "",
                                            userId = userId,
                                            currentTier = tierFrom.label
                                        )
                                    }
                                },
                                onCancel = onDismiss
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────────────

@Composable
private fun CheckoutHeader(step: CheckoutStep, onClose: () -> Unit) {
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
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                CosmosPrimary,
                                CosmosPrimary.copy(alpha = 0.7f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = CosmosOnPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column {
                Text(
                    "Cosmos Secure Pay",
                    style = MaterialTheme.typography.titleMedium,
                    color = CosmosOnBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when (step) {
                        CheckoutStep.REVIEW -> "REVIEW ORDER"
                        CheckoutStep.PROCESSING -> "PROCESSING"
                        CheckoutStep.SUCCESS -> "PAYMENT CONFIRMED"
                        CheckoutStep.ERROR -> "PAYMENT FAILED"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (step) {
                        CheckoutStep.SUCCESS -> CosmosSuccess
                        CheckoutStep.ERROR -> CosmosError
                        else -> CosmosPrimary
                    },
                    letterSpacing = 1.2.sp
                )
            }
        }
        if (step == CheckoutStep.REVIEW || step == CheckoutStep.ERROR) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = CosmosOnSurfaceVariant)
            }
        }
    }
}

// ── Review Step ────────────────────────────────────────────────────────────────

@Composable
private fun ReviewStep(
    tierFrom: MembershipTier,
    tierTo: MembershipTier,
    featureGains: List<String>,
    billDetails: app.cosmos.com.data.payment.PaymentDetails,
    termsAccepted: Boolean,
    onTermsToggle: (Boolean) -> Unit,
    onProceed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // ── Upgrade Arrow Card ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            CosmosGradientStart.copy(alpha = 0.08f),
                            CosmosGradientEnd.copy(alpha = 0.12f)
                        )
                    )
                )
                .border(1.dp, CosmosPrimary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
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
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(tierFrom.color).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            null,
                            tint = Color(tierFrom.color),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tierFrom.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = CosmosOnSurfaceVariant
                    )
                }

                // Arrow
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ArrowForward,
                        null,
                        tint = CosmosPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        "UPGRADE",
                        style = MaterialTheme.typography.labelSmall,
                        color = CosmosPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // To tier
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(tierTo.color),
                                        Color(tierTo.color).copy(alpha = 0.7f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.WorkspacePremium,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tierTo.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = CosmosOnBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── New Features You Unlock ──────────────────────────────────────
        if (featureGains.isNotEmpty()) {
            CosmosSectionHeader("What You Unlock")
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CosmosGlass)
                    .border(1.dp, CosmosGlassBorder, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    featureGains.forEach { feature ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(CosmosSuccess.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    tint = CosmosSuccess,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                feature,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CosmosOnBackground
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // ── Payment Method ───────────────────────────────────────────────
        CosmosSectionHeader("Payment via Razorpay")
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
                        color = CosmosOnBackground,
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

        Spacer(Modifier.height(24.dp))

        // ── Bill Summary ─────────────────────────────────────────────────
        CosmosSectionHeader("Billing Summary")
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CosmosGlass)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BillRow("${tierTo.label} Plan (Monthly)", "₹${billDetails.subtotal.toInt()}")
                BillRow("GST (18%)", "₹${billDetails.gst.toInt()}")
                HorizontalDivider(color = CosmosOutlineVariant.copy(alpha = 0.2f), thickness = 0.5.dp)
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Total Payable",
                        style = MaterialTheme.typography.titleMedium,
                        color = CosmosOnBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "₹${billDetails.grandTotal.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        color = CosmosPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Terms ────────────────────────────────────────────────────────
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
                    checkedColor = CosmosPrimary,
                    uncheckedColor = CosmosOnSurfaceVariant
                )
            )
            Text(
                "I agree to the Terms of Service and Subscription Policy. This is a recurring monthly charge.",
                style = MaterialTheme.typography.bodySmall,
                color = CosmosOnSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Pay Button ───────────────────────────────────────────────────
        Button(
            onClick = onProceed,
            enabled = termsAccepted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CosmosPrimary,
                disabledContainerColor = CosmosPrimary.copy(alpha = 0.3f)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Lock,
                    null,
                    tint = CosmosOnPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "Pay ₹${billDetails.grandTotal.toInt()} via Razorpay",
                    color = CosmosOnPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BillRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground)
    }
}

// ── Processing Step ────────────────────────────────────────────────────────────

@Composable
private fun ProcessingStep(tierTo: MembershipTier) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Animated pulsing indicator
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
            "Completing your payment...",
            style = MaterialTheme.typography.titleMedium,
            color = CosmosOnBackground,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Please complete the payment in the Razorpay window",
            style = MaterialTheme.typography.bodySmall,
            color = CosmosOnSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, start = 32.dp, end = 32.dp),
            textAlign = TextAlign.Center
        )
    }
}

// ── Success Step ───────────────────────────────────────────────────────────────

@Composable
private fun SuccessStep(
    tierTo: MembershipTier,
    transactionId: String,
    amountPaid: Double
) {
    // Animated entrance
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
        // Success checkmark with glow
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
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(CosmosSuccess.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = CosmosSuccess,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Payment Successful! 🎉",
            style = MaterialTheme.typography.headlineSmall,
            color = CosmosOnBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            "Welcome to ${tierTo.label}",
            style = MaterialTheme.typography.titleMedium,
            color = CosmosPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(20.dp))

        // New tier badge preview
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(tierTo.color).copy(alpha = 0.2f),
                            Color(tierTo.color).copy(alpha = 0.05f)
                        )
                    )
                )
                .border(1.dp, Color(tierTo.color).copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.WorkspacePremium,
                    null,
                    tint = Color(tierTo.color),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "${tierTo.label} Member",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(tierTo.color),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Transaction details
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CosmosGlass)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Amount Paid", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                    Text("₹${amountPaid.toInt()}", style = MaterialTheme.typography.bodySmall, color = CosmosOnBackground, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Payment Method", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                    Text("Razorpay", style = MaterialTheme.typography.bodySmall, color = CosmosOnBackground, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Transaction ID", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                    Text(
                        if (transactionId.length > 16) "${transactionId.take(16)}…" else transactionId,
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Your subscription is now active. Enjoy your premium features!",
            style = MaterialTheme.typography.bodySmall,
            color = CosmosOnSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ── Error Step ─────────────────────────────────────────────────────────────────

@Composable
private fun ErrorStep(
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

        Spacer(Modifier.height(24.dp))

        Text(
            "Payment Failed",
            style = MaterialTheme.typography.headlineSmall,
            color = CosmosOnBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = CosmosOnSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(32.dp))

        // Retry button
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CosmosPrimary)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Refresh, null, tint = CosmosOnPrimary, modifier = Modifier.size(18.dp))
                Text("Try Again", color = CosmosOnPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Cancel / Contact support
        TextButton(onClick = onCancel) {
            Text(
                "Cancel",
                color = CosmosOnSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
