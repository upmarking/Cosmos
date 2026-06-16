package com.cosmos.app.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import com.cosmos.app.data.ValidationUtils
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cosmos.app.ui.components.*
import com.cosmos.app.ui.theme.*
import kotlinx.coroutines.delay

data class DepartmentSlide(
    val title: String,
    val headline: String,
    val description: String,
    val icon: String
)

val departmentSlides = listOf(
    DepartmentSlide(
        title = "CONNECT",
        headline = "Curated Matchmaking & Swipe Discovery",
        description = "Discover world-class founders and professionals based on shared interest tags, intent, and mutual relevance.",
        icon = "🤝"
    ),
    DepartmentSlide(
        title = "EVENTS",
        headline = "Structured Networking & Curated Rounds",
        description = "Register for invite-only events with live scheduled rounds, matching preferences, and feedback loops.",
        icon = "📅"
    ),
    DepartmentSlide(
        title = "COMMUNITIES",
        headline = "Curated Manager-Led Groups",
        description = "Engage in highly focused, moderated circles built for strategic learning and industry collaboration.",
        icon = "👥"
    ),
    DepartmentSlide(
        title = "CONVERSATIONS",
        headline = "CRM Chat & AI Meeting Summaries",
        description = "Track relationship milestones, add private goals and notes, and view AI-powered meeting summaries.",
        icon = "💬"
    )
)

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

    val pagerState = rememberPagerState(pageCount = { departmentSlides.size })

    // Auto-sliding loop for the department slides
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            if (!pagerState.isScrollInProgress) {
                val nextPage = (pagerState.currentPage + 1) % departmentSlides.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }
    var password by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf("") }
    var forgotPasswordMode by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(showSignIn, forgotPasswordMode) {
        passwordVisible = false
    }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val isLoading by authViewModel.isLoading.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    LaunchedEffect(Unit) {
        delay(200)
        visible = true
    }

    LaunchedEffect(currentUser) {
        val user = currentUser
        if (user != null) {
            if (user.isProfileComplete) {
                onSignIn()
            } else {
                onGetStarted()
            }
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
                                    onValueChange = { 
                                        email = it
                                        localError = ""
                                    },
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
                                                        android.widget.Toast.makeText(context, "Password reset link sent to $email. Please check your inbox.", android.widget.Toast.LENGTH_LONG).show()
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
                                onValueChange = { 
                                    email = it
                                    localError = ""
                                },
                                label = { Text("Email Address", color = CosmosOnSurfaceVariant) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }
                                ),
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
                                onValueChange = { 
                                    password = it
                                    localError = ""
                                },
                                label = { Text("Password", color = CosmosOnSurfaceVariant) },
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                    val description = if (passwordVisible) "Hide password" else "Show password"
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(imageVector = image, contentDescription = description, tint = CosmosOnSurfaceVariant)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        localError = ""
                                        val emailVal = ValidationUtils.validateEmail(email)
                                        if (!emailVal.isValid) {
                                            localError = emailVal.errorMessage ?: "Invalid email"
                                        } else if (password.isBlank()) {
                                            localError = "Password is required"
                                        } else {
                                            authViewModel.signIn(email, password, onSignIn)
                                        }
                                    }
                                ),
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
                                        localError = ""
                                        val emailVal = ValidationUtils.validateEmail(email)
                                        if (!emailVal.isValid) {
                                            localError = emailVal.errorMessage ?: "Invalid email"
                                        } else if (password.isBlank()) {
                                            localError = "Password is required"
                                        } else {
                                            authViewModel.signIn(email, password, onSignIn)
                                        }
                                    }
                                )
                                Spacer(Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        showSignIn = false
                                        forgotPasswordMode = false
                                        localError = ""
                                    },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text("Cancel", color = CosmosOnSurfaceVariant)
                                }
                            }
                        }
                    }
                } else {
                    // Sliding glass value proposition cards (Departments)
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(1000, delayMillis = 300)) + slideInVertically(tween(800, 300)) { it / 3 }
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                        ) { page ->
                            val slide = departmentSlides[page]
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(CosmosGlass)
                                        .border(
                                            width = 1.dp,
                                            color = CosmosGlassBorder,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = slide.icon,
                                            fontSize = 24.sp
                                        )
                                        Text(
                                            text = slide.title,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            ),
                                            color = CosmosPrimary
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = slide.headline,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = CosmosOnSurface,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = slide.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CosmosOnSurfaceVariant.copy(alpha = 0.8f),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                // Top gradient shimmer line
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    CosmosPrimary.copy(alpha = 0.5f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                            }
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
                        // Sliding progress dots for the 4 departments
                        CosmosProgressDots(
                            totalSteps = departmentSlides.size,
                            currentStep = pagerState.currentPage,
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
                                onClick = { 
                                    showSignIn = true
                                    localError = ""
                                },
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
