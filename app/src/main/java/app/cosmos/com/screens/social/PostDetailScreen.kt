package app.cosmos.com.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Send
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
import app.cosmos.com.data.model.SocialPostReply
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*
import app.cosmos.com.ui.viewmodel.SocialViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: String,
    onBack: () -> Unit,
    onProfileTap: (String) -> Unit,
    socialViewModel: SocialViewModel = viewModel()
) {
    LaunchedEffect(postId) {
        socialViewModel.selectPost(postId)
    }

    val post by socialViewModel.activePost.collectAsState()
    val replies by socialViewModel.activePostReplies.collectAsState()
    val isRepliesLoading by socialViewModel.isRepliesLoading.collectAsState()
    val likedPostIds by socialViewModel.likedPostIds.collectAsState()
    val currentUser by socialViewModel.currentUser.collectAsState(initial = null)

    var replyText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    CosmosAmbientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
        ) {
            // Glass Top Bar
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
                            SocialPostCard(
                                post = activePost,
                                isLiked = isLiked,
                                isOwner = activePost.authorId == currentUser?.id,
                                onLikeTap = {
                                    if (isLiked) socialViewModel.unlikePost(activePost.id)
                                    else socialViewModel.likePost(activePost.id)
                                },
                                onCommentsTap = { /* Already on detail screen */ },
                                onProfileTap = {
                                    if (activePost.authorId.isNotEmpty()) {
                                        onProfileTap(activePost.authorId)
                                    }
                                },
                                onDeleteTap = {
                                    socialViewModel.deletePost(activePost.id)
                                    onBack()
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
                                ReplyCard(
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

                    // Comments Composer Bar
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
                                        socialViewModel.addReply(postId, replyText.trim())
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
                }
            }
        }
    }
}

@Composable
fun ReplyCard(
    reply: SocialPostReply,
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
                isLinkedInConnected = reply.isLinkedInConnected,
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
