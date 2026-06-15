package com.cosmos.app.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cosmos.app.ui.components.*
import com.cosmos.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onSignIn: () -> Unit,
    initialShowSignIn: Boolean = false,
    authViewModel: com.cosmos.app.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var visible by remember { mutableStateOf(false) }
    var showSignIn by remember { mutableStateOf(initialShowSignIn) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf("") }
    var forgotPasswordMode by remember { mutableStateOf(false) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val isLoading by authViewModel.isLoading.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    LaunchedEffect(Unit) {
        delay(200)
        visible = true
    }

    LaunchedEffect(currentUser) {
        // Only auto-navigate when the user is on the Sign In panel.
        // During registration, navigation is handled by the signUp callback.
        if (currentUser != null && showSignIn) {
            onSignIn()
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.authError.collect { error ->
            localError = error
        }
    }

    CosmosAmbientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(80.dp))

            // Center content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Brand name
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { it / 2 }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "COSMOS",
                            style = MaterialTheme.typography.displayLarge.copy(
                                letterSpacing = (-1.76).sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = CosmosPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Digital Private Member's Club",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
                            color = CosmosOnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))

                if (showSignIn) {
                    if (forgotPasswordMode) {
                            CosmosGlassCard(showTopGradientBorder = true) {
                                Text(
                                    text = "Reset Password",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CosmosOnBackground,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = "Enter your email address and we'll send you a link to reset your password.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CosmosOnSurfaceVariant
                                )
                                Spacer(Modifier.height(16.dp))
                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("Email Address", color = CosmosOnSurfaceVariant) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CosmosPrimary,
                                        unfocusedBorderColor = CosmosOutlineVariant,
                                        focusedTextColor = CosmosOnBackground,
                                        unfocusedTextColor = CosmosOnBackground
                                    )
                                )
                                if (localError.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(localError, color = CosmosError, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.height(24.dp))
                                if (isLoading) {
                                    CircularProgressIndicator(color = CosmosPrimary, modifier = Modifier.align(Alignment.CenterHorizontally))
                                } else {
                                    CosmosButton(
                                        text = "Send Reset Link",
                                        onClick = {
                                            if (email.isNotBlank()) {
                                                authViewModel.resetPassword(
                                                    email = email,
                                                    onSuccess = {
                                                        android.widget.Toast.makeText(context, "Reset link sent to $email", android.widget.Toast.LENGTH_LONG).show()
                                                        forgotPasswordMode = false
                                                        localError = ""
                                                    },
                                                    onError = { err ->
                                                        localError = err
                                                    }
                                                )
                                            } else {
                                                localError = "Please enter your email address"
                                            }
                                        }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(
                                        onClick = {
                                            forgotPasswordMode = false
                                            localError = ""
                                        },
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    ) {
                                        Text("Cancel", color = CosmosOnSurfaceVariant)
                                    }
                                }
                        }
                    } else {
                        // Sign In Card
                        CosmosGlassCard(showTopGradientBorder = true) {
                            Text(
                                text = "Sign In to Cosmos",
                                style = MaterialTheme.typography.titleMedium,
                                color = CosmosOnBackground,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(16.dp))
                            
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email Address", color = CosmosOnSurfaceVariant) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CosmosPrimary,
                                    unfocusedBorderColor = CosmosOutlineVariant,
                                    focusedTextColor = CosmosOnBackground,
                                    unfocusedTextColor = CosmosOnBackground
                                )
                            )
                            Spacer(Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password", color = CosmosOnSurfaceVariant) },
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = PasswordVisualTransformation(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CosmosPrimary,
                                    unfocusedBorderColor = CosmosOutlineVariant,
                                    focusedTextColor = CosmosOnBackground,
                                    unfocusedTextColor = CosmosOnBackground
                                )
                            )
                            
                            Spacer(Modifier.height(4.dp))
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                TextButton(
                                    onClick = {
                                        forgotPasswordMode = true
                                        localError = ""
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Forgot Password?", style = MaterialTheme.typography.bodySmall, color = CosmosPrimary)
                                }
                            }
                            
                            if (localError.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(localError, color = CosmosError, style = MaterialTheme.typography.bodySmall)
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            if (isLoading) {
                                CircularProgressIndicator(color = CosmosPrimary, modifier = Modifier.align(Alignment.CenterHorizontally))
                            } else {
                                CosmosButton(
                                    text = "Sign In",
                                    onClick = {
                                        if (email.isNotBlank() && password.isNotBlank()) {
                                            authViewModel.signIn(email, password, onSignIn)
                                        } else {
                                            localError = "Please enter email and password"
                                        }
                                    }
                                )
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        showSignIn = false
                                        forgotPasswordMode = false
                                    },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text("Cancel", color = CosmosOnSurfaceVariant)
                                }
                            }
                        }
                    }
                } else {
                    // Glass value proposition card
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(1000, delayMillis = 300)) + slideInVertically(tween(800, 300)) { it / 3 }
                    ) {
                        CosmosGlassCard(showTopGradientBorder = true) {
                            Text(
                                text = "Curated Networking for World-Class Founders and Professionals.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = CosmosOnSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Join an exclusive environment designed for high-value connections and intentional engagement.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CosmosOnSurfaceVariant.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Bottom section
            if (!showSignIn) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(1000, delayMillis = 600))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 40.dp)
                    ) {
                        // Progress dots (onboarding step 1 of 5)
                        CosmosProgressDots(
                            totalSteps = 5,
                            currentStep = 0,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        CosmosButton(
                            text = "Get Started",
                            onClick = onGetStarted,
                            icon = Icons.AutoMirrored.Filled.ArrowForward
                        )

                        Spacer(Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Already a member? ",
                                style = MaterialTheme.typography.labelMedium,
                                color = CosmosOnSurfaceVariant
                            )
                            TextButton(
                                onClick = { showSignIn = true },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text(
                                    text = "Sign In",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CosmosPrimary
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(1.dp))
            }
        }
    }
}
