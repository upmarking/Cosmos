package com.cosmos.app.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cosmos.app.ui.components.*
import com.cosmos.app.ui.theme.*

import androidx.compose.runtime.collectAsState
import com.cosmos.app.data.model.MembershipTier

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DefineIntentScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    authViewModel: com.cosmos.app.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val intents = listOf(
        "Co-founders", "Business Partners", "Mentors", "Collaborators",
        "Investors", "Clients", "Hiring Opportunities", "Strategic Introductions",
        "Industry Peers", "Community Support"
    )

    val interestTags = listOf(
        "Startup", "AI", "Marketing", "Product", "Design", "Finance",
        "Legal", "Healthcare", "Education", "Mobility", "Consumer", "SaaS",
        "Web3", "Content", "Sales", "Growth", "Operations", "HR",
        "Deep Tech", "Climate", "Biotech", "PropTech", "EdTech"
    )

    val selectedIntents = remember { mutableStateListOf<String>() }
    val selectedTags = remember { mutableStateListOf<String>() }

    val currentUser by authViewModel.currentUser.collectAsState()
    val isLoading by authViewModel.isLoading.collectAsState()

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(title = "Define Your Intent", onBack = onBack)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                CosmosProgressDots(totalSteps = 5, currentStep = 2, modifier = Modifier.padding(vertical = 16.dp))

                Text("What are you looking for?", style = MaterialTheme.typography.headlineMedium, color = CosmosOnBackground)
                Text(
                    "Select all that apply. This helps us match you with the right people.",
                    style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                // Intent section
                Text("Looking for...", style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, modifier = Modifier.padding(bottom = 12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)
                ) {
                    intents.forEach { intent ->
                        val isSelected = selectedIntents.contains(intent)
                        CosmosTagChip(
                            text = intent,
                            backgroundColor = if (isSelected) CosmosPrimary.copy(alpha = 0.2f) else CosmosSurfaceContainerHigh,
                            textColor = if (isSelected) CosmosPrimary else CosmosOnSurfaceVariant,
                            onClick = {
                                if (isSelected) selectedIntents.remove(intent)
                                else selectedIntents.add(intent)
                            }
                        )
                    }
                }

                // Interest tags
                Text("My interests...", style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, modifier = Modifier.padding(bottom = 12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    interestTags.forEach { tag ->
                        val isSelected = selectedTags.contains(tag)
                        CosmosTagChip(
                            text = "#$tag",
                            backgroundColor = if (isSelected) CosmosSecondaryContainer.copy(alpha = 0.3f) else CosmosSurfaceContainerHigh,
                            textColor = if (isSelected) CosmosSecondary else CosmosOnSurfaceVariant,
                            onClick = {
                                if (isSelected) selectedTags.remove(tag)
                                else selectedTags.add(tag)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(100.dp))
            }

            Box(modifier = Modifier.fillMaxWidth().background(CosmosBackground).navigationBarsPadding().padding(20.dp)) {
                if (isLoading) {
                    CircularProgressIndicator(color = CosmosPrimary, modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
                } else {
                    CosmosButton(
                        text = "Continue",
                        onClick = {
                            currentUser?.let { member ->
                                val updated = member.copy(
                                    tags = selectedTags.toList()
                                )
                                authViewModel.saveOnboarding(member = updated, onSuccess = onNext)
                            } ?: onNext()
                        },
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        enabled = selectedIntents.isNotEmpty()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YourVisionScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    authViewModel: com.cosmos.app.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var goalStatement by remember { mutableStateOf("") }
    var longTermVision by remember { mutableStateOf("") }
    var preferredFormat by remember { mutableStateOf("") }

    val formats = listOf("Video Call", "In Person", "Coffee Chat", "Virtual Event", "Message First")

    val currentUser by authViewModel.currentUser.collectAsState()
    val isLoading by authViewModel.isLoading.collectAsState()

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(title = "Your Vision", onBack = onBack)

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
            ) {
                CosmosProgressDots(totalSteps = 5, currentStep = 3, modifier = Modifier.padding(vertical = 16.dp))

                Text("Share your vision", style = MaterialTheme.typography.headlineMedium, color = CosmosOnBackground)
                Text(
                    "Help us understand what drives you and who you want to meet.",
                    style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                // Goal statement
                Column(modifier = Modifier.padding(bottom = 20.dp)) {
                    Text("Goal statement", style = MaterialTheme.typography.labelMedium, color = CosmosOnSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                    OutlinedTextField(
                        value = goalStatement,
                        onValueChange = { goalStatement = it },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        placeholder = { Text("What are you looking to achieve on Cosmos?", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CosmosPrimary,
                            unfocusedBorderColor = CosmosOutlineVariant,
                            focusedTextColor = CosmosOnBackground,
                            unfocusedTextColor = CosmosOnBackground,
                            cursorColor = CosmosPrimary,
                            focusedContainerColor = CosmosSurfaceContainerLow,
                            unfocusedContainerColor = CosmosSurfaceContainerLow
                        ),
                        maxLines = 4
                    )
                }

                // Long-term vision
                Column(modifier = Modifier.padding(bottom = 20.dp)) {
                    Text("Long-term vision", style = MaterialTheme.typography.labelMedium, color = CosmosOnSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                    OutlinedTextField(
                        value = longTermVision,
                        onValueChange = { longTermVision = it },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        placeholder = { Text("What are you building, why are you here, and what kind of people do you want to meet?", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CosmosPrimary,
                            unfocusedBorderColor = CosmosOutlineVariant,
                            focusedTextColor = CosmosOnBackground,
                            unfocusedTextColor = CosmosOnBackground,
                            cursorColor = CosmosPrimary,
                            focusedContainerColor = CosmosSurfaceContainerLow,
                            unfocusedContainerColor = CosmosSurfaceContainerLow
                        ),
                        maxLines = 6
                    )
                }

                // Preferred meeting format
                Text("Preferred meeting format", style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground, modifier = Modifier.padding(bottom = 12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    formats.forEach { fmt ->
                        CosmosTagChip(
                            text = fmt,
                            backgroundColor = if (preferredFormat == fmt) CosmosPrimary.copy(alpha = 0.2f) else CosmosSurfaceContainerHigh,
                            textColor = if (preferredFormat == fmt) CosmosPrimary else CosmosOnSurfaceVariant,
                            onClick = { preferredFormat = fmt }
                        )
                    }
                }

                Spacer(Modifier.height(100.dp))
            }

            Box(modifier = Modifier.fillMaxWidth().background(CosmosBackground).navigationBarsPadding().padding(20.dp)) {
                if (isLoading) {
                    CircularProgressIndicator(color = CosmosPrimary, modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
                } else {
                    CosmosButton(
                        text = "Continue",
                        onClick = {
                            currentUser?.let { member ->
                                val updated = member.copy(
                                    goalStatement = goalStatement,
                                    longTermVision = longTermVision
                                )
                                authViewModel.saveOnboarding(member = updated, onSuccess = onNext)
                            } ?: onNext()
                        },
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        enabled = goalStatement.isNotBlank()
                    )
                }
            }
        }
    }
}

@Composable
fun AiMatchingRefinementScreen(
    onFinish: () -> Unit,
    onBack: () -> Unit,
    authViewModel: com.cosmos.app.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val attributes = listOf(
        "Technical Co-founder" to false,
        "Business Co-founder" to false,
        "Angel Investor" to true,
        "Series A VC" to false,
        "Product Advisor" to true,
        "Go-to-Market Expert" to false,
        "Design Partner" to false,
        "Enterprise Sales" to true,
        "Domain Expert (AI)" to false,
        "Board Member" to false
    )
    val selections = remember { mutableStateMapOf<String, Boolean>().also { map -> attributes.forEach { (k, v) -> map[k] = v } } }

    val currentUser by authViewModel.currentUser.collectAsState()
    val isLoading by authViewModel.isLoading.collectAsState()

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(title = "AI Matching Refinement", onBack = onBack)

            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                CosmosProgressDots(totalSteps = 5, currentStep = 4, modifier = Modifier.padding(vertical = 16.dp))

                Text("AI-Powered Matching", style = MaterialTheme.typography.headlineMedium, color = CosmosOnBackground)
                Text(
                    "Our AI has pre-selected the most relevant connection types based on your profile. Fine-tune to perfection.",
                    style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                CosmosGlassCard {
                    Text("AI Recommendations", style = MaterialTheme.typography.titleSmall, color = CosmosPrimary, modifier = Modifier.padding(bottom = 16.dp))
                    selections.forEach { (attribute, isSelected) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(attribute, style = MaterialTheme.typography.bodyMedium, color = CosmosOnBackground, modifier = Modifier.weight(1f))
                            Switch(
                                checked = isSelected,
                                onCheckedChange = { selections[attribute] = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CosmosBackground,
                                    checkedTrackColor = CosmosPrimary,
                                    uncheckedThumbColor = CosmosOnSurfaceVariant,
                                    uncheckedTrackColor = CosmosSurfaceContainerHigh
                                )
                            )
                        }
                        if (attribute != attributes.last().first) {
                            Divider(color = CosmosOutlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Match quality notice
                CosmosGlassCard(showTopGradientBorder = false) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                        Text("✨", style = MaterialTheme.typography.titleLarge)
                        Column {
                            Text("Quality over quantity", style = MaterialTheme.typography.titleSmall, color = CosmosOnBackground)
                            Text(
                                "You'll receive up to 10 curated matches per month. Each one is handpicked by our AI for maximum relevance.",
                                style = MaterialTheme.typography.bodySmall, color = CosmosOnSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(100.dp))
            }

            Box(modifier = Modifier.fillMaxWidth().background(CosmosBackground).navigationBarsPadding().padding(20.dp)) {
                if (isLoading) {
                    CircularProgressIndicator(color = CosmosPrimary, modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
                } else {
                    CosmosButton(
                        text = "Start Discovering →",
                        onClick = {
                            currentUser?.let { member ->
                                val updated = member.copy(
                                    membershipTier = MembershipTier.MEMBER,
                                    isProfileComplete = true
                                )
                                authViewModel.saveOnboarding(member = updated, onSuccess = onFinish)
                            } ?: onFinish()
                        }
                    )
                }
            }
        }
    }
}

// Needed for multi-select set
@Composable
fun <T> mutableStateSetOf(vararg elements: T): MutableSet<T> = remember { mutableSetOf(*elements) }
