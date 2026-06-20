package app.cosmos.com.screens.communities

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.style.TextOverflow
import app.cosmos.com.data.model.CirclePostReply
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*
import app.cosmos.com.ui.viewmodel.CommunityViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrbitPostDetailScreen(
    circleId: String,
    postId: String,
    onBack: () -> Unit,
    onProfileTap: (String) -> Unit,
    communityViewModel: CommunityViewModel = viewModel()
) {
    LaunchedEffect(circleId, postId) {
        communityViewModel.selectOrbitPost(circleId, postId)
        communityViewModel.loadCircles()
    }

    val post by communityViewModel.activeOrbitPost.collectAsState()
    val replies by communityViewModel.orbitPostReplies.collectAsState()
    val isRepliesLoading by communityViewModel.isOrbitRepliesLoading.collectAsState()
    val circles by communityViewModel.circles.collectAsState()
    val likedPostIds by communityViewModel.likedPostIds.collectAsState()

    val circle = circles.find { it.id == circleId }
    val isJoined = circle?.isJoined ?: false

    val currentUserState by communityViewModel.currentUser.collectAsState(initial = null)
    val currentUserId = currentUserState?.id ?: ""
    var showDeleteDialog by remember { mutableStateOf(false) }

    var replyText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    CosmosAmbientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            CosmosTopBar(
                title = "Discussion",
                onBack = onBack
            )

            if (post == null) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CosmosPrimary)
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Original Post Card
                        item {
                            val activePost = post!!
                            val isLiked = likedPostIds.contains(activePost.id)
                            OrbitFeedPost(
                                author = activePost.author,
                                avatarUrl = activePost.avatarUrl,
                                content = activePost.content,
                                time = activePost.timeString,
                                likesCount = activePost.likesCount,
                                repliesCount = activePost.repliesCount,
                                isLiked = isLiked,
                                isOwner = activePost.authorId == currentUserId || circle?.createdBy == currentUserId,
                                onLikeTap = {
                                    if (isLiked) communityViewModel.unlikePost(circleId, activePost.id)
                                    else communityViewModel.likePost(circleId, activePost.id)
                                },
                                onCommentsTap = { /* Already on detail screen */ },
                                onDeleteTap = {
                                    showDeleteDialog = true
                                },
                                onAuthorTap = {
                                    if (activePost.authorId.isNotEmpty()) {
                                        onProfileTap(activePost.authorId)
                                    }
                                }
                            )
                        }

                        // Section Header
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Comments (${replies.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = CosmosOnBackground,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Replies list
                        if (isRepliesLoading && replies.isEmpty()) {
                            item {
                                CosmosLoadingShimmer(modifier = Modifier.fillMaxWidth(), lines = 3)
                            }
                        } else if (replies.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No comments yet. Start the conversation!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CosmosOnSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(replies) { reply ->
                                OrbitReplyCard(
                                    reply = reply,
                                    onProfileTap = {
                                        if (reply.authorId.isNotEmpty()) {
                                            onProfileTap(reply.authorId)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Comments Composer Bar / Join CTA Banner
                    if (isJoined) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF16191F).copy(alpha = 0.95f))
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = replyText,
                                    onValueChange = { replyText = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = {
                                        Text(
                                            "Add a comment...",
                                            color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CosmosPrimary,
                                        unfocusedBorderColor = CosmosOutlineVariant.copy(alpha = 0.3f),
                                        focusedTextColor = CosmosOnBackground,
                                        unfocusedTextColor = CosmosOnBackground,
                                        cursorColor = CosmosPrimary,
                                        focusedContainerColor = CosmosSurfaceContainerLow,
                                        unfocusedContainerColor = CosmosSurfaceContainerLow
                                    ),
                                    maxLines = 4,
                                    singleLine = false
                                )

                                IconButton(
                                    onClick = {
                                        if (replyText.isNotBlank()) {
                                            communityViewModel.addOrbitPostReply(circleId, post!!.id, replyText.trim())
                                            replyText = ""
                                            keyboardController?.hide()
                                        }
                                    },
                                    enabled = replyText.isNotBlank(),
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(
                                            if (replyText.isNotBlank())
                                                CosmosPrimary
                                            else
                                                CosmosOutlineVariant.copy(alpha = 0.2f)
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send comment",
                                        tint = if (replyText.isNotBlank()) Color.White else CosmosOnSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF16191F).copy(alpha = 0.95f))
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val isPrivate = circle?.isPrivate == true
                                val isPending = circle?.isPending == true
                                Text(
                                    text = if (isPrivate) "Request access to comment" else "Join this Orbit to comment",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CosmosOnBackground,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isPrivate)
                                        "Only approved members of this orbit can participate in discussions."
                                    else
                                        "Only members of this orbit can participate in discussions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = CosmosOnSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(Modifier.height(4.dp))
                                CosmosButton(
                                    text = if (isPrivate) {
                                        if (isPending) "Request Pending" else "Request Access"
                                    } else {
                                        "Join Orbit"
                                    },
                                    onClick = {
                                        communityViewModel.joinCircle(circleId)
                                    },
                                    enabled = !isPending,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog && post != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
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
                        post?.id?.let { communityViewModel.deleteOrbitPost(circleId, it) }
                        showDeleteDialog = false
                        onBack()
                    }
                ) {
                    Text("Delete", color = CosmosError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = CosmosOnSurfaceVariant)
                }
            },
            containerColor = CosmosSurfaceContainerLow,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun OrbitReplyCard(
    reply: CirclePostReply,
    onProfileTap: () -> Unit
) {
    CosmosGlassCard(showTopGradientBorder = false) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CosmosAvatar(
                avatarUrl = reply.authorAvatarUrl,
                name = reply.authorName,
                size = 36.dp,
                isLinkedInConnected = false,
                modifier = Modifier.clickable(onClick = onProfileTap)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = reply.authorName,
                            style = MaterialTheme.typography.titleSmall,
                            color = CosmosOnBackground,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(onClick = onProfileTap)
                        )
                        Text(
                            text = reply.authorHeadline,
                            style = MaterialTheme.typography.labelSmall,
                            color = CosmosOnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = reply.timeString,
                        style = MaterialTheme.typography.labelSmall,
                        color = CosmosOnSurfaceVariant
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = reply.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CosmosOnBackground,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
