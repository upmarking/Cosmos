package com.cosmos.app.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cosmos.app.ui.components.*
import com.cosmos.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteIdentityScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    authViewModel: com.cosmos.app.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var headline by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var yearsExperience by remember { mutableStateOf("") }
    var selectedUserType by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf("") }

    val isLoading by authViewModel.isLoading.collectAsState()

    val userTypes = listOf(
        "Founder", "Co-Founder", "Startup Operator", "Investor", "Student",
        "Mentor", "Tech Professional", "Marketing Professional", "Finance Professional",
        "Legal Professional", "Healthcare Professional", "Business Professional",
        "Creator", "Freelancer", "Service Provider", "Community Member"
    )

    LaunchedEffect(Unit) {
        authViewModel.authError.collect { error ->
            localError = error
        }
    }

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(title = "Complete Your Identity", onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // Progress
                CosmosProgressDots(totalSteps = 5, currentStep = 1, modifier = Modifier.padding(vertical = 16.dp))

                Text(
                    "Tell us who you are",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CosmosOnBackground
                )
                Text(
                    "Build a credible, professional profile",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CosmosOnSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                // Profile photo placeholder
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(CosmosSurfaceContainerHigh)
                            .border(2.dp, CosmosOutlineVariant, CircleShape)
                            .clickable { },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AddAPhoto,
                                contentDescription = "Add photo",
                                tint = CosmosOnSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                "Photo",
                                style = MaterialTheme.typography.labelSmall,
                                color = CosmosOnSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // User type selector
                Text(
                    "I am a...",
                    style = MaterialTheme.typography.titleSmall,
                    color = CosmosOnBackground,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    userTypes.forEach { type ->
                        CosmosTagChip(
                            text = type,
                            backgroundColor = if (selectedUserType == type) CosmosPrimary.copy(alpha = 0.2f)
                                             else CosmosSurfaceContainerHigh,
                            textColor = if (selectedUserType == type) CosmosPrimary else CosmosOnSurfaceVariant,
                            onClick = { selectedUserType = type }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Input fields
                CosmosTextField(label = "Full Name *", value = name, onValueChange = { name = it }, placeholder = "Alexandra Chen")
                CosmosTextField(label = "Email Address *", value = email, onValueChange = { email = it }, placeholder = "alex@nexusai.com")
                
                Text(
                    "Password *",
                    style = MaterialTheme.typography.labelMedium,
                    color = CosmosOnSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    placeholder = { Text("Password", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = androidx.compose.ui.text.input.PasswordTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CosmosPrimary,
                        unfocusedBorderColor = CosmosOutlineVariant,
                        focusedTextColor = CosmosOnBackground,
                        unfocusedTextColor = CosmosOnBackground
                    ),
                    singleLine = true
                )

                CosmosTextField(label = "Professional Headline", value = headline, onValueChange = { headline = it }, placeholder = "Founder & CEO at NexusAI")
                CosmosTextField(label = "Current Role", value = role, onValueChange = { role = it }, placeholder = "CEO")
                CosmosTextField(label = "Company", value = company, onValueChange = { company = it }, placeholder = "NexusAI")
                CosmosTextField(label = "Location", value = location, onValueChange = { location = it }, placeholder = "San Francisco, CA")
                CosmosTextField(label = "Years of Experience", value = yearsExperience, onValueChange = { yearsExperience = it }, placeholder = "8")

                // LinkedIn connect card
                Spacer(Modifier.height(8.dp))
                CosmosGlassCard(showTopGradientBorder = false) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(CosmosLinkedIn),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("in", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Connect LinkedIn", style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground)
                            Text("Import your profile & build trust", style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant)
                        }
                        CosmosOutlinedButton(text = "Connect", onClick = {}, modifier = Modifier.wrapContentWidth())
                    }
                }
                
                if (localError.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(localError, color = CosmosError, style = MaterialTheme.typography.bodyMedium)
                }
                
                Spacer(Modifier.height(100.dp))
            }

            // Bottom CTA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmosBackground)
                    .navigationBarsPadding()
                    .padding(20.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = CosmosPrimary, modifier = Modifier.align(Alignment.Center))
                } else {
                    CosmosButton(
                        text = "Continue",
                        onClick = {
                            if (name.isNotBlank() && email.isNotBlank() && password.isNotBlank() && selectedUserType.isNotBlank()) {
                                authViewModel.signUp(email, password, name, selectedUserType) {
                                    // Successfully registered! Let's save profile details.
                                    val memberData = Member(
                                        id = "", // populated inside repository using uid
                                        name = name,
                                        headline = headline.ifBlank { "$selectedUserType at $company" },
                                        role = role,
                                        company = company,
                                        avatarUrl = "",
                                        location = location,
                                        membershipTier = MembershipTier.EXPLORER
                                    )
                                    authViewModel.saveOnboarding(memberData, onNext)
                                }
                            } else {
                                localError = "Please fill in all required (*) fields"
                            }
                        },
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        enabled = name.isNotBlank() && email.isNotBlank() && password.isNotBlank() && selectedUserType.isNotBlank()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CosmosTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(bottom = 16.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = CosmosOnSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CosmosPrimary,
                unfocusedBorderColor = CosmosOutlineVariant,
                focusedTextColor = CosmosOnBackground,
                unfocusedTextColor = CosmosOnBackground,
                cursorColor = CosmosPrimary,
                focusedContainerColor = CosmosSurfaceContainerLow,
                unfocusedContainerColor = CosmosSurfaceContainerLow
            ),
            singleLine = true
        )
    }
}
