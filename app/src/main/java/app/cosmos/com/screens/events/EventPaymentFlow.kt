package app.cosmos.com.screens.events

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cosmos.com.data.model.EventPaymentRecord
import app.cosmos.com.data.model.NetworkEvent
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

// ── Payment Step Enum ────────────────────────────────────────────────────────

enum class PaymentStep {
    SUMMARY, INSTRUCTIONS, CONFIRMATION, SUCCESS
}

// ── Main Payment Bottom Sheet ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaidEventRegistrationSheet(
    event: NetworkEvent,
    userName: String,
    userEmail: String,
    isRegistering: Boolean,
    onRegisterWithPayment: (transactionId: String) -> Unit,
    onDismiss: () -> Unit,
    paymentRecord: EventPaymentRecord? = null
) {
    var currentStep by remember { mutableStateOf(PaymentStep.SUMMARY) }
    var transactionId by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = {
            if (currentStep != PaymentStep.SUCCESS) onDismiss()
        },
        containerColor = Color(0xFF12151A),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CosmosOnSurfaceVariant.copy(alpha = 0.3f))
                )
                Spacer(Modifier.height(16.dp))

                // Step progress dots
                if (currentStep != PaymentStep.SUCCESS) {
                    val stepIndex = PaymentStep.entries.indexOf(currentStep)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        PaymentStep.entries.filter { it != PaymentStep.SUCCESS }.forEachIndexed { index, _ ->
                            val isActive = index == stepIndex
                            val isCompleted = index < stepIndex
                            Box(
                                modifier = Modifier
                                    .width(if (isActive) 24.dp else 8.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        when {
                                            isCompleted -> CosmosPrimary
                                            isActive -> CosmosPrimary.copy(alpha = 0.8f)
                                            else -> CosmosOutlineVariant.copy(alpha = 0.3f)
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }
    ) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                if (forward) {
                    (slideInHorizontally { it } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally { -it } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally { -it } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally { it } + fadeOut(tween(200)))
                }
            },
            label = "PaymentStepTransition"
        ) { step ->
            when (step) {
                PaymentStep.SUMMARY -> PaymentSummaryStep(
                    event = event,
                    userName = userName,
                    onNext = { currentStep = PaymentStep.INSTRUCTIONS },
                    onCancel = onDismiss
                )
                PaymentStep.INSTRUCTIONS -> PaymentInstructionsStep(
                    event = event,
                    onNext = { currentStep = PaymentStep.CONFIRMATION },
                    onBack = { currentStep = PaymentStep.SUMMARY }
                )
                PaymentStep.CONFIRMATION -> PaymentConfirmationStep(
                    event = event,
                    transactionId = transactionId,
                    onTransactionIdChange = { transactionId = it },
                    isRegistering = isRegistering,
                    onConfirm = { onRegisterWithPayment(transactionId) },
                    onBack = { currentStep = PaymentStep.INSTRUCTIONS }
                )
                PaymentStep.SUCCESS -> {
                    // Handled via overlay, not in sheet
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── Step 1: Payment Summary ──────────────────────────────────────────────────

@Composable
private fun PaymentSummaryStep(
    event: NetworkEvent,
    userName: String,
    onNext: () -> Unit,
    onCancel: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Event header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(listOf(CosmosGradientStart, CosmosGradientEnd))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ConfirmationNumber, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Column {
                    Text(
                        event.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = CosmosOnBackground,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "\uD83D\uDCC5 ${event.date} · \uD83D\uDCCD ${event.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(color = CosmosOutlineVariant.copy(alpha = 0.15f))

            // Price breakdown card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CosmosSurfaceContainerLowest)
                    .border(1.dp, CosmosOutlineVariant.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "PAYMENT SUMMARY",
                        style = MaterialTheme.typography.labelSmall,
                        color = CosmosPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Participant", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                        Text(userName, style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground, fontWeight = FontWeight.Medium)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Event Entry", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                        Text(event.price, style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground, fontWeight = FontWeight.Medium)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Spots Remaining", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                        Text(
                            "${event.spotsRemaining} / ${event.maxParticipants}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (event.spotsRemaining <= 5) CosmosError else CosmosSuccess,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    HorizontalDivider(color = CosmosOutlineVariant.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total", style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
                        Text(event.price, style = MaterialTheme.typography.headlineSmall, color = CosmosPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Urgency badge
            if (event.spotsRemaining <= 10) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CosmosError.copy(alpha = 0.08f))
                        .border(1.dp, CosmosError.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text("\uD83D\uDD25", fontSize = 16.sp)
                    Text(
                        "Only ${event.spotsRemaining} spots left! Secure yours now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosError,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Proceed button
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(CosmosGradientStart, CosmosGradientEnd)), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Proceed to Pay", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                }
            }

            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = CosmosOnSurfaceVariant)
            }
        }
    }
}

// ── Step 2: Payment Instructions ─────────────────────────────────────────────

@Composable
private fun PaymentInstructionsStep(
    event: NetworkEvent,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); visible = true }

    val infiniteTransition = rememberInfiniteTransition(label = "PricePulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with back
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = CosmosOnSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                Text("Complete Payment", style = MaterialTheme.typography.titleMedium, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
            }

            // Animated amount display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(pulseScale)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(CosmosGradientStart.copy(alpha = 0.15f), CosmosGradientEnd.copy(alpha = 0.08f))))
                    .border(1.dp, CosmosPrimary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PAY", style = MaterialTheme.typography.labelSmall, color = CosmosPrimary.copy(alpha = 0.7f), letterSpacing = 2.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(event.price, style = MaterialTheme.typography.displaySmall, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
                }
            }

            // UPI Details Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CosmosSurfaceContainerLowest)
                    .border(1.dp, CosmosOutlineVariant.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("UPI PAYMENT DETAILS", style = MaterialTheme.typography.labelSmall, color = CosmosPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    // UPI ID row with copy
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CosmosSurfaceContainerHigh)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                            Text("\uD83D\uDCF1", fontSize = 20.sp)
                            Column {
                                Text("UPI ID", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
                                Text(event.paymentUpiId, style = MaterialTheme.typography.bodyLarge, color = CosmosPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", event.paymentUpiId))
                                Toast.makeText(context, "UPI ID copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = CosmosPrimary, modifier = Modifier.size(18.dp))
                        }
                    }

                    if (event.paymentAccountName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Person, null, tint = CosmosOnSurfaceVariant, modifier = Modifier.size(18.dp))
                            Column {
                                Text("Account Holder", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
                                Text(event.paymentAccountName, style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    if (event.paymentInstructions.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CosmosPrimary.copy(alpha = 0.06f)).padding(12.dp)
                        ) {
                            Icon(Icons.Default.Info, null, tint = CosmosPrimary, modifier = Modifier.size(16.dp))
                            Text(event.paymentInstructions, style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                        }
                    }
                }
            }

            // Open UPI App button
            OutlinedButton(
                onClick = {
                    val amount = event.priceAmount
                    val upiUri = "upi://pay?pa=${event.paymentUpiId}&pn=${event.paymentAccountName}&am=$amount&cu=${event.currency}&tn=Cosmos Event: ${event.title}"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                    try {
                        context.startActivity(Intent.createChooser(intent, "Pay with UPI"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "No UPI app found", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CosmosPrimary),
                border = BorderStroke(1.dp, CosmosPrimary.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.OpenInNew, null, tint = CosmosPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open UPI App", color = CosmosPrimary, fontWeight = FontWeight.Bold)
            }

            // I've Paid button
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(CosmosGradientStart, CosmosGradientEnd)), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("I've Made the Payment \u2192", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

// ── Step 3: Payment Confirmation ─────────────────────────────────────────────

@Composable
private fun PaymentConfirmationStep(
    event: NetworkEvent,
    transactionId: String,
    onTransactionIdChange: (String) -> Unit,
    isRegistering: Boolean,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = CosmosOnSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                Text("Confirm Payment", style = MaterialTheme.typography.titleMedium, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
            }

            // Instruction card
            Box(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CosmosPrimary.copy(alpha = 0.06f))
                    .border(1.dp, CosmosPrimary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Verified, null, tint = CosmosPrimary, modifier = Modifier.size(20.dp))
                    Column {
                        Text("Enter your UPI transaction reference", style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text("Find the UTR/Reference number in your UPI app's transaction history.", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                    }
                }
            }

            // Transaction ID input
            OutlinedTextField(
                value = transactionId,
                onValueChange = onTransactionIdChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Transaction ID / UTR Number") },
                placeholder = { Text("e.g. 412345678901", color = CosmosOnSurfaceVariant.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Default.Receipt, null, tint = CosmosPrimary, modifier = Modifier.padding(start = 4.dp)) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CosmosPrimary,
                    unfocusedBorderColor = CosmosOutlineVariant.copy(alpha = 0.5f),
                    focusedTextColor = CosmosOnBackground,
                    unfocusedTextColor = CosmosOnBackground,
                    cursorColor = CosmosPrimary,
                    focusedLabelColor = CosmosPrimary,
                    unfocusedLabelColor = CosmosOnSurfaceVariant,
                    focusedContainerColor = CosmosSurfaceContainerLowest,
                    unfocusedContainerColor = CosmosSurfaceContainerLowest
                ),
                singleLine = true
            )

            // Payment mini summary
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CosmosSurfaceContainerLowest).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Amount Paid", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
                    Text(event.price, style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("To", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
                    Text(event.paymentUpiId, style = MaterialTheme.typography.titleSmall, color = CosmosPrimary, fontWeight = FontWeight.Medium)
                }
            }

            // Confirm button
            Button(
                onClick = onConfirm,
                enabled = transactionId.isNotBlank() && !isRegistering,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = CosmosSurfaceContainerHigh
                ),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        if (transactionId.isNotBlank() && !isRegistering)
                            Brush.horizontalGradient(listOf(CosmosGradientStart, CosmosGradientEnd))
                        else
                            Brush.horizontalGradient(listOf(CosmosSurfaceContainerHigh, CosmosSurfaceContainerHigh)),
                        RoundedCornerShape(14.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRegistering) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Text("Processing...", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            "Confirm & Register \u2713",
                            fontWeight = FontWeight.Bold,
                            color = if (transactionId.isNotBlank()) Color.White else CosmosOnSurfaceVariant,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Security note
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = CosmosOnSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Your payment details are secured & encrypted", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant.copy(alpha = 0.4f))
            }
        }
    }
}

// ── Success Overlay (Cinematic) ──────────────────────────────────────────────

private data class PaymentParticle(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val delay: Int
)

@Composable
fun EventPaymentSuccessOverlay(
    event: NetworkEvent,
    paymentRecord: EventPaymentRecord,
    onDismiss: () -> Unit
) {
    var showOverlay by remember { mutableStateOf(false) }
    var showIcon by remember { mutableStateOf(false) }
    var showParticles by remember { mutableStateOf(false) }
    var showTitle by remember { mutableStateOf(false) }
    var showTicket by remember { mutableStateOf(false) }
    var showChecks by remember { mutableStateOf(false) }

    val particles = remember {
        List(25) {
            PaymentParticle(
                angle = Random.nextFloat() * 2f * PI.toFloat(),
                speed = Random.nextFloat() * 3f + 1f,
                size = Random.nextFloat() * 3.5f + 1f,
                color = listOf(CosmosPrimary, CosmosGradientEnd, CosmosSunGlow, CosmosStarWhite, CosmosSuccess).random(),
                delay = Random.nextInt(300)
            )
        }
    }

    val iconScale = remember { Animatable(0f) }
    val particleProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        showOverlay = true
        delay(200)
        showIcon = true
        iconScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        showParticles = true
        particleProgress.animateTo(1f, animationSpec = tween(1200, easing = EaseOutCubic))
        delay(200)
        showTitle = true
        delay(400)
        showTicket = true
        delay(300)
        showChecks = true
        delay(3500)
        onDismiss()
    }

    AnimatedVisibility(visible = showOverlay, enter = fadeIn(tween(300))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CosmosCosmicDeep.copy(alpha = 0.95f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                // Particles
                if (showParticles) {
                    Canvas(modifier = Modifier.size(180.dp)) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        particles.forEach { particle ->
                            val progress = particleProgress.value
                            val distance = particle.speed * progress * 80f
                            val particleAlpha = (1f - progress).coerceIn(0f, 1f)
                            drawCircle(
                                color = particle.color.copy(alpha = particleAlpha * 0.8f),
                                radius = particle.size * (1f - progress * 0.5f),
                                center = Offset(
                                    center.x + cos(particle.angle) * distance,
                                    center.y + sin(particle.angle) * distance
                                )
                            )
                        }
                    }
                }

                if (showIcon) {
                    Box(modifier = Modifier.offset(y = (-80).dp).scale(iconScale.value)) {
                        Text("\u2705", fontSize = 56.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                AnimatedVisibility(
                    visible = showTitle,
                    enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 2 }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("REGISTERED", style = MaterialTheme.typography.labelMedium, color = CosmosSuccess, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(event.title, style = MaterialTheme.typography.headlineSmall, color = CosmosStarWhite, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Text("Your spot is confirmed. See you there! \uD83D\uDE80", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Ticket card
                AnimatedVisibility(
                    visible = showTicket,
                    enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(tween(300))
                ) {
                    EventTicketCard(event = event, paymentRecord = paymentRecord)
                }

                Spacer(Modifier.height(20.dp))

                // Staggered checklist
                AnimatedVisibility(visible = showChecks, enter = fadeIn(tween(400))) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        listOf(
                            "Payment recorded \u2014 ${paymentRecord.receiptId}",
                            "Registration confirmed",
                            "You'll be notified before the event",
                            "Check \"My Meetings\" for schedule"
                        ).forEachIndexed { index, check ->
                            var itemVisible by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) { delay(index * 200L); itemVisible = true }
                            AnimatedVisibility(
                                visible = itemVisible,
                                enter = fadeIn(tween(300)) + slideInHorizontally(tween(300)) { -it }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.CheckCircle, null, tint = CosmosSuccess, modifier = Modifier.size(16.dp))
                                    Text(check, style = MaterialTheme.typography.bodySmall, color = CosmosStarWhite)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Digital Ticket Card ──────────────────────────────────────────────────────

@Composable
fun EventTicketCard(
    event: NetworkEvent,
    paymentRecord: EventPaymentRecord,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1A1D28), Color(0xFF141722))))
            .border(1.dp, Brush.linearGradient(listOf(CosmosPrimary.copy(alpha = 0.4f), CosmosGradientEnd.copy(alpha = 0.2f))), RoundedCornerShape(18.dp))
    ) {
        Column {
            // Header
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(CosmosGradientStart.copy(alpha = 0.25f), CosmosGradientEnd.copy(alpha = 0.15f))))
                    .padding(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("COSMOS EVENT TICKET", style = MaterialTheme.typography.labelSmall, color = CosmosPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(event.title, style = MaterialTheme.typography.titleSmall, color = CosmosStarWhite, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("\uD83C\uDFAB", fontSize = 28.sp)
                }
            }

            // Dashed line
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(20) {
                    Box(modifier = Modifier.width(8.dp).height(1.dp).background(CosmosOutlineVariant.copy(alpha = 0.3f)))
                    Spacer(Modifier.width(4.dp))
                }
            }

            // Body
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TicketDetail("Date", event.date)
                    TicketDetail("Time", event.time, align = Alignment.End)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TicketDetail("Amount", event.price)
                    TicketDetail("Status", "CONFIRMED \u2713", align = Alignment.End, valueColor = CosmosSuccess)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TicketDetail("Receipt", paymentRecord.receiptId)
                    TicketDetail("Txn ID", paymentRecord.transactionId, align = Alignment.End)
                }
            }
        }
    }
}

@Composable
private fun TicketDetail(
    label: String,
    value: String,
    align: Alignment.Horizontal = Alignment.Start,
    valueColor: Color = CosmosStarWhite
) {
    Column(horizontalAlignment = align) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
