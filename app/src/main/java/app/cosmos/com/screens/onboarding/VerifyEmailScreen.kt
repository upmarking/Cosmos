package app.cosmos.com.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun VerifyEmailScreen(
    onVerified: () -> Unit,
    onSignOut: () -> Unit,
    authViewModel: app.cosmos.com.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val isLoading by authViewModel.isLoading.collectAsState()
    var localError by remember { mutableStateOf("") }
    val currentUserState by authViewModel.currentUser.collectAsState()
    val email = currentUserState?.email ?: "your email"

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
            Spacer(Modifier.height(40.dp))

            // Center card
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "COSMOS",
                    style = MaterialTheme.typography.displaySmall.copy(
                        letterSpacing = (-1.2).sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = CosmosPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))

                CosmosGlassCard(showTopGradientBorder = true) {
                    Text(
                        text = "Verify Your Email",
                        style = MaterialTheme.typography.titleLarge,
                        color = CosmosOnBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "We sent a verification link to:",
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosOnSurfaceVariant
                    )
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = CosmosPrimary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Text(
                        text = "Please check your inbox and click the link to activate your account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosOnSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                    )

                    Text(
                        text = "✉️",
                        fontSize = 54.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp)
                    )

                    if (localError.isNotEmpty()) {
                        Text(
                            text = localError,
                            color = CosmosError,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    if (isLoading) {
                        CircularProgressIndicator(color = CosmosPrimary, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        CosmosButton(
                            text = "I have verified my email",
                            onClick = {
                                localError = ""
                                authViewModel.reloadUser(
                                    onResult = { isVerified ->
                                        if (isVerified) {
                                            android.widget.Toast.makeText(context, "Email verified! Welcome to Cosmos.", android.widget.Toast.LENGTH_SHORT).show()
                                            onVerified()
                                        } else {
                                            localError = "Email is still not verified. Please click the link in your email and try again."
                                        }
                                    },
                                    onError = { err ->
                                        localError = err
                                    }
                                )
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        CosmosOutlinedButton(
                            text = "Resend verification link",
                            onClick = {
                                localError = ""
                                authViewModel.resendVerificationEmail(
                                    onSuccess = {
                                        android.widget.Toast.makeText(context, "Verification email resent!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { err ->
                                        localError = err
                                    }
                                )
                            }
                        )
                        Spacer(Modifier.height(16.dp))
                        Divider(color = CosmosOutlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                authViewModel.signOut {
                                    onSignOut()
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("Cancel & Sign Out", color = CosmosOnSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
