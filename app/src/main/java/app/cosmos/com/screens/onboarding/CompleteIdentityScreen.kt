package app.cosmos.com.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import app.cosmos.com.data.ValidationResult
import app.cosmos.com.data.ValidationUtils
import app.cosmos.com.data.model.Member
import app.cosmos.com.data.model.MembershipTier
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*
import android.graphics.Bitmap
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CompleteIdentityScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSignInInstead: () -> Unit = {},
    authViewModel: app.cosmos.com.ui.viewmodel.AuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val initialUser = remember { authViewModel.currentUser.value }
    val startSubStep = remember { if (initialUser != null && initialUser.name.isNotBlank()) 2 else 1 }
    var subStep by remember { mutableStateOf(startSubStep) }
    
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var isDuplicateEmailError by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
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

    // Handle system back button
    BackHandler(enabled = true) {
        if (subStep > startSubStep) {
            subStep -= 1
            localError = ""
        } else {
            authViewModel.signOut {
                onBack()
            }
        }
    }

    // Prefill form if already signed up (back navigation)
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
            isDuplicateEmailError = error.contains("already exists", ignoreCase = true) ||
                error.contains("email address is already in use", ignoreCase = true)
            if (isDuplicateEmailError) {
                subStep = 1
            }
        }
    }

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(
                title = when (subStep) {
                    1 -> "Create Account"
                    2 -> "Professional Info"
                    3 -> "Security Settings"
                    else -> "Complete Your Identity"
                },
                onBack = {
                    if (subStep > startSubStep) {
                        subStep -= 1
                        localError = ""
                    } else {
                        authViewModel.signOut {
                            onBack()
                        }
                    }
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // Onboarding global progress dots
                CosmosProgressDots(totalSteps = 5, currentStep = 1, modifier = Modifier.padding(vertical = 12.dp))

                // Custom Premium Stepper Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val stepNames = listOf("Account", "Profile", "Security")
                    stepNames.forEachIndexed { index, title ->
                        val stepNum = index + 1
                        val isActive = stepNum == subStep
                        val isCompleted = stepNum < subStep

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isCompleted -> CosmosSuccess.copy(alpha = 0.2f)
                                            isActive -> CosmosPrimary.copy(alpha = 0.2f)
                                            else -> CosmosOutlineVariant.copy(alpha = 0.2f)
                                        }
                                    )
                                    .border(
                                        width = 1.5.dp,
                                        color = when {
                                            isCompleted -> CosmosSuccess
                                            isActive -> CosmosPrimary
                                            else -> CosmosOutlineVariant.copy(alpha = 0.6f)
                                        },
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCompleted) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = CosmosSuccess,
                                        modifier = Modifier.size(12.dp)
                                    )
                                } else {
                                    Text(
                                        text = stepNum.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isActive) CosmosPrimary else CosmosOnSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = when {
                                    isActive -> CosmosPrimary
                                    isCompleted -> CosmosSuccess
                                    else -> CosmosOnSurfaceVariant.copy(alpha = 0.4f)
                                }
                            )
                        }

                        if (index < 2) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(1.5.dp)
                                    .background(
                                        if (subStep > stepNum) CosmosSuccess else CosmosOutlineVariant.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Translucent Glassmorphic Form Card
                CosmosGlassCard(showTopGradientBorder = true) {
                    Text(
                        text = when (subStep) {
                            1 -> "Account Credentials"
                            2 -> "Professional Details"
                            3 -> "Secure Account"
                            else -> "Tell us who you are"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = CosmosOnBackground
                    )
                    Text(
                        text = when (subStep) {
                            1 -> "Enter your name and email address"
                            2 -> "Upload photo, select user role, and set experience"
                            3 -> "Create a secure password to register"
                            else -> "Build a credible, professional profile"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = CosmosOnSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 20.dp)
                    )

                    AnimatedContent(
                        targetState = subStep,
                        transitionSpec = {
                            if (targetState > initialState) {
                                slideInHorizontally(animationSpec = tween(300)) { width -> width } + fadeIn(animationSpec = tween(300)) togetherWith
                                        slideOutHorizontally(animationSpec = tween(300)) { width -> -width } + fadeOut(animationSpec = tween(300))
                            } else {
                                slideInHorizontally(animationSpec = tween(300)) { width -> -width } + fadeIn(animationSpec = tween(300)) togetherWith
                                        slideOutHorizontally(animationSpec = tween(300)) { width -> width } + fadeOut(animationSpec = tween(300))
                            }.using(
                                SizeTransform(clip = false)
                            )
                        },
                        label = "SignUpSubStepTransition",
                        modifier = Modifier.fillMaxWidth()
                    ) { currentSubStep ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            when (currentSubStep) {
                                1 -> {
                                    val isNameValid = name.trim().length >= 2
                                    val isEmailValid = email.trim().contains("@") && email.trim().substringAfterLast(".", "").length >= 2

                                    CosmosTextField(
                                        label = "Full Name *",
                                        value = name,
                                        onValueChange = { name = it; localError = "" },
                                        placeholder = "Alexandra Chen",
                                        trailingIcon = if (isNameValid) {
                                            { Icon(Icons.Default.CheckCircle, contentDescription = "Valid Name", tint = CosmosSuccess, modifier = Modifier.size(20.dp)) }
                                        } else null,
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
                                        trailingIcon = if (isEmailValid) {
                                            { Icon(Icons.Default.CheckCircle, contentDescription = "Valid Email", tint = CosmosSuccess, modifier = Modifier.size(20.dp)) }
                                        } else null,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Email,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = { focusManager.clearFocus() }
                                        )
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Already have an account? ", style = MaterialTheme.typography.bodyMedium, color = CosmosOnSurfaceVariant)
                                        Text(
                                            text = "Sign In",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = CosmosPrimary,
                                            modifier = Modifier.clickable { onSignInInstead() }
                                        )
                                    }
                                }
                                2 -> {
                                    // Instagram-style glowing sweep gradient Profile photo placeholder
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 28.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(contentAlignment = Alignment.BottomEnd) {
                                            Box(
                                                modifier = Modifier
                                                    .size(104.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.sweepGradient(
                                                            colors = listOf(
                                                                CosmosPrimary,
                                                                CosmosSecondary,
                                                                CosmosGradientEnd,
                                                                CosmosPrimary
                                                            )
                                                        )
                                                    )
                                                    .padding(3.dp)
                                                    .clip(CircleShape)
                                                    .background(CosmosBackground)
                                                    .padding(3.dp)
                                                    .clip(CircleShape)
                                                    .background(CosmosSurfaceContainerHigh)
                                                    .border(1.dp, CosmosOutlineVariant.copy(alpha = 0.3f), CircleShape)
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
                                                            imageVector = Icons.Default.AddAPhoto,
                                                            contentDescription = "Add photo",
                                                            tint = CosmosPrimary,
                                                            modifier = Modifier.size(28.dp)
                                                        )
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                            text = "Upload",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                            color = CosmosPrimary
                                                        )
                                                    }
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        Brush.linearGradient(
                                                            colors = listOf(CosmosGradientStart, CosmosGradientEnd)
                                                        )
                                                    )
                                                    .border(2.dp, CosmosBackground, CircleShape)
                                                    .clickable { showPhotoOptions = true },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit photo", tint = Color.White, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }

                                    Text(
                                        "I am a...",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = CosmosOnBackground,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
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

                                    Spacer(Modifier.height(12.dp))
                                    
                                    // Professional LinkedIn Import Card
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
                                }
                                3 -> {
                                    val isLengthValid = password.length >= 8
                                    val hasUppercase = password.any { it.isUpperCase() }
                                    val hasNumber = password.any { it.isDigit() }
                                    val strengthScore = (if (isLengthValid) 1 else 0) + (if (hasUppercase) 1 else 0) + (if (hasNumber) 1 else 0)

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
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
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
                                            unfocusedTextColor = CosmosOnBackground,
                                            focusedContainerColor = CosmosSurfaceContainerLow,
                                            unfocusedContainerColor = CosmosSurfaceContainerLow
                                        ),
                                        singleLine = true
                                    )

                                    // Strength rating status row
                                    val strengthLabel = when (strengthScore) {
                                        1 -> "Weak"
                                        2 -> "Medium"
                                        3 -> "Strong"
                                        else -> "Requirements unmet"
                                    }
                                    val strengthColor = when (strengthScore) {
                                        1 -> CosmosError
                                        2 -> Color(0xFFFFB300)
                                        3 -> CosmosSuccess
                                        else -> CosmosOnSurfaceVariant.copy(alpha = 0.5f)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Password Security",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CosmosOnSurfaceVariant
                                        )
                                        Text(
                                            text = strengthLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = strengthColor
                                        )
                                    }

                                    // Segmented strength indicator progress bars
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        repeat(3) { index ->
                                            val active = index < strengthScore
                                            val barColor = when (strengthScore) {
                                                1 -> CosmosError
                                                2 -> Color(0xFFFFB300)
                                                3 -> CosmosSuccess
                                                else -> CosmosOutlineVariant
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(if (active) barColor else CosmosOutlineVariant.copy(alpha = 0.3f))
                                            )
                                        }
                                    }

                                    // Interactive requirements checklist inside a sub-card
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 20.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.03f))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                            .padding(14.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "Password Requirements",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = CosmosOnBackground,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                            PasswordRequirementItem("At least 8 characters", isLengthValid)
                                            PasswordRequirementItem("At least one uppercase letter", hasUppercase)
                                            PasswordRequirementItem("At least one number", hasNumber)
                                        }
                                    }

                                    var confirmPasswordVisible by remember { mutableStateOf(false) }
                                    val passwordsMatch = password.isNotEmpty() && password == confirmPassword
                                    val showMatchError = confirmPassword.isNotEmpty() && !passwordsMatch

                                    Text(
                                        "Confirm Password *",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (showMatchError) CosmosError else CosmosOnSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    OutlinedTextField(
                                        value = confirmPassword,
                                        onValueChange = { 
                                            confirmPassword = it
                                            localError = ""
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("Confirm Password", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
                                        shape = RoundedCornerShape(12.dp),
                                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            val image = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                                Icon(imageVector = image, contentDescription = "Toggle Visibility", tint = CosmosOnSurfaceVariant)
                                            }
                                        },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Password,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = { focusManager.clearFocus() }
                                        ),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = if (showMatchError) CosmosError else if (passwordsMatch) CosmosSuccess else CosmosPrimary,
                                            unfocusedBorderColor = if (showMatchError) CosmosError else if (passwordsMatch) CosmosSuccess else CosmosOutlineVariant,
                                            focusedTextColor = CosmosOnBackground,
                                            unfocusedTextColor = CosmosOnBackground,
                                            focusedContainerColor = CosmosSurfaceContainerLow,
                                            unfocusedContainerColor = CosmosSurfaceContainerLow
                                        ),
                                        singleLine = true
                                    )

                                    if (confirmPassword.isNotEmpty()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (passwordsMatch) Icons.Default.CheckCircle else Icons.Default.Error,
                                                contentDescription = null,
                                                tint = if (passwordsMatch) CosmosSuccess else CosmosError,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = if (passwordsMatch) "Passwords match" else "Passwords do not match",
                                                color = if (passwordsMatch) CosmosSuccess else CosmosError,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
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
                        text = if (subStep == 3 || (subStep == 2 && initialUser != null)) "Complete Sign Up" else "Continue",
                        onClick = {
                            localError = ""
                            when (subStep) {
                                1 -> {
                                    val nameVal = ValidationUtils.validateName(name)
                                    val emailVal = ValidationUtils.validateEmail(email)
                                    val aggregate = ValidationUtils.validateAll(nameVal, emailVal)
                                    if (!aggregate.isValid) {
                                        localError = aggregate.errorMessage ?: "Invalid input"
                                    } else {
                                        subStep = 2
                                    }
                                }
                                2 -> {
                                    val typeVal = if (selectedUserType.isBlank()) {
                                        ValidationResult.Error("Please select a user type")
                                    } else {
                                        ValidationResult.Valid
                                    }
                                    if (!typeVal.isValid) {
                                        localError = typeVal.errorMessage ?: "Invalid input"
                                    } else {
                                        if (initialUser != null) {
                                            // Already logged in, save and go next directly!
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

                                            val memberData = initialUser.copy(
                                                name = name,
                                                headline = headline.ifBlank { if (company.isBlank()) selectedUserType else "$selectedUserType at $company" },
                                                role = role,
                                                company = company,
                                                location = location,
                                                isLinkedInConnected = isLinkedInConnected,
                                                primaryUserType = selectedUserType
                                            )
                                            authViewModel.saveOnboarding(member = memberData, onSuccess = onNext, imageBytes = imageBytes)
                                        } else {
                                            subStep = 3
                                        }
                                    }
                                }
                                3 -> {
                                    val pwdVal = ValidationUtils.validatePassword(password)
                                    val pwdMatchVal = ValidationUtils.validatePasswordMatch(password, confirmPassword)
                                    val aggregate = ValidationUtils.validateAll(pwdVal, pwdMatchVal)
                                    if (!aggregate.isValid) {
                                        localError = aggregate.errorMessage ?: "Invalid password"
                                    } else {
                                        // Proceed with Firebase registration
                                        val proceedToSaveAndNavigate: () -> Unit = {
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
                                                id = "",
                                                name = name,
                                                headline = headline.ifBlank { if (company.isBlank()) selectedUserType else "$selectedUserType at $company" },
                                                role = role,
                                                company = company,
                                                avatarUrl = "",
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
                                            proceedToSaveAndNavigate()
                                        } else {
                                            authViewModel.signUp(email, password, name, selectedUserType) {
                                                alreadySignedUp = true
                                                lastSignedUpEmail = email
                                                lastSignedUpPassword = password
                                                
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
                                                    id = "",
                                                    name = name,
                                                    headline = headline.ifBlank { if (company.isBlank()) selectedUserType else "$selectedUserType at $company" },
                                                    role = role,
                                                    company = company,
                                                    avatarUrl = "",
                                                    location = location,
                                                    email = email,
                                                    isLinkedInConnected = isLinkedInConnected,
                                                    membershipTier = MembershipTier.EXPLORER,
                                                    primaryUserType = selectedUserType
                                                )

                                                authViewModel.saveOnboarding(
                                                    member = memberData,
                                                    onSuccess = {
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            "Verification email sent! Please check your inbox.",
                                                            android.widget.Toast.LENGTH_LONG
                                                        ).show()
                                                        onNext()
                                                    },
                                                    imageBytes = imageBytes,
                                                    optimisticNavigation = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        enabled = when (subStep) {
                            1 -> name.isNotBlank() && email.isNotBlank()
                            2 -> selectedUserType.isNotBlank()
                            3 -> password.isNotBlank() && confirmPassword.isNotBlank()
                            else -> false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PasswordRequirementItem(text: String, isMet: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(if (isMet) CosmosSuccess.copy(alpha = 0.15f) else Color.Transparent)
                .border(1.5.dp, if (isMet) CosmosSuccess else CosmosOnSurfaceVariant.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isMet) {
                Icon(Icons.Default.Check, contentDescription = null, tint = CosmosSuccess, modifier = Modifier.size(10.dp))
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isMet) CosmosOnBackground else CosmosOnSurfaceVariant.copy(alpha = 0.6f)
        )
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
    isError: Boolean = false,
    errorMessage: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(bottom = 16.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isError) CosmosError else CosmosOnSurfaceVariant,
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
            isError = isError,
            trailingIcon = trailingIcon,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (isError) CosmosError else CosmosPrimary,
                unfocusedBorderColor = if (isError) CosmosError else CosmosOutlineVariant,
                focusedTextColor = CosmosOnBackground,
                unfocusedTextColor = CosmosOnBackground,
                cursorColor = CosmosPrimary,
                focusedContainerColor = CosmosSurfaceContainerLow,
                unfocusedContainerColor = CosmosSurfaceContainerLow,
                errorBorderColor = CosmosError,
                errorLabelColor = CosmosError,
                errorTextColor = CosmosOnBackground,
                errorContainerColor = CosmosSurfaceContainerLow
            ),
            singleLine = true
        )
        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                color = CosmosError,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
    }
}
