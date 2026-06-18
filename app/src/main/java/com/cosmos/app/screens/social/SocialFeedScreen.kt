package com.cosmos.app.screens.social

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.cosmos.app.data.model.SocialPost
import com.cosmos.app.ui.components.*
import com.cosmos.app.ui.theme.*
import com.cosmos.app.ui.viewmodel.SocialViewModel


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

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            // Glass Top Bar
            CosmosGlassTopBar(
                pageTitle = "Social Feed",
                onSearchClick = { /* Search posts if needed */ },
                onNotificationsClick = { onNavigate("notifications") },
                onRequestsClick = { onNavigate("connection_requests") }
            )

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { socialViewModel.refreshSocialPosts() },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Premium Composer Card
                    item {
                        CosmosGlassCard(showTopGradientBorder = false) {
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
                                        modifier = Modifier.fillMaxWidth(),
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
                                        minLines = if (isComposerFocused || postText.isNotEmpty()) 3 else 1
                                    )
                                    
                                    AnimatedVisibility(
                                        visible = isComposerFocused || postText.isNotEmpty(),
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
                                                // Media attachment indicator (mocked)
                                                IconButton(
                                                    onClick = {
                                                        Toast.makeText(context, "Image attachments coming in the next release!", Toast.LENGTH_SHORT).show()
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
                                                    CosmosButton(
                                                        text = "Post",
                                                        onClick = {
                                                            socialViewModel.createPost(postText.trim())
                                                            postText = ""
                                                            isComposerFocused = false
                                                            keyboardController?.hide()
                                                        },
                                                        enabled = postText.isNotBlank(),
                                                        modifier = Modifier.width(80.dp).height(36.dp)
                                                    )
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
                                    if (post.authorId.isNotEmpty() && !post.authorId.startsWith("mock_user_")) {
                                        onProfileTap(post.authorId)
                                    } else if (post.authorId.isNotEmpty()) {
                                        onProfileTap(post.authorId)
                                    }
                                },
                                onDeleteTap = { showDeleteDialogId = post.id }
                            )
                        }
                    }

                    // Spacer at the bottom to avoid nav overlapping
                    item {
                        Spacer(Modifier.height(80.dp))
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

    CosmosGlassCard(showTopGradientBorder = false) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Author Avatar
            CosmosAvatar(
                avatarUrl = post.authorAvatarUrl,
                name = post.authorName,
                size = 44.dp,
                isLinkedInConnected = true, // default premium look
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

                // Render Post Image if exists (future expansion / seeded posts support)
                if (post.imageUrl != null) {
                    Spacer(Modifier.height(12.dp))
                    AsyncImage(
                        model = post.imageUrl,
                        contentDescription = "Post attachment",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CosmosSurfaceContainerHigh)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Interaction Row
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Like button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
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
                                Toast.makeText(context, "Post link copied to clipboard!", Toast.LENGTH_SHORT).show()
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
