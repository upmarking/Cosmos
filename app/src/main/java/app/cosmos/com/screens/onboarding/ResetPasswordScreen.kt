package app.cosmos.com.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cosmos.com.data.ValidationUtils
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*
import app.cosmos.com.ui.viewmodel.AuthViewModel

@Composable
fun ResetPasswordScreen(
    oobCode: String?,
    onResetSuccess: () -> Unit,
    onBackToLogin: () -> Unit,
    authViewModel: AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var isVerifying by remember { mutableStateOf(true) }
    var verificationError by remember { mutableStateOf("") }
    var verifiedEmail by remember { mutableStateOf("") }

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf("") }

    val context = LocalContext.current
    val isLoading by authViewModel.isLoading.collectAsState()

    // ── Verify Code on Entry ──────────────────────────────────────────────────
    LaunchedEffect(oobCode) {
        if (oobCode.isNullOrBlank()) {
            verificationError = "The security token is missing or malformed."
            isVerifying = false
        } else {
            authViewModel.verifyResetCode(
                oobCode = oobCode,
                onSuccess = { email ->
                    verifiedEmail = email
                    isVerifying = false
                },
                onError = { err ->
                    verificationError = err
                    isVerifying = false
                }
            )
        }
    }

    CosmosAmbientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Logo
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
                text = "Password Recovery",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = CosmosOnSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            CosmosGlassCard(showTopGradientBorder = true) {
                if (isVerifying) {
                    // Verification Loading State
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = CosmosPrimary)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Verifying security link...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CosmosOnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (verificationError.isNotEmpty()) {
                    // Verification Failed State
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Invalid or Expired Link",
                            style = MaterialTheme.typography.titleMedium,
                            color = CosmosError,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "This password reset link is invalid, expired, or has already been used.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CosmosOnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        CosmosButton(
                            text = "Back to Sign In",
                            onClick = onBackToLogin
                        )
                    }
                } else {
                    // Password Reset Form
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Reset Your Password",
                            style = MaterialTheme.typography.titleMedium,
                            color = CosmosOnBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Please enter a strong new password to secure your account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CosmosOnSurfaceVariant
                        )
                        Spacer(Modifier.height(20.dp))

                        // Verified Email (Read-Only)
                        OutlinedTextField(
                            value = verifiedEmail,
                            onValueChange = {},
                            enabled = false,
                            label = { Text("Account Email", color = CosmosOnSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = CosmosOutlineVariant.copy(alpha = 0.5f),
                                disabledTextColor = CosmosOnSurfaceVariant.copy(alpha = 0.8f),
                                disabledLabelColor = CosmosOnSurfaceVariant
                            )
                        )
                        Spacer(Modifier.height(16.dp))

                        // New Password Input
                        OutlinedTextField(
                            value = password,
                            onValueChange = { 
                                password = it
                                localError = ""
                            },
                            label = { Text("New Password", color = CosmosOnSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, contentDescription = "Toggle password visibility", tint = CosmosOnSurfaceVariant)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CosmosPrimary,
                                unfocusedBorderColor = CosmosOutlineVariant,
                                focusedTextColor = CosmosOnBackground,
                                unfocusedTextColor = CosmosOnBackground
                            )
                        )
                        Spacer(Modifier.height(16.dp))

                        // Confirm Password Input
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { 
                                confirmPassword = it
                                localError = ""
                            },
                            label = { Text("Confirm New Password", color = CosmosOnSurfaceVariant) },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val image = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(imageVector = image, contentDescription = "Toggle password visibility", tint = CosmosOnSurfaceVariant)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CosmosPrimary,
                                unfocusedBorderColor = CosmosOutlineVariant,
                                focusedTextColor = CosmosOnBackground,
                                unfocusedTextColor = CosmosOnBackground
                            )
                        )

                        if (localError.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = localError,
                                color = CosmosError,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        if (isLoading) {
                            CircularProgressIndicator(
                                color = CosmosPrimary,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        } else {
                            CosmosButton(
                                text = "Save Password",
                                onClick = {
                                    localError = ""
                                    val passwordVal = ValidationUtils.validatePassword(password)
                                    val matchVal = ValidationUtils.validatePasswordMatch(password, confirmPassword)

                                    if (!passwordVal.isValid) {
                                        localError = passwordVal.errorMessage ?: "Invalid password"
                                    } else if (!matchVal.isValid) {
                                        localError = matchVal.errorMessage ?: "Passwords do not match"
                                    } else {
                                        authViewModel.confirmPasswordReset(
                                            oobCode = oobCode ?: "",
                                            newPassword = password,
                                            onSuccess = {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Password updated successfully. Please sign in.",
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                                onResetSuccess()
                                            },
                                            onError = { err ->
                                                localError = err
                                            }
                                        )
                                    }
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = onBackToLogin,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Cancel", color = CosmosOnSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
