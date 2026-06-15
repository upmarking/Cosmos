package com.cosmos.app.screens.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cosmos.app.data.payment.PaymentManager
import com.cosmos.app.ui.theme.*
import com.cosmos.app.ui.components.CosmosSectionHeader
import kotlinx.coroutines.delay
import java.util.UUID

enum class PaymentMethod {
    GOOGLE_PAY, PHONE_PE, VISA
}

enum class CheckoutState {
    INPUT, PROCESSING, OTP_VERIFICATION, UPI_COUNTDOWN, SUCCESS
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckoutDialog(
    tierName: String,
    priceInUsd: Double,
    onDismiss: () -> Unit,
    onPaymentSuccess: (transactionId: String, methodUsed: String) -> Unit
) {
    val context = LocalContext.current
    // Convert USD to INR (approx 1 USD = 83 INR)
    val priceInInr = priceInUsd * 83.0

    // States
    var paymentMethod by remember { mutableStateOf(PaymentMethod.GOOGLE_PAY) }
    var checkoutState by remember { mutableStateOf(CheckoutState.INPUT) }

    // Card inputs
    var cardNumber by remember { mutableStateOf("") }
    var cardholderName by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var cardError by remember { mutableStateOf<String?>(null) }

    // UPI ID input
    var upiId by remember { mutableStateOf("") }

    // OTP verification
    var otpCode by remember { mutableStateOf("") }
    var otpError by remember { mutableStateOf<String?>(null) }

    // Simulated Details
    val billDetails = PaymentManager.calculatePayment(priceInInr)
    var generatedTxId by remember { mutableStateOf("") }

    // Determine readable method string
    val readableMethodString = when (paymentMethod) {
        PaymentMethod.GOOGLE_PAY -> "Google Pay"
        PaymentMethod.PHONE_PE -> "PhonePe"
        PaymentMethod.VISA -> "Visa Card"
    }

    // UPI Intent Launcher
    val upiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val response = result.data?.getStringExtra("response")
            if (response != null) {
                val statusMatch = response.split("&").find { it.startsWith("Status=", ignoreCase = true) }
                val status = statusMatch?.split("=")?.getOrNull(1) ?: ""
                
                if (status.equals("SUCCESS", ignoreCase = true) || status.equals("SUBMITTED", ignoreCase = true)) {
                    generatedTxId = "pay_" + UUID.randomUUID().toString().replace("-", "").take(14)
                    checkoutState = CheckoutState.SUCCESS
                } else {
                    Toast.makeText(context, "Payment Failed or Cancelled", Toast.LENGTH_SHORT).show()
                    checkoutState = CheckoutState.INPUT
                }
            } else {
                Toast.makeText(context, "Payment Failed (No response)", Toast.LENGTH_SHORT).show()
                checkoutState = CheckoutState.INPUT
            }
        } else {
            Toast.makeText(context, "Payment Cancelled", Toast.LENGTH_SHORT).show()
            checkoutState = CheckoutState.INPUT
        }
    }

    // Launch simulations
    LaunchedEffect(checkoutState) {
        if (checkoutState == CheckoutState.PROCESSING) {
            delay(2000)
            if (paymentMethod == PaymentMethod.VISA) {
                checkoutState = CheckoutState.OTP_VERIFICATION
            } else {
                checkoutState = CheckoutState.UPI_COUNTDOWN
            }
        } else if (checkoutState == CheckoutState.UPI_COUNTDOWN) {
            delay(3000) // Simulating approval in background
            generatedTxId = "pay_" + UUID.randomUUID().toString().replace("-", "").take(14)
            checkoutState = CheckoutState.SUCCESS
        } else if (checkoutState == CheckoutState.SUCCESS) {
            delay(2500)
            onPaymentSuccess(generatedTxId, readableMethodString)
        }
    }

    Dialog(
        onDismissRequest = { if (checkoutState == CheckoutState.INPUT) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .systemBarsPadding()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(CosmosBackground)
                .border(1.dp, CosmosOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header (Branded Cosmos Secure Pay)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(CosmosPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = CosmosOnPrimary, modifier = Modifier.size(16.dp))
                        }
                        Column {
                            Text("Cosmos Secure Pay", style = MaterialTheme.typography.titleMedium, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
                            Text("SECURED GATEWAY", style = MaterialTheme.typography.labelSmall, color = CosmosSuccess, letterSpacing = 1.sp)
                        }
                    }
                    if (checkoutState == CheckoutState.INPUT) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close", tint = CosmosOnBackground)
                        }
                    }
                }

                HorizontalDivider(color = CosmosOutlineVariant.copy(alpha = 0.2f), thickness = 1.dp)

                Spacer(Modifier.height(16.dp))

                Box(modifier = Modifier.weight(1f)) {
                    when (checkoutState) {
                        CheckoutState.INPUT -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                // Tier Card Summary
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Brush.linearGradient(listOf(CosmosGradientStart.copy(alpha = 0.1f), CosmosGradientEnd.copy(alpha = 0.1f))))
                                        .border(1.dp, CosmosPrimary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(tierName, style = MaterialTheme.typography.titleLarge, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
                                            Text("Cosmos Premium Membership", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                        }
                                        Text("₹${priceInInr.toInt()}", style = MaterialTheme.typography.titleLarge, color = CosmosPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(Modifier.height(24.dp))

                                // Payment Methods Tabs
                                CosmosSectionHeader("Select Payment Method")
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CosmosGlass).padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        PaymentMethod.GOOGLE_PAY,
                                        PaymentMethod.PHONE_PE,
                                        PaymentMethod.VISA
                                    ).forEach { method ->
                                        val isSelected = paymentMethod == method
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) CosmosPrimary.copy(alpha = 0.15f) else Color.Transparent)
                                                .border(1.dp, if (isSelected) CosmosPrimary.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(8.dp))
                                                .clickable { paymentMethod = method }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                when (method) {
                                                    PaymentMethod.GOOGLE_PAY -> {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                            Text("o", color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                            Text("o", color = Color(0xFFFBBC05), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                            Text("g", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                            Text("l", color = Color(0xFF34A853), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                            Text("e", color = Color(0xFFEA4335), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        }
                                                        Text("Pay", style = MaterialTheme.typography.labelSmall, color = if (isSelected) CosmosPrimary else CosmosOnSurfaceVariant, fontSize = 9.sp)
                                                    }
                                                    PaymentMethod.PHONE_PE -> {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(12.dp)
                                                                    .clip(RoundedCornerShape(3.dp))
                                                                    .background(Color(0xFF5F259F)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text("pe", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 7.sp)
                                                            }
                                                            Text("PhonePe", style = MaterialTheme.typography.labelMedium, color = if (isSelected) CosmosPrimary else CosmosOnSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                        }
                                                        Text("UPI Direct", style = MaterialTheme.typography.labelSmall, color = if (isSelected) CosmosPrimary else CosmosOnSurfaceVariant, fontSize = 9.sp)
                                                    }
                                                    PaymentMethod.VISA -> {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Icon(Icons.Outlined.CreditCard, null, tint = if (isSelected) CosmosPrimary else CosmosOnSurfaceVariant, modifier = Modifier.size(12.dp))
                                                            Text("VISA", color = if (isSelected) Color(0xFFADC6FF) else Color(0xFF908FA0), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                                        }
                                                        Text("Visa Pay", style = MaterialTheme.typography.labelSmall, color = if (isSelected) CosmosPrimary else CosmosOnSurfaceVariant, fontSize = 9.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                // Conditional Method Form
                                when (paymentMethod) {
                                    PaymentMethod.GOOGLE_PAY -> {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text("Enter Google Pay UPI ID", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                            Spacer(Modifier.height(6.dp))
                                            OutlinedTextField(
                                                value = upiId,
                                                onValueChange = { upiId = it },
                                                placeholder = { Text("username@gpay", fontSize = 13.sp) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = CosmosPrimary,
                                                    unfocusedBorderColor = CosmosOutlineVariant,
                                                    focusedTextColor = CosmosOnBackground,
                                                    unfocusedTextColor = CosmosOnBackground
                                                )
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text("Quick test shortcut:", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
                                            Spacer(Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(CosmosGlass)
                                                    .border(1.dp, CosmosGlassBorder, RoundedCornerShape(8.dp))
                                                    .clickable { upiId = "cosmos.user@okaxis" }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text("cosmos.user@okaxis", style = MaterialTheme.typography.labelSmall, color = CosmosPrimary)
                                            }
                                        }
                                    }
                                    PaymentMethod.PHONE_PE -> {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text("Enter PhonePe UPI ID", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                                            Spacer(Modifier.height(6.dp))
                                            OutlinedTextField(
                                                value = upiId,
                                                onValueChange = { upiId = it },
                                                placeholder = { Text("username@ybl", fontSize = 13.sp) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = CosmosPrimary,
                                                    unfocusedBorderColor = CosmosOutlineVariant,
                                                    focusedTextColor = CosmosOnBackground,
                                                    unfocusedTextColor = CosmosOnBackground
                                                )
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text("Quick test shortcut:", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant)
                                            Spacer(Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(CosmosGlass)
                                                    .border(1.dp, CosmosGlassBorder, RoundedCornerShape(8.dp))
                                                    .clickable { upiId = "cosmos.founder@ybl" }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Text("cosmos.founder@ybl", style = MaterialTheme.typography.labelSmall, color = CosmosPrimary)
                                            }
                                        }
                                    }
                                    PaymentMethod.VISA -> {
                                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            // Card number textfield with dynamic validation
                                            val startsWithFour = cardNumber.replace(" ", "").startsWith("4")
                                            OutlinedTextField(
                                                value = cardNumber,
                                                onValueChange = {
                                                    val raw = it.replace(" ", "").take(16)
                                                    cardNumber = raw.chunked(4).joinToString(" ")
                                                    cardError = null
                                                },
                                                placeholder = { Text("Visa Card Number (starts with 4)", fontSize = 13.sp) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.fillMaxWidth(),
                                                trailingIcon = {
                                                    if (startsWithFour) {
                                                        Box(
                                                            modifier = Modifier
                                                                .padding(end = 8.dp)
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(Color(0xFF1A1F71))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text("VISA", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                                        }
                                                    }
                                                },
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = if (startsWithFour) CosmosSuccess else CosmosPrimary,
                                                    unfocusedBorderColor = CosmosOutlineVariant,
                                                    focusedTextColor = CosmosOnBackground,
                                                    unfocusedTextColor = CosmosOnBackground
                                                )
                                            )

                                            OutlinedTextField(
                                                value = cardholderName,
                                                onValueChange = {
                                                    cardholderName = it
                                                    cardError = null
                                                },
                                                placeholder = { Text("Cardholder Name (e.g. John Doe)", fontSize = 13.sp) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = CosmosPrimary,
                                                    unfocusedBorderColor = CosmosOutlineVariant,
                                                    focusedTextColor = CosmosOnBackground,
                                                    unfocusedTextColor = CosmosOnBackground
                                                )
                                            )

                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = expiryDate,
                                                    onValueChange = {
                                                        val raw = it.replace("/", "").take(4)
                                                        expiryDate = if (raw.length >= 3) "${raw.take(2)}/${raw.drop(2)}" else raw
                                                        cardError = null
                                                    },
                                                    placeholder = { Text("MM/YY", fontSize = 13.sp) },
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(12.dp),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier.weight(1f),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = CosmosPrimary,
                                                        unfocusedBorderColor = CosmosOutlineVariant,
                                                        focusedTextColor = CosmosOnBackground,
                                                        unfocusedTextColor = CosmosOnBackground
                                                    )
                                                )
                                                OutlinedTextField(
                                                    value = cvv,
                                                    onValueChange = {
                                                        cvv = it.take(3)
                                                        cardError = null
                                                    },
                                                    placeholder = { Text("CVV", fontSize = 13.sp) },
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(12.dp),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    visualTransformation = PasswordVisualTransformation(),
                                                    modifier = Modifier.weight(1f),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = CosmosPrimary,
                                                        unfocusedBorderColor = CosmosOutlineVariant,
                                                        focusedTextColor = CosmosOnBackground,
                                                        unfocusedTextColor = CosmosOnBackground
                                                    )
                                                )
                                            }
                                            if (cardError != null) {
                                                Text(cardError!!, color = CosmosError, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(24.dp))

                                // Bill Summary
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CosmosGlass)
                                        .padding(16.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text("Subtotal", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                                            Text("₹${billDetails.subtotal.toInt()}", style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground)
                                        }
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text("GST (18%)", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                                            Text("₹${billDetails.gst.toInt()}", style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground)
                                        }
                                        HorizontalDivider(color = CosmosOutlineVariant.copy(alpha = 0.2f), thickness = 0.5.dp)
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text("Total Payable", style = MaterialTheme.typography.titleMedium, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
                                            Text("₹${billDetails.grandTotal.toInt()}", style = MaterialTheme.typography.titleMedium, color = CosmosPrimary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(24.dp))

                                // Checkout CTA Button
                                Button(
                                    onClick = {
                                        // Validate before payment
                                        if (paymentMethod == PaymentMethod.VISA) {
                                            val cleanCard = cardNumber.replace(" ", "")
                                            if (cleanCard.length != 16) {
                                                cardError = "Invalid Card Number (must be 16 digits)"
                                                return@Button
                                            }
                                            if (!cleanCard.startsWith("4")) {
                                                cardError = "Only Visa cards are supported (must start with 4)"
                                                return@Button
                                            }
                                            if (cardholderName.isBlank()) {
                                                cardError = "Cardholder Name is required"
                                                return@Button
                                            }
                                            if (expiryDate.length != 5 || !expiryDate.contains("/")) {
                                                cardError = "Invalid Expiry Date (MM/YY)"
                                                return@Button
                                            }
                                            if (cvv.length != 3) {
                                                cardError = "Invalid CVV (must be 3 digits)"
                                                return@Button
                                            }
                                            checkoutState = CheckoutState.PROCESSING
                                        } else {
                                            // UPI validation
                                            if (upiId.isBlank() || !upiId.contains("@")) {
                                                Toast.makeText(context, "Please enter a valid UPI ID", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }
                                            
                                            // Launch actual UPI Intent
                                            val uri = Uri.parse("upi://pay").buildUpon()
                                                .appendQueryParameter("pa", "merchant@upi") // Dummy merchant VPA
                                                .appendQueryParameter("pn", "Cosmos App")
                                                .appendQueryParameter("tn", tierName)
                                                .appendQueryParameter("am", billDetails.grandTotal.toInt().toString())
                                                .appendQueryParameter("cu", "INR")
                                                .build()
                                                
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                data = uri
                                                if (paymentMethod == PaymentMethod.GOOGLE_PAY) {
                                                    setPackage("com.google.android.apps.nbu.paisa.user")
                                                } else if (paymentMethod == PaymentMethod.PHONE_PE) {
                                                    setPackage("com.phonepe.app")
                                                }
                                            }
                                            
                                            try {
                                                upiLauncher.launch(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "UPI App not found! Proceeding with simulation.", Toast.LENGTH_SHORT).show()
                                                checkoutState = CheckoutState.PROCESSING
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CosmosPrimary)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                         Icon(Icons.Default.Lock, null, tint = CosmosOnPrimary, modifier = Modifier.size(16.dp))
                                         Text("Pay ₹${billDetails.grandTotal.toInt()} via $readableMethodString", color = CosmosOnPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                                Spacer(Modifier.height(20.dp))
                            }
                        }
                        CheckoutState.PROCESSING -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = CosmosPrimary, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("Securing payment with Cosmos Secure Pay...", style = MaterialTheme.typography.titleMedium, color = CosmosOnBackground)
                                Text("Initializing transaction via $readableMethodString...", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        CheckoutState.OTP_VERIFICATION -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(Color(0xFF1A1F71).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("VISA", color = Color(0xFFADC6FF), fontWeight = FontWeight.Bold, fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                }
                                Spacer(Modifier.height(12.dp))
                                Text("Verified by Visa", style = MaterialTheme.typography.titleMedium, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
                                Text("Enter the 6-digit verification code sent to your mobile device ending in **${cardNumber.takeLast(4)}** (Test OTP: 123456).", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                                Spacer(Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = otpCode,
                                    onValueChange = {
                                        otpCode = it.take(6)
                                        otpError = null
                                    },
                                    placeholder = { Text("123456") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CosmosPrimary,
                                        unfocusedBorderColor = CosmosOutlineVariant,
                                        focusedTextColor = CosmosOnBackground,
                                        unfocusedTextColor = CosmosOnBackground
                                    )
                                )
                                if (otpError != null) {
                                    Text(otpError!!, color = CosmosError, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                                }
                                Spacer(Modifier.height(20.dp))
                                Button(
                                    onClick = {
                                        if (otpCode == "123456" || otpCode.length == 6) {
                                            generatedTxId = "pay_" + UUID.randomUUID().toString().replace("-", "").take(14)
                                            checkoutState = CheckoutState.SUCCESS
                                        } else {
                                            otpError = "Incorrect OTP. Try entering 123456"
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CosmosGradientStart),
                                    modifier = Modifier.width(180.dp)
                                ) {
                                    Text("Verify", color = Color.White)
                                }
                            }
                        }
                        CheckoutState.UPI_COUNTDOWN -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = CosmosSuccess, modifier = Modifier.size(44.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("Awaiting UPI Approval", style = MaterialTheme.typography.titleMedium, color = CosmosOnBackground)
                                Text("A collect request of ₹${billDetails.grandTotal.toInt()} has been sent to your registered UPI address ($upiId) via $readableMethodString. Please open the app and authorize the transaction.", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("Simulating auto-approval in 3 seconds...", style = MaterialTheme.typography.labelMedium, color = CosmosPrimary)
                            }
                        }
                        CheckoutState.SUCCESS -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(32.dp))
                                        .background(CosmosSuccess.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, tint = CosmosSuccess, modifier = Modifier.size(48.dp))
                                }
                                Spacer(Modifier.height(16.dp))
                                Text("Payment Successful! 🎉", style = MaterialTheme.typography.headlineSmall, color = CosmosOnBackground, fontWeight = FontWeight.Bold)
                                Text("Your subscription is now active.", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                                Spacer(Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CosmosGlass)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Paid via $readableMethodString", style = MaterialTheme.typography.bodyMedium, color = CosmosPrimary, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(12.dp))
                                Text("Transaction ID: $generatedTxId", style = MaterialTheme.typography.labelSmall, color = CosmosOnSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
