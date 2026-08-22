package app.cosmos.com.screens.events

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cosmos.com.data.model.EventPaymentRecord
import app.cosmos.com.data.model.EventTicketOrder
import app.cosmos.com.data.model.NetworkEvent
import app.cosmos.com.data.payment.RazorpayPaymentHelper
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*
import app.cosmos.com.ui.viewmodel.EventViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

// ── Checkout Step Enum ────────────────────────────────────────────────────────

enum class EventCheckoutStep {
    REVIEW,
    CREATING_ORDER,
    AWAITING_PAYMENT,
    VERIFYING,
    ERROR
}

// ── Main Razorpay Event Payment Bottom Sheet ──────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaidEventRegistrationSheet(
    event: NetworkEvent,
    eventViewModel: EventViewModel,
    initialUserName: String,
    initialUserEmail: String,
    onPaymentSuccess: (EventPaymentRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var checkoutStep by remember { mutableStateOf(EventCheckoutStep.REVIEW) }
    var attendeeName by remember { mutableStateOf(initialUserName) }
    var attendeeEmail by remember { mutableStateOf(initialUserEmail) }
    var attendeePhone by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var createdOrder by remember { mutableStateOf<EventTicketOrder?>(null) }

    // Resolve parent activity for Razorpay Checkout SDK
    val activity = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) break
            ctx = ctx.baseContext
        }
        ctx as? Activity
    }

    // Register Razorpay Callbacks
    DisposableEffect(Unit) {
        RazorpayPaymentHelper.registerCallbacks(
            onSuccess = { paymentId, orderId, signature ->
                checkoutStep = EventCheckoutStep.VERIFYING
                val currentOrder = createdOrder
                val finalOrderId = if (orderId.isNotBlank()) orderId else (currentOrder?.orderId ?: "")

                eventViewModel.verifyTicketPayment(
                    eventId = event.id,
                    orderId = finalOrderId,
                    paymentId = paymentId,
                    signature = signature,
                    userName = attendeeName.trim(),
                    userEmail = attendeeEmail.trim(),
                    onSuccess = { paymentRecord ->
                        onPaymentSuccess(paymentRecord)
                    },
                    onError = { error ->
                        errorMessage = error
                        checkoutStep = EventCheckoutStep.ERROR
                    }
                )
            },
            onError = { code, message ->
                errorMessage = RazorpayPaymentHelper.getReadableError(code, message)
                checkoutStep = if (code == 1) EventCheckoutStep.REVIEW else EventCheckoutStep.ERROR
            }
        )

        onDispose {
            RazorpayPaymentHelper.clearCallbacks()
        }
    }

    val ticketPriceInr = remember(event) {
        if (event.priceAmount > 0) event.priceAmount.toInt()
        else event.price.replace(Regex("[^0-9.]"), "").toDoubleOrNull()?.toInt() ?: 0
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (checkoutStep != EventCheckoutStep.CREATING_ORDER && checkoutStep != EventCheckoutStep.VERIFYING) {
                onDismiss()
            }
        },
        containerColor = Color(0xFF0F121A),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CosmosOutlineVariant.copy(alpha = 0.4f))
                )
                Spacer(Modifier.height(14.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            when (checkoutStep) {
                EventCheckoutStep.REVIEW -> {
                    // Header & Event Hero Badge
                    EventCheckoutHeader(event = event)

                    // Attendee Information Input
                    AttendeeDetailsSection(
                        name = attendeeName,
                        onNameChange = { attendeeName = it },
                        email = attendeeEmail,
                        onEmailChange = { attendeeEmail = it },
                        phone = attendeePhone,
                        onPhoneChange = { attendeePhone = it }
                    )

                    // Price Breakdown
                    TicketOrderSummaryCard(
                        event = event,
                        ticketPriceInr = ticketPriceInr
                    )

                    // Trust & Platform Razorpay Badge
                    RazorpayTrustBadge()

                    // CTA Button: Launch Razorpay
                    val isFormValid = attendeeName.isNotBlank() && attendeeEmail.isNotBlank() && attendeeEmail.contains("@")
                    Button(
                        onClick = {
                            if (activity == null) {
                                Toast.makeText(context, "Activity not ready for Razorpay", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            checkoutStep = EventCheckoutStep.CREATING_ORDER
                            eventViewModel.createTicketOrder(
                                eventId = event.id,
                                userName = attendeeName.trim(),
                                userEmail = attendeeEmail.trim(),
                                userContact = attendeePhone.trim(),
                                onSuccess = { order ->
                                    createdOrder = order
                                    checkoutStep = EventCheckoutStep.AWAITING_PAYMENT
                                    RazorpayPaymentHelper.startEventTicketPayment(
                                        activity = activity,
                                        orderId = order.orderId,
                                        amountInInr = order.amount,
                                        keyId = order.keyId,
                                        eventTitle = event.title,
                                        eventId = event.id,
                                        userName = attendeeName.trim(),
                                        userEmail = attendeeEmail.trim(),
                                        userContact = attendeePhone.trim()
                                    )
                                },
                                onError = { error ->
                                    errorMessage = error
                                    checkoutStep = EventCheckoutStep.ERROR
                                }
                            )
                        },
                        enabled = isFormValid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = CosmosSurfaceContainerHigh
                        ),
                        contentPadding = PaddingValues()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isFormValid)
                                        Brush.horizontalGradient(listOf(CosmosGradientStart, CosmosGradientEnd))
                                    else
                                        Brush.horizontalGradient(listOf(CosmosSurfaceContainerHigh, CosmosSurfaceContainerHigh)),
                                    RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Payment, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Text(
                                    text = if (ticketPriceInr > 0) "Pay ₹$ticketPriceInr with Razorpay →" else "Confirm Pass →",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = if (isFormValid) Color.White else CosmosOnSurfaceVariant
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", color = CosmosOnSurfaceVariant)
                    }
                }

                EventCheckoutStep.CREATING_ORDER, EventCheckoutStep.AWAITING_PAYMENT, EventCheckoutStep.VERIFYING -> {
                    PaymentProcessingStateView(step = checkoutStep, event = event)
                }

                EventCheckoutStep.ERROR -> {
                    PaymentErrorStateView(
                        errorMessage = errorMessage,
                        onRetry = {
                            errorMessage = ""
                            checkoutStep = EventCheckoutStep.REVIEW
                        },
                        onDismiss = onDismiss
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── Overload for backwards compatibility ──────────────────────────────────────

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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F121A),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            EventCheckoutHeader(event = event)
            TicketOrderSummaryCard(event = event, ticketPriceInr = event.priceAmount.toInt())
            RazorpayTrustBadge()

            Button(
                onClick = { onRegisterWithPayment("rzp_pay_${System.currentTimeMillis()}") },
                enabled = !isRegistering,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(listOf(CosmosGradientStart, CosmosGradientEnd)),
                        RoundedCornerShape(16.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRegistering) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text("Proceed with Razorpay →", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// ── Step Component: Event Checkout Header ─────────────────────────────────────

@Composable
private fun EventCheckoutHeader(event: NetworkEvent) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        CosmosSurfaceContainerLowest,
                        CosmosSurfaceContainerHigh.copy(alpha = 0.5f)
                    )
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        CosmosPrimary.copy(alpha = 0.35f),
                        CosmosGradientEnd.copy(alpha = 0.15f)
                    )
                ),
                RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(CosmosGradientStart, CosmosGradientEnd))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        event.type.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = CosmosPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    event.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = CosmosOnBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "📅 ${event.date} · ⏰ ${event.time}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CosmosOnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Attendee Details Section ──────────────────────────────────────────────────

@Composable
private fun AttendeeDetailsSection(
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "ATTENDEE PASS DETAILS",
            style = MaterialTheme.typography.labelSmall,
            color = CosmosPrimary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Full Name") },
            placeholder = { Text("Your name for badge & ticket") },
            leadingIcon = { Icon(Icons.Default.Person, null, tint = CosmosPrimary, modifier = Modifier.size(18.dp)) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CosmosPrimary,
                unfocusedBorderColor = CosmosOutlineVariant.copy(alpha = 0.4f),
                focusedTextColor = CosmosOnBackground,
                unfocusedTextColor = CosmosOnBackground,
                focusedContainerColor = CosmosSurfaceContainerLowest,
                unfocusedContainerColor = CosmosSurfaceContainerLowest
            ),
            singleLine = true
        )

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email Address") },
            placeholder = { Text("Ticket confirmation & QR receipt sent here") },
            leadingIcon = { Icon(Icons.Default.Email, null, tint = CosmosPrimary, modifier = Modifier.size(18.dp)) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CosmosPrimary,
                unfocusedBorderColor = CosmosOutlineVariant.copy(alpha = 0.4f),
                focusedTextColor = CosmosOnBackground,
                unfocusedTextColor = CosmosOnBackground,
                focusedContainerColor = CosmosSurfaceContainerLowest,
                unfocusedContainerColor = CosmosSurfaceContainerLowest
            ),
            singleLine = true
        )
    }
}

// ── Ticket Order Breakdown Card ───────────────────────────────────────────────

@Composable
private fun TicketOrderSummaryCard(
    event: NetworkEvent,
    ticketPriceInr: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CosmosSurfaceContainerLowest)
            .border(1.dp, CosmosOutlineVariant.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ORDER SUMMARY", style = MaterialTheme.typography.labelSmall, color = CosmosPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                if (event.spotsRemaining <= 10) {
                    Text("🔥 ${event.spotsRemaining} spots left", style = MaterialTheme.typography.labelSmall, color = CosmosError, fontWeight = FontWeight.Bold)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1x Event Admission Pass", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                Text("₹$ticketPriceInr", style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground, fontWeight = FontWeight.Medium)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Platform & Processing Fee", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                Text("FREE", style = MaterialTheme.typography.bodyMedium, color = CosmosSuccess, fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(color = CosmosOutlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total Amount", style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
                    Text("Centralized platform checkout", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant.copy(alpha = 0.6f))
                }
                Text("₹$ticketPriceInr", style = MaterialTheme.typography.headlineSmall, color = CosmosPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Razorpay Trust Badge ──────────────────────────────────────────────────────

@Composable
private fun RazorpayTrustBadge() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CosmosPrimary.copy(alpha = 0.08f))
            .border(1.dp, CosmosPrimary.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, tint = CosmosPrimary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            "Secured by Razorpay • 256-bit SSL • All UPI & Cards Accepted",
            style = MaterialTheme.typography.labelSmall,
            color = CosmosPrimary.copy(alpha = 0.9f),
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Processing State View ─────────────────────────────────────────────────────

@Composable
private fun PaymentProcessingStateView(
    step: EventCheckoutStep,
    event: NetworkEvent
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PulseRing")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "RingScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .scale(ringScale)
                    .clip(CircleShape)
                    .background(CosmosPrimary.copy(alpha = 0.15f))
            )
            CircularProgressIndicator(
                modifier = Modifier.size(60.dp),
                color = CosmosPrimary,
                strokeWidth = 3.dp
            )
        }

        Text(
            text = when (step) {
                EventCheckoutStep.CREATING_ORDER -> "Creating Secure Order..."
                EventCheckoutStep.AWAITING_PAYMENT -> "Completing Payment in Razorpay..."
                EventCheckoutStep.VERIFYING -> "Verifying Payment & Issuing Ticket..."
                else -> "Processing..."
            },
            style = MaterialTheme.typography.titleMedium,
            color = CosmosStarWhite,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Your ticket for \"${event.title}\" is being processed securely on the COSMOS network.",
            style = MaterialTheme.typography.bodySmall,
            color = CosmosOnSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

// ── Error State View ──────────────────────────────────────────────────────────

@Composable
private fun PaymentErrorStateView(
    errorMessage: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(CosmosError.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ErrorOutline, null, tint = CosmosError, modifier = Modifier.size(32.dp))
        }

        Text(
            "Payment Failed",
            style = MaterialTheme.typography.titleMedium,
            color = CosmosError,
            fontWeight = FontWeight.Bold
        )

        Text(
            errorMessage.ifBlank { "An error occurred while processing your ticket payment. Please try again." },
            style = MaterialTheme.typography.bodySmall,
            color = CosmosOnSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Dismiss", color = CosmosOnSurfaceVariant)
            }

            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CosmosPrimary)
            ) {
                Text("Try Again", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Cinematic Digital Ticket Pass Overlay ──────────────────────────────────────

private data class CosmicParticle(
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
    val context = LocalContext.current
    var showOverlay by remember { mutableStateOf(false) }
    var showParticles by remember { mutableStateOf(false) }
    var showTicket by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    val particles = remember {
        List(30) {
            CosmicParticle(
                angle = Random.nextFloat() * 2f * PI.toFloat(),
                speed = Random.nextFloat() * 3.5f + 1.2f,
                size = Random.nextFloat() * 4f + 1.5f,
                color = listOf(CosmosPrimary, CosmosGradientEnd, CosmosSunGlow, CosmosStarWhite, CosmosSuccess).random(),
                delay = Random.nextInt(250)
            )
        }
    }

    val particleProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        showOverlay = true
        delay(150)
        showParticles = true
        particleProgress.animateTo(1f, animationSpec = tween(1200, easing = EaseOutCubic))
        delay(200)
        showTicket = true
        delay(400)
        showActions = true
    }

    AnimatedVisibility(visible = showOverlay, enter = fadeIn(tween(300))) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CosmosCosmicDeep.copy(alpha = 0.96f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Particles Explosion
                if (showParticles) {
                    Canvas(modifier = Modifier.size(160.dp)) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        particles.forEach { p ->
                            val progress = particleProgress.value
                            val distance = p.speed * progress * 90f
                            val pAlpha = (1f - progress).coerceIn(0f, 1f)
                            drawCircle(
                                color = p.color.copy(alpha = pAlpha * 0.9f),
                                radius = p.size * (1f - progress * 0.4f),
                                center = Offset(
                                    center.x + cos(p.angle) * distance,
                                    center.y + sin(p.angle) * distance
                                )
                            )
                        }
                    }
                }

                // Success Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(CosmosSuccess.copy(alpha = 0.15f))
                        .border(1.dp, CosmosSuccess.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("✨", fontSize = 16.sp)
                    Text("TICKET CONFIRMED", style = MaterialTheme.typography.labelSmall, color = CosmosSuccess, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }

                Spacer(Modifier.height(16.dp))

                // Premium Holographic Ticket Card
                AnimatedVisibility(
                    visible = showTicket,
                    enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) + fadeIn(tween(400))
                ) {
                    EventTicketCard(event = event, paymentRecord = paymentRecord)
                }

                Spacer(Modifier.height(20.dp))

                // Action Buttons
                AnimatedVisibility(visible = showActions, enter = fadeIn(tween(400))) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(listOf(CosmosGradientStart, CosmosGradientEnd)),
                                        RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Enter Event Lobby 🚀", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Cosmos Ticket Receipt", paymentRecord.receiptId))
                                Toast.makeText(context, "Ticket receipt copied to clipboard! 📋", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CosmosOutlineVariant.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CosmosPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Copy Receipt (${paymentRecord.receiptId.takeLast(8)})", color = CosmosStarWhite, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

// ── Holographic Digital Ticket Card ───────────────────────────────────────────

@Composable
fun EventTicketCard(
    event: NetworkEvent,
    paymentRecord: EventPaymentRecord,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF191D2B),
                        Color(0xFF111420)
                    )
                )
            )
            .border(
                1.5.dp,
                Brush.linearGradient(
                    listOf(
                        CosmosPrimary.copy(alpha = 0.6f),
                        CosmosGradientEnd.copy(alpha = 0.3f),
                        CosmosPrimary.copy(alpha = 0.1f)
                    )
                ),
                RoundedCornerShape(22.dp)
            )
    ) {
        Column {
            // Header with Cosmic Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                CosmosGradientStart.copy(alpha = 0.35f),
                                CosmosGradientEnd.copy(alpha = 0.2f)
                            )
                        )
                    )
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("COSMOS DELEGATE PASS", style = MaterialTheme.typography.labelSmall, color = CosmosPrimary, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(event.title, style = MaterialTheme.typography.titleMedium, color = CosmosStarWhite, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("🎟️", fontSize = 32.sp)
                }
            }

            // Perforated Dashed Divider Line with side cutouts simulation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(24) {
                    Box(modifier = Modifier.width(6.dp).height(1.5.dp).background(CosmosOutlineVariant.copy(alpha = 0.35f)))
                    Spacer(Modifier.width(4.dp))
                }
            }

            // Ticket Body Grid
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TicketDetail("ATTENDEE", paymentRecord.participantName.ifBlank { "Cosmos Member" })
                    TicketDetail("STATUS", "CONFIRMED ✓", align = Alignment.End, valueColor = CosmosSuccess)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TicketDetail("DATE", event.date)
                    TicketDetail("TIME", event.time, align = Alignment.End)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TicketDetail("AMOUNT PAID", if (paymentRecord.amount > 0) "₹${paymentRecord.amount.toInt()}" else event.price)
                    TicketDetail("PAYMENT", "Razorpay Secured", align = Alignment.End, valueColor = CosmosPrimary)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TicketDetail("RECEIPT ID", paymentRecord.receiptId)
                    TicketDetail("TXN ID", paymentRecord.transactionId.take(14), align = Alignment.End)
                }

                Spacer(Modifier.height(4.dp))

                // Barcode Aesthetic Representation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(36) { index ->
                            val height = if (index % 3 == 0) 24.dp else if (index % 2 == 0) 18.dp else 12.dp
                            val width = if (index % 5 == 0) 3.dp else 1.5.dp
                            Box(
                                modifier = Modifier
                                    .width(width)
                                    .height(height)
                                    .background(CosmosStarWhite.copy(alpha = 0.5f))
                            )
                        }
                    }
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
        Text(label, style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant.copy(alpha = 0.6f), letterSpacing = 0.5.sp)
        Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

