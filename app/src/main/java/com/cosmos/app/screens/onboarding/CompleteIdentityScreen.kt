package com.cosmos.app.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import com.cosmos.app.data.ValidationUtils
import com.cosmos.app.data.ValidationResult
import androidx.compose.ui.unit.dp
import com.cosmos.app.data.model.Member
import com.cosmos.app.data.model.MembershipTier
import com.cosmos.app.ui.components.*
import com.cosmos.app.ui.theme.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CompleteIdentityScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSignInInstead: () -> Unit = {},
    authViewModel: com.cosmos.app.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isDuplicateEmailError by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var headline by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var yearsExperience by remember { mutableStateOf("") }
    var selectedUserType by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    // Track whether the user has already been registered (handles back-nav from onboarding)
    var alreadySignedUp by remember { mutableStateOf(false) }
    var lastSignedUpEmail by remember { mutableStateOf("") }
    var lastSignedUpPassword by remember { mutableStateOf("") }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showPhotoOptions by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            selectedImageBitmap = null
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            selectedImageBitmap = bitmap
            selectedImageUri = null
        }
    }

    var isLinkedInConnected by remember { mutableStateOf(false) }
    var isConnectingLinkedIn by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val isLoading by authViewModel.isLoading.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    // If user comes back to this screen while already logged in, mark as already signed up and pre-fill form
    LaunchedEffect(currentUser) {
        val user = currentUser
        if (user != null) {
            alreadySignedUp = true
            if (name.isBlank() && user.name.isNotBlank()) {
                name = user.name
            }
            if (email.isBlank() && user.email.isNotBlank()) {
                email = user.email
            }
            if (headline.isBlank() && user.headline.isNotBlank()) {
                headline = user.headline
            }
            if (role.isBlank() && user.role.isNotBlank()) {
                role = user.role
            }
            if (company.isBlank() && user.company.isNotBlank()) {
                company = user.company
            }
            if (location.isBlank() && user.location.isNotBlank()) {
                location = user.location
            }
            if (selectedUserType.isBlank() && user.primaryUserType.isNotBlank()) {
                selectedUserType = user.primaryUserType
            }
            if (lastSignedUpEmail.isBlank() && user.email.isNotBlank()) {
                lastSignedUpEmail = user.email
            }
            val cachedPassword = com.cosmos.app.data.repository.LocalStore.userPasswords[user.email.trim().lowercase()]
            if (password.isBlank() && cachedPassword != null) {
                password = cachedPassword
                lastSignedUpPassword = cachedPassword
            }
        }
    }

    val userTypes = listOf(
        "Founder", "Co-Founder", "Startup Operator", "Investor", "Student",
        "Mentor", "Tech Professional", "Marketing Professional", "Finance Professional",
        "Legal Professional", "Healthcare Professional", "Business Professional",
        "Creator", "Freelancer", "Service Provider", "Community Member"
    )

    LaunchedEffect(Unit) {
        authViewModel.authError.collect { error ->
            localError = error
            // Detect duplicate-email errors and set a special flag for better UX
            isDuplicateEmailError = error.contains("already exists", ignoreCase = true) ||
                error.contains("email address is already in use", ignoreCase = true)
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
                    Box(contentAlignment = Alignment.BottomEnd) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(CosmosSurfaceContainerHigh)
                                .border(2.dp, CosmosOutlineVariant, CircleShape)
                                .clickable { showPhotoOptions = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedImageUri != null || selectedImageBitmap != null) {
                                AsyncImage(
                                    model = selectedImageUri ?: selectedImageBitmap,
                                    contentDescription = "Selected photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
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
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(CosmosPrimary)
                                .border(2.dp, CosmosBackground, CircleShape)
                                .clickable { showPhotoOptions = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit photo", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                if (showPhotoOptions) {
                    AlertDialog(
                        onDismissRequest = { showPhotoOptions = false },
                        title = { Text("Select Profile Photo", color = CosmosOnBackground) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Choose a photo from your gallery or take a new one.", color = CosmosOnSurfaceVariant)
                                if (selectedImageUri != null || selectedImageBitmap != null) {
                                    TextButton(onClick = {
                                        showPhotoOptions = false
                                        selectedImageUri = null
                                        selectedImageBitmap = null
                                    }) {
                                        Text("Remove Current Photo", color = CosmosError)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showPhotoOptions = false
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Text("Gallery", color = CosmosPrimary)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showPhotoOptions = false
                                    cameraLauncher.launch(null)
                                }
                            ) {
                                Text("Camera", color = CosmosPrimary)
                            }
                        },
                        containerColor = CosmosSurfaceContainerHigh
                    )
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
                            onClick = {
                                val trimmedHeadline = headline.trim()
                                val isDefaultHeadline = trimmedHeadline.isBlank() || userTypes.any { t ->
                                    val tLower = t.lowercase()
                                    trimmedHeadline.lowercase() == tLower || trimmedHeadline.lowercase().startsWith("$tLower at")
                                }
                                if (isDefaultHeadline) {
                                    val matchingType = userTypes.find { t ->
                                        val tLower = t.lowercase()
                                        trimmedHeadline.lowercase() == tLower || trimmedHeadline.lowercase().startsWith("$tLower at")
                                    }
                                    val companySuffix = if (matchingType != null && trimmedHeadline.lowercase().startsWith("${matchingType.lowercase()} at")) {
                                        trimmedHeadline.substring(matchingType.length + 4).trim()
                                    } else {
                                        company
                                    }
                                    headline = if (companySuffix.isBlank()) type else "$type at $companySuffix"
                                }
                                selectedUserType = type
                                localError = ""
                            }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Input fields
                CosmosTextField(
                    label = "Full Name *",
                    value = name,
                    onValueChange = { name = it; localError = "" },
                    placeholder = "Alexandra Chen",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )
                CosmosTextField(
                    label = "Email Address *",
                    value = email,
                    onValueChange = {
                        email = it
                        localError = ""
                        isDuplicateEmailError = false
                    },
                    placeholder = "alex@nexusai.com",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )
                
                Text(
                    "Password *",
                    style = MaterialTheme.typography.labelMedium,
                    color = CosmosOnSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { 
                        password = it
                        localError = ""
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    placeholder = { Text("Password", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
                    shape = RoundedCornerShape(12.dp),
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
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CosmosPrimary,
                        unfocusedBorderColor = CosmosOutlineVariant,
                        focusedTextColor = CosmosOnBackground,
                        unfocusedTextColor = CosmosOnBackground
                    ),
                    singleLine = true
                )

                CosmosTextField(
                    label = "Professional Headline",
                    value = headline,
                    onValueChange = { headline = it },
                    placeholder = "Founder & CEO at NexusAI",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )
                CosmosTextField(
                    label = "Current Role",
                    value = role,
                    onValueChange = { role = it },
                    placeholder = "CEO",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )
                CosmosTextField(
                    label = "Company",
                    value = company,
                    onValueChange = { newCompany ->
                        val trimmedHeadline = headline.trim()
                        val isDefaultHeadline = trimmedHeadline.isBlank() || userTypes.any { t ->
                            val tLower = t.lowercase()
                            trimmedHeadline.lowercase() == tLower || trimmedHeadline.lowercase().startsWith("$tLower at")
                        }
                        if (isDefaultHeadline) {
                            headline = if (newCompany.isBlank()) selectedUserType else "$selectedUserType at $newCompany"
                        }
                        company = newCompany
                    },
                    placeholder = "NexusAI",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )
                CosmosTextField(
                    label = "Location",
                    value = location,
                    onValueChange = { location = it },
                    placeholder = "San Francisco, CA",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )
                CosmosTextField(
                    label = "Years of Experience",
                    value = yearsExperience,
                    onValueChange = { yearsExperience = it },
                    placeholder = "8",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    )
                )

                // LinkedIn connect card
                Spacer(Modifier.height(8.dp))
                CosmosGlassCard(showTopGradientBorder = false) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isLinkedInConnected) CosmosSuccess.copy(alpha = 0.2f) else CosmosLinkedIn),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLinkedInConnected) {
                                Icon(Icons.Default.Check, contentDescription = "Connected", tint = CosmosSuccess, modifier = Modifier.size(20.dp))
                            } else {
                                Text("in", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isLinkedInConnected) "LinkedIn Connected" else "Connect LinkedIn",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isLinkedInConnected) CosmosSuccess else CosmosOnBackground
                            )
                            Text(
                                if (isLinkedInConnected) "Alexandra Chen linked" else "Import your profile & build trust",
                                style = MaterialTheme.typography.bodySmall,
                                color = CosmosOnSurfaceVariant
                            )
                        }
                        CosmosOutlinedButton(
                            text = if (isLinkedInConnected) "Disconnect" else "Connect",
                            onClick = {
                                if (isLinkedInConnected) {
                                    isLinkedInConnected = false
                                } else {
                                    isConnectingLinkedIn = true
                                    coroutineScope.launch {
                                        delay(1500)
                                        isConnectingLinkedIn = false
                                        isLinkedInConnected = true
                                        // Fill inputs if they are currently blank
                                        if (name.isBlank()) name = "Alexandra Chen"
                                        if (headline.isBlank()) headline = "Founder & CEO at NexusAI"
                                        if (role.isBlank()) role = "CEO"
                                        if (company.isBlank()) company = "NexusAI"
                                        if (location.isBlank()) location = "San Francisco, CA"
                                        if (yearsExperience.isBlank()) yearsExperience = "8"
                                    }
                                }
                            },
                            modifier = Modifier.wrapContentWidth()
                        )
                    }
                }

                // LinkedIn Connection simulated OAuth loader dialog
                if (isConnectingLinkedIn) {
                    AlertDialog(
                        onDismissRequest = { isConnectingLinkedIn = false },
                        confirmButton = {},
                        dismissButton = {},
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(CosmosLinkedIn),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("in", color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Text("LinkedIn Verification", color = CosmosOnBackground, style = MaterialTheme.typography.titleMedium)
                            }
                        },
                        text = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                            ) {
                                CircularProgressIndicator(color = CosmosLinkedIn)
                                Spacer(Modifier.height(16.dp))
                                Text("Connecting and importing profile data...", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                            }
                        },
                        containerColor = CosmosSurfaceContainerHigh,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
                
                if (localError.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(localError, color = CosmosError, style = MaterialTheme.typography.bodyMedium)
                    if (isDuplicateEmailError) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(
                            onClick = onSignInInstead,
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Text(
                                "Sign In Instead →",
                                color = CosmosPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
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
                            localError = ""
                            val nameVal = ValidationUtils.validateName(name)
                            val emailVal = ValidationUtils.validateEmail(email)
                            val pwdVal = ValidationUtils.validatePassword(password)
                            val typeVal = if (selectedUserType.isBlank()) {
                                ValidationResult.Error("Please select a user type")
                            } else {
                                ValidationResult.Valid
                            }

                            val aggregate = ValidationUtils.validateAll(nameVal, emailVal, pwdVal, typeVal)
                            if (!aggregate.isValid) {
                                localError = aggregate.errorMessage ?: "Invalid input"
                            } else {
                                // Helper: build member data and save onboarding, then navigate
                                val proceedToSaveAndNavigate: () -> Unit = {
                                    // Extract photo bytes if user picked one
                                    val imageBytes = when {
                                        selectedImageUri != null -> {
                                            try {
                                                context.contentResolver.openInputStream(selectedImageUri!!)?.use { it.readBytes() }
                                            } catch (e: Exception) {
                                                null
                                            }
                                        }
                                        selectedImageBitmap != null -> {
                                            try {
                                                val stream = ByteArrayOutputStream()
                                                selectedImageBitmap!!.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                                                stream.toByteArray()
                                            } catch (e: Exception) {
                                                null
                                            }
                                        }
                                        else -> null
                                    }

                                    val memberData = Member(
                                        id = "", // populated inside repository using uid
                                        name = name,
                                        headline = headline.ifBlank { if (company.isBlank()) selectedUserType else "$selectedUserType at $company" },
                                        role = role,
                                        company = company,
                                        avatarUrl = "", // will be updated by saveOnboarding with the uploaded url
                                        location = location,
                                        email = email,
                                        isLinkedInConnected = isLinkedInConnected,
                                        membershipTier = MembershipTier.EXPLORER,
                                        primaryUserType = selectedUserType
                                    )
                                    authViewModel.saveOnboarding(member = memberData, onSuccess = onNext, imageBytes = imageBytes)
                                }

                                val isCurrentCredentialsSignedUp = alreadySignedUp &&
                                        email == lastSignedUpEmail &&
                                        password == lastSignedUpPassword

                                if (isCurrentCredentialsSignedUp && currentUser != null) {
                                    // User already registered with the EXACT same email and password.
                                    // Skip signUp and go straight to saveOnboarding.
                                    proceedToSaveAndNavigate()
                                } else {
                                    // Fresh sign-up or typo correction (credentials changed)
                                    authViewModel.signUp(email, password, name, selectedUserType) {
                                        alreadySignedUp = true
                                        lastSignedUpEmail = email
                                        lastSignedUpPassword = password
                                        proceedToSaveAndNavigate()
                                    }
                                }
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
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
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
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
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
