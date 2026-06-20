package app.cosmos.com.screens.profile

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cosmos.com.data.model.NotificationType
import app.cosmos.com.data.repository.ServiceLocator
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class FaqItem(
    val question: String,
    val answer: String,
    val category: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSupportScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // FAQ Static Data
    val faqs = remember {
        listOf(
            FaqItem(
                "Why is there a limit of 10 new connections per month?",
                "Cosmos prioritizes quality over quantity. By limiting new connections to 10 per month, we ensure that every interaction is intentional, structured, and leads to meaningful relationship building rather than mindless swiping.",
                "Matchmaking & Connections"
            ),
            FaqItem(
                "How does the AI matching logic work?",
                "Our recommendation engine analyzes your industry, role, interest tags, professional goals, and complementary needs (e.g., co-founders matching with investors or mentors matching with students) to prioritize the most relevant profiles for you.",
                "Matchmaking & Connections"
            ),
            FaqItem(
                "How do structured speed networking rounds work?",
                "Events are split into multiple 15-minute rounds. You will be matched with another participant based on common tags or goals. A countdown timer will show, and after the round, you can provide quick feedback and ratings.",
                "Events & Networking"
            ),
            FaqItem(
                "How do paid events and refunds work?",
                "Some premium events require an upfront fee. If you register, attend the scheduled rounds, and receive positive collaboration feedback, a refund may be triggered automatically to reward active participation.",
                "Events & Networking"
            ),
            FaqItem(
                "How are meeting AI summaries generated?",
                "If you record/transcript your meeting inside the platform, our AI models analyze the key discussion points, decisions, and action items, then automatically save a concise summary directly in your conversation history.",
                "AI Summaries & CRM"
            ),
            FaqItem(
                "Who can see my CRM labels and private goals?",
                "Your CRM notes, labels (like 'Follow up needed' or 'Warm intro requested'), and private relationship goals are 100% private to you. The other person cannot see them.",
                "AI Summaries & CRM"
            ),
            FaqItem(
                "How do I pause my account visibility?",
                "Go to Settings -> Control Center -> Privacy, and toggle 'Profile Visibility' off. This stops you from appearing in matchmaking decks while keeping your existing chats and events active.",
                "Account & Security"
            ),
            FaqItem(
                "How can I block or report someone?",
                "Go to the profile of the person you want to block or report, tap the three dots at the top right, and select 'Block' or 'Report'. You can also review your list of blocked users under Control Center.",
                "Account & Security"
            )
        )
    }

    // State variables
    var searchQuery by remember { mutableStateOf("") }
    var expandedFaqItem by remember { mutableStateOf<String?>(null) }

    // Support Form State
    var selectedCategory by remember { mutableStateOf("Technical Issue") }
    var descriptionText by remember { mutableStateOf("") }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var isSubmitted by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val categories = listOf(
        "Technical Issue",
        "Matchmaking & Discovery",
        "Events & Rounds",
        "Billing & Premium Tiers",
        "General Feedback",
        "Other"
    )

    // Filter FAQs based on query
    val filteredFaqs = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            faqs
        } else {
            faqs.filter {
                it.question.contains(searchQuery, ignoreCase = true) ||
                it.answer.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Group FAQs by category if not searching
    val groupedFaqs = remember(filteredFaqs, searchQuery) {
        if (searchQuery.isBlank()) {
            filteredFaqs.groupBy { it.category }
        } else {
            emptyMap()
        }
    }

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(title = "Help & Support", onBack = onBack)

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Header / Intro
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(
                            text = "How can we help you?",
                            style = MaterialTheme.typography.headlineSmall,
                            color = CosmosOnBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Search our knowledge base or submit a support request below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CosmosOnSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )
                    }
                }

                // Search Bar
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 20.dp),
                        placeholder = { Text("Search FAQs...", color = CosmosOnSurfaceVariant) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = CosmosOnSurfaceVariant) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, "Clear", tint = CosmosOnSurfaceVariant)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CosmosPrimary,
                            unfocusedBorderColor = CosmosOutlineVariant,
                            focusedTextColor = CosmosOnBackground,
                            unfocusedTextColor = CosmosOnBackground
                        )
                    )
                }

                // Grouped FAQs (Static / No Search)
                if (searchQuery.isBlank()) {
                    groupedFaqs.forEach { (categoryName, faqList) ->
                        item {
                            CosmosSectionHeader(title = categoryName)
                            Spacer(Modifier.height(8.dp))
                        }
                        items(faqList) { faq ->
                            FaqAccordionItem(
                                faq = faq,
                                isExpanded = expandedFaqItem == faq.question,
                                onToggle = {
                                    expandedFaqItem = if (expandedFaqItem == faq.question) null else faq.question
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                } else {
                    // Search results FAQ list
                    item {
                        CosmosSectionHeader(title = "Search Results (${filteredFaqs.size})")
                        Spacer(Modifier.height(8.dp))
                    }
                    if (filteredFaqs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp, horizontal = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching FAQ articles found.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CosmosOnSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(filteredFaqs) { faq ->
                            FaqAccordionItem(
                                faq = faq,
                                isExpanded = expandedFaqItem == faq.question,
                                onToggle = {
                                    expandedFaqItem = if (expandedFaqItem == faq.question) null else faq.question
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                // Still Need Help? / Support Ticket Section
                item {
                    Spacer(Modifier.height(24.dp))
                    CosmosSectionHeader(title = "Contact Support")
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        CosmosGlassCard {
                            if (isSubmitted) {
                                SupportSuccessView(
                                    onReset = {
                                        isSubmitted = false
                                        descriptionText = ""
                                    }
                                )
                            } else {
                                SupportFormView(
                                    selectedCategory = selectedCategory,
                                    descriptionText = descriptionText,
                                    showCategoryDropdown = showCategoryDropdown,
                                    isSubmitting = isSubmitting,
                                    categories = categories,
                                    onCategoryClick = { showCategoryDropdown = true },
                                    onDismissDropdown = { showCategoryDropdown = false },
                                    onSelectCategory = {
                                        selectedCategory = it
                                        showCategoryDropdown = false
                                    },
                                    onDescriptionChange = { descriptionText = it },
                                    onSubmit = {
                                        if (descriptionText.trim().isEmpty()) {
                                            Toast.makeText(context, "Please describe your query.", Toast.LENGTH_SHORT).show()
                                            return@SupportFormView
                                        }
                                        isSubmitting = true
                                        coroutineScope.launch {
                                            val userId = ServiceLocator.authRepository.currentUserId
                                            if (userId == null) {
                                                Toast.makeText(context, "You must be signed in to submit a request.", Toast.LENGTH_SHORT).show()
                                                isSubmitting = false
                                                return@launch
                                            }
                                            
                                            try {
                                                val ticketData = mapOf(
                                                    "userId" to userId,
                                                    "category" to selectedCategory,
                                                    "description" to descriptionText,
                                                    "status" to "OPEN",
                                                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                                )
                                                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                                    .collection("support_tickets")
                                                    .add(ticketData)
                                                    .await()

                                                ServiceLocator.notificationRepository.createNotification(
                                                    userId = userId,
                                                    type = NotificationType.COMMUNITY_ANNOUNCEMENT,
                                                    title = "Support Request Received 📥",
                                                    body = "We received your request about '$selectedCategory'. A representative will review it soon.",
                                                    actionId = "support_${System.currentTimeMillis()}"
                                                )
                                            } catch (e: Exception) {
                                                // Log the error for debugging
                                                android.util.Log.e("HelpSupport", "Error submitting ticket", e)
                                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            }
                                            isSubmitting = false
                                            isSubmitted = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FaqAccordionItem(
    faq: FaqItem,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "arrowRotation"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CosmosGlass)
            .border(1.dp, CosmosGlassBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = faq.question,
                    style = MaterialTheme.typography.titleMedium,
                    color = CosmosOnBackground,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = CosmosPrimary,
                    modifier = Modifier
                        .rotate(rotationState)
                        .size(24.dp)
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = CosmosOutlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = faq.answer,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CosmosOnSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SupportFormView(
    selectedCategory: String,
    descriptionText: String,
    showCategoryDropdown: Boolean,
    isSubmitting: Boolean,
    categories: List<String>,
    onCategoryClick: () -> Unit,
    onDismissDropdown: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Describe your issue or feedback, and we will get back to you soon.",
            style = MaterialTheme.typography.bodyMedium,
            color = CosmosOnSurfaceVariant
        )

        // Category Selection OutlinedTextField acting as Dropdown Trigger
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedCategory,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Category", color = CosmosOnSurfaceVariant) },
                trailingIcon = {
                    IconButton(onClick = onCategoryClick) {
                        Icon(Icons.Default.ArrowDropDown, "Select Category", tint = CosmosPrimary)
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

            // Transparent layer to make whole text field clickable
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(onClick = onCategoryClick)
            )

            DropdownMenu(
                expanded = showCategoryDropdown,
                onDismissRequest = onDismissDropdown,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(CosmosSurfaceContainerHigh)
                    .border(1.dp, CosmosGlassBorder, RoundedCornerShape(8.dp))
            ) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category, color = CosmosOnBackground) },
                        onClick = { onSelectCategory(category) }
                    )
                }
            }
        }

        // Details Description multiline textfield
        OutlinedTextField(
            value = descriptionText,
            onValueChange = onDescriptionChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            label = { Text("Details / Explanation", color = CosmosOnSurfaceVariant) },
            placeholder = { Text("Explain your issue in detail...", color = CosmosOnSurfaceVariant) },
            maxLines = 6,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CosmosPrimary,
                unfocusedBorderColor = CosmosOutlineVariant,
                focusedTextColor = CosmosOnBackground,
                unfocusedTextColor = CosmosOnBackground
            )
        )

        // Submit Button
        if (isSubmitting) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CosmosPrimary)
            }
        } else {
            CosmosButton(
                text = "Submit Request",
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SupportSuccessView(
    onReset: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Success",
            tint = CosmosSuccess,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Request Submitted!",
            style = MaterialTheme.typography.titleLarge,
            color = CosmosOnBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your support request has been logged successfully. We've sent a confirmation notification, and a member of our team will reach out to you within 24 hours.",
            style = MaterialTheme.typography.bodyMedium,
            color = CosmosOnSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(24.dp))
        CosmosOutlinedButton(
            text = "Submit Another Request",
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
