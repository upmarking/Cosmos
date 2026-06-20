package app.cosmos.com.screens.social

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import app.cosmos.com.data.model.SocialPost
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*
import app.cosmos.com.ui.viewmodel.SocialViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.focus.onFocusChanged
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialFeedScreen(
    onPostTap: (String) -> Unit,
    onProfileTap: (String) -> Unit,
    onNavigate: (String) -> Unit,
    socialViewModel: SocialViewModel = viewModel()
) {
    val posts by socialViewModel.socialPosts.collectAsState()
    val likedPostIds by socialViewModel.likedPostIds.collectAsState()
    val isLoading by socialViewModel.isLoading.collectAsState()
    val isRefreshing by socialViewModel.isRefreshing.collectAsState()
    val currentUser by socialViewModel.currentUser.collectAsState(initial = null)
    
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var postText by remember { mutableStateOf("") }
    var isComposerFocused by remember { mutableStateOf(false) }
    var showDeleteDialogId by remember { mutableStateOf<String?>(null) }
    var is3DMode by remember { mutableStateOf(true) } // default to the gorgeous 3D view
    var showComposerDialog by remember { mutableStateOf(false) }

    var selectedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> selectedImageUri = uri }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        CosmosAmbientBackground {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                // Glass Top Bar
                CosmosGlassTopBar(
                    pageTitle = "Social Feed",
                    onSearchClick = { /* Search posts if needed */ },
                    onNotificationsClick = { onNavigate("notifications") },
                    onRequestsClick = { onNavigate("connection_requests") }
                )

                // View Mode Toggle (Segmented control)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(19.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(19.dp))
                            .background(if (!is3DMode) CosmosPrimary.copy(alpha = 0.85f) else Color.Transparent)
                            .clickable { is3DMode = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Classic Feed",
                            color = if (!is3DMode) Color.White else CosmosOnSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(19.dp))
                            .background(if (is3DMode) CosmosPrimary.copy(alpha = 0.85f) else Color.Transparent)
                            .clickable { is3DMode = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "3D Deck View ✦",
                            color = if (is3DMode) Color.White else CosmosOnSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { socialViewModel.refreshSocialPosts() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (is3DMode) {
                        if (isLoading && posts.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = CosmosPrimary)
                            }
                        } else if (posts.isEmpty()) {
                            CosmosEmptyState(
                                icon = Icons.Outlined.ChatBubbleOutline,
                                title = "No posts yet",
                                subtitle = "Be the first to share your thoughts with the community!",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            val pagerState = rememberPagerState(pageCount = { posts.size })
                            Box(modifier = Modifier.fillMaxSize()) {
                                VerticalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp) // space for floating bottom nav and top bar
                                ) { page ->
                                    val post = posts[page]
                                    val isLiked = likedPostIds.contains(post.id)
                                    val isOwner = post.authorId == currentUser?.id

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                            .graphicsLayer {
                                                // Calculate offset relative to current page
                                                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                                val absOffset = abs(pageOffset)
                                                
                                                // 3D rotation around the X-axis (cylindrical rolling effect)
                                                rotationX = pageOffset * -30f
                                                
                                                // Perspective distance (larger makes rotation subtler, smaller makes it extreme)
                                                cameraDistance = 8f * density
                                                
                                                // Card scaling
                                                val scale = 0.88f + (1f - absOffset.coerceIn(0f, 1f)) * 0.12f
                                                scaleX = scale
                                                scaleY = scale
                                                
                                                // Opacity fade
                                                alpha = 0.4f + (1f - absOffset.coerceIn(0f, 1f)) * 0.6f
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        SocialPostCard(
                                            post = post,
                                            isLiked = isLiked,
                                            isOwner = isOwner,
                                            onLikeTap = {
                                                if (isLiked) socialViewModel.unlikePost(post.id)
                                                else socialViewModel.likePost(post.id)
                                            },
                                            onCommentsTap = { onPostTap(post.id) },
                                            onProfileTap = {
                                                if (post.authorId.isNotEmpty()) {
                                                    onProfileTap(post.authorId)
                                                }
                                            },
                                            onDeleteTap = { showDeleteDialogId = post.id }
                                        )
                                    }
                                }

                                // Page indicator pill
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 12.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.4f))
                                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Post ${pagerState.currentPage + 1} of ${posts.size}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. Premium Composer Card
                            item {
                                CosmosGlassCard(showTopGradientBorder = isComposerFocused) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        CosmosAvatar(
                                            avatarUrl = currentUser?.avatarUrl ?: "",
                                            name = currentUser?.name ?: "User",
                                            size = 40.dp,
                                            isLinkedInConnected = currentUser?.isLinkedInConnected ?: false
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            OutlinedTextField(
                                                value = postText,
                                                onValueChange = { if (it.length <= 500) postText = it },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .onFocusChanged { isComposerFocused = it.isFocused },
                                                placeholder = {
                                                    Text(
                                                        "Share an insight, milestone, or request...",
                                                        color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)
                                                    )
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = CosmosPrimary,
                                                    unfocusedBorderColor = CosmosOutlineVariant.copy(alpha = 0.3f),
                                                    focusedTextColor = CosmosOnBackground,
                                                    unfocusedTextColor = CosmosOnBackground,
                                                    cursorColor = CosmosPrimary,
                                                    focusedContainerColor = CosmosSurfaceContainerLow,
                                                    unfocusedContainerColor = Color.Transparent
                                                ),
                                                maxLines = 6,
                                                minLines = if (isComposerFocused || postText.isNotEmpty() || selectedImageUri != null) 3 else 1
                                            )
                                            
                                            if (selectedImageUri != null) {
                                                Spacer(Modifier.height(12.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(200.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(CosmosSurfaceContainerHigh)
                                                        .border(1.dp, CosmosGlassBorder, RoundedCornerShape(16.dp))
                                                ) {
                                                    AsyncImage(
                                                        model = selectedImageUri,
                                                        contentDescription = "Selected image preview",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                    IconButton(
                                                        onClick = { selectedImageUri = null },
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(8.dp)
                                                            .size(28.dp)
                                                            .clip(CircleShape)
                                                            .background(Color.Black.copy(alpha = 0.6f))
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Remove image",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            AnimatedVisibility(
                                                visible = isComposerFocused || postText.isNotEmpty() || selectedImageUri != null,
                                                enter = fadeIn(),
                                                exit = fadeOut()
                                            ) {
                                                Column {
                                                    Spacer(Modifier.height(8.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        // Media attachment button
                                                        IconButton(
                                                            onClick = {
                                                                photoPickerLauncher.launch(
                                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                                )
                                                            },
                                                            modifier = Modifier.size(36.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.Image,
                                                                contentDescription = "Attach image",
                                                                tint = CosmosPrimary,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }

                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                        ) {
                                                            Text(
                                                                text = "${postText.length}/500",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = if (postText.length >= 480) CosmosError else CosmosOnSurfaceVariant
                                                            )
                                                            Button(
                                                                onClick = {
                                                                    val imageBytes = selectedImageUri?.let { uri ->
                                                                        try {
                                                                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                                                        } catch (e: Exception) {
                                                                            e.printStackTrace()
                                                                            null
                                                                        }
                                                                    }
                                                                    socialViewModel.createPost(postText.trim(), imageBytes) {
                                                                        postText = ""
                                                                        selectedImageUri = null
                                                                        isComposerFocused = false
                                                                        keyboardController?.hide()
                                                                    }
                                                                },
                                                                enabled = postText.isNotBlank() || selectedImageUri != null,
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = CosmosPrimary,
                                                                    contentColor = CosmosOnPrimary,
                                                                    disabledContainerColor = CosmosOutlineVariant.copy(alpha = 0.2f),
                                                                    disabledContentColor = CosmosOnSurfaceVariant.copy(alpha = 0.5f)
                                                                ),
                                                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                                                modifier = Modifier.height(36.dp)
                                                            ) {
                                                                Text(
                                                                    text = "Post",
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 2. Loading Shimmers
                            if (isLoading && posts.isEmpty()) {
                                items(3) {
                                    CosmosLoadingShimmer(modifier = Modifier.fillMaxWidth(), lines = 4)
                                }
                            } else if (posts.isEmpty()) {
                                // 3. Empty State
                                item {
                                    CosmosEmptyState(
                                        icon = Icons.Outlined.ChatBubbleOutline,
                                        title = "No posts yet",
                                        subtitle = "Be the first to share your thoughts with the community!",
                                        modifier = Modifier.fillParentMaxSize()
                                    )
                                }
                            } else {
                                // 4. Feed Items
                                items(posts) { post ->
                                    val isLiked = likedPostIds.contains(post.id)
                                    val isOwner = post.authorId == currentUser?.id

                                    SocialPostCard(
                                        post = post,
                                        isLiked = isLiked,
                                        isOwner = isOwner,
                                        onLikeTap = {
                                            if (isLiked) socialViewModel.unlikePost(post.id)
                                            else socialViewModel.likePost(post.id)
                                        },
                                        onCommentsTap = { onPostTap(post.id) },
                                        onProfileTap = {
                                            if (post.authorId.isNotEmpty()) {
                                                onProfileTap(post.authorId)
                                            }
                                        },
                                        onDeleteTap = { showDeleteDialogId = post.id }
                                    )
                                }
                            }

                            // Spacer at the bottom to avoid nav overlapping
                            item {
                                Spacer(Modifier.height(120.dp))
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button for 3D Mode
        if (is3DMode && posts.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showComposerDialog = true },
                containerColor = CosmosPrimary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 110.dp) // clear bottom nav
                    .size(56.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Create Post",
                    tint = Color.White
                )
            }
        }

        // Premium Full Screen Loading Overlay when posting/uploading image
        if (isLoading && posts.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {}, // block interactions
                contentAlignment = Alignment.Center
            ) {
                CosmosGlassCard(
                    modifier = Modifier.width(280.dp),
                    showTopGradientBorder = true
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(color = CosmosPrimary)
                        Text(
                            text = "Sharing your post...",
                            style = MaterialTheme.typography.titleMedium,
                            color = CosmosOnBackground,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Uploading media and updating feed",
                            style = MaterialTheme.typography.bodySmall,
                            color = CosmosOnSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialogId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialogId = null },
            title = {
                Text(
                    "Delete Post",
                    style = MaterialTheme.typography.titleLarge,
                    color = CosmosOnBackground,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete this post? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CosmosOnSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialogId?.let { socialViewModel.deletePost(it) }
                        showDeleteDialogId = null
                    }
                ) {
                    Text("Delete", color = CosmosError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogId = null }) {
                    Text("Cancel", color = CosmosOnSurfaceVariant)
                }
            },
            containerColor = CosmosSurfaceContainerLow,
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Overlay Composer Dialog for 3D Pager Mode
    if (showComposerDialog) {
        var dialogText by remember { mutableStateOf("") }
        Dialog(
            onDismissRequest = { 
                showComposerDialog = false
                selectedImageUri = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { 
                        showComposerDialog = false
                        selectedImageUri = null
                    },
                contentAlignment = Alignment.Center
            ) {
                CosmosGlassCard(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .padding(16.dp)
                        .clickable(enabled = false) {}, // prevent click-through dismiss
                    showTopGradientBorder = true
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "New Insight ✦",
                                style = MaterialTheme.typography.titleMedium,
                                color = CosmosOnBackground,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { 
                                    showComposerDialog = false
                                    selectedImageUri = null
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = CosmosOnSurfaceVariant
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CosmosAvatar(
                                avatarUrl = currentUser?.avatarUrl ?: "",
                                name = currentUser?.name ?: "User",
                                size = 40.dp,
                                isLinkedInConnected = currentUser?.isLinkedInConnected ?: false
                            )
                            Column {
                                Text(
                                    text = currentUser?.name ?: "User",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CosmosOnBackground,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = currentUser?.headline ?: "Sharing to Cosmos",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CosmosOnSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(180.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = dialogText,
                            onValueChange = { if (it.length <= 500) dialogText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            placeholder = {
                                Text(
                                    "Share an insight, milestone, or request...",
                                    color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CosmosPrimary,
                                unfocusedBorderColor = CosmosOutlineVariant.copy(alpha = 0.3f),
                                focusedTextColor = CosmosOnBackground,
                                unfocusedTextColor = CosmosOnBackground,
                                cursorColor = CosmosPrimary,
                                focusedContainerColor = CosmosSurfaceContainerLow,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        if (selectedImageUri != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CosmosSurfaceContainerHigh)
                                    .border(1.dp, CosmosGlassBorder, RoundedCornerShape(12.dp))
                            ) {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = "Selected image preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = { selectedImageUri = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove image",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = "Attach image",
                                    tint = CosmosPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "${dialogText.length}/500",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (dialogText.length >= 480) CosmosError else CosmosOnSurfaceVariant
                                )
                                Button(
                                    onClick = {
                                        val imageBytes = selectedImageUri?.let { uri ->
                                            try {
                                                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                null
                                            }
                                        }
                                        socialViewModel.createPost(dialogText.trim(), imageBytes) {
                                            dialogText = ""
                                            selectedImageUri = null
                                            showComposerDialog = false
                                        }
                                    },
                                    enabled = dialogText.isNotBlank() || selectedImageUri != null,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CosmosPrimary,
                                        contentColor = CosmosOnPrimary,
                                        disabledContainerColor = CosmosOutlineVariant.copy(alpha = 0.2f),
                                        disabledContentColor = CosmosOnSurfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(
                                        text = "Post",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SocialPostCard(
    post: SocialPost,
    isLiked: Boolean,
    isOwner: Boolean,
    onLikeTap: () -> Unit,
    onCommentsTap: () -> Unit,
    onProfileTap: () -> Unit,
    onDeleteTap: () -> Unit
) {
    val context = LocalContext.current
    
    val likeIconColor by animateColorAsState(
        targetValue = if (isLiked) CosmosError else CosmosOnSurfaceVariant,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "likeColor"
    )
    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "likeScale"
    )

    CosmosGlassCard(showTopGradientBorder = true) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Author Avatar
            CosmosAvatar(
                avatarUrl = post.authorAvatarUrl,
                name = post.authorName,
                size = 44.dp,
                isLinkedInConnected = post.isLinkedInConnected,
                modifier = Modifier.clickable(onClick = onProfileTap)
            )

            // Content Block
            Column(modifier = Modifier.weight(1f)) {
                // Header details
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = post.authorName,
                            style = MaterialTheme.typography.titleSmall,
                            color = CosmosOnBackground,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(onClick = onProfileTap)
                        )
                        Text(
                            text = post.authorHeadline,
                            style = MaterialTheme.typography.labelSmall,
                            color = CosmosOnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = post.timeString,
                            style = MaterialTheme.typography.labelSmall,
                            color = CosmosOnSurfaceVariant
                        )
                        if (isOwner) {
                            IconButton(
                                onClick = onDeleteTap,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete post",
                                    tint = CosmosOnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Post Content
                Text(
                    text = post.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CosmosOnBackground,
                    lineHeight = 20.sp
                )

                // Render Post Image if exists
                if (post.imageUrl != null) {
                    Spacer(Modifier.height(12.dp))
                    AsyncImage(
                        model = post.imageUrl,
                        contentDescription = "Post attachment",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CosmosSurfaceContainerHigh)
                            .border(1.dp, CosmosGlassBorder, RoundedCornerShape(16.dp))
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Interaction Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Like button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isLiked) CosmosError.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable(onClick = onLikeTap)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Like",
                            tint = likeIconColor,
                            modifier = Modifier.size(18.dp).scale(likeScale)
                        )
                        Text(
                            text = "${post.likesCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = likeIconColor
                        )
                    }

                    // Comments button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (post.repliesCount > 0) CosmosPrimary.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable(onClick = onCommentsTap)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = "Comments",
                            tint = CosmosOnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${post.repliesCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CosmosOnSurfaceVariant
                        )
                    }

                    // Share button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val sendIntent = android.content.Intent().apply {
                                    action = android.content.Intent.ACTION_SEND
                                    putExtra(android.content.Intent.EXTRA_TEXT, "${post.authorName} shared a post on Cosmos:\n\n\"${post.content}\"")
                                    type = "text/plain"
                                }
                                val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                                context.startActivity(shareIntent)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = CosmosOnSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Share",
                            style = MaterialTheme.typography.labelSmall,
                            color = CosmosOnSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
