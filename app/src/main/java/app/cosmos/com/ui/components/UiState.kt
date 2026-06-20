package app.cosmos.com.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Loading Shimmer ─────────────────────────────────────────────────────────

@Composable
fun CosmosLoadingShimmer(
    modifier: Modifier = Modifier,
    lines: Int = 4
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 1300f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1C1C2E).copy(alpha = 0.3f),
            Color(0xFF2E2E48).copy(alpha = 0.5f),
            Color(0xFF1C1C2E).copy(alpha = 0.3f)
        ),
        start = Offset(shimmerTranslate, 0f),
        end = Offset(shimmerTranslate + 400f, 0f)
    )

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(lines) { index ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index % 2 == 0) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(shimmerBrush)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (index % 3 == 0) 0.7f else 0.9f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )
                }
            }
        }
    }
}

@Composable
fun CosmosCardShimmer(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "cardShimmer")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -300f, targetValue = 1300f,
        animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Restart),
        label = "cardShimmerOffset"
    )
    val shimmerBrush = Brush.linearGradient(
        colors = listOf(Color(0xFF1C1C2E).copy(alpha = 0.3f), Color(0xFF2E2E48).copy(alpha = 0.5f), Color(0xFF1C1C2E).copy(alpha = 0.3f)),
        start = Offset(shimmerTranslate, 0f), end = Offset(shimmerTranslate + 400f, 0f)
    )

    Card(
        modifier = modifier.fillMaxWidth().height(200.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C2E))
    ) {
        Box(modifier = Modifier.fillMaxSize().background(shimmerBrush))
    }
}

// ── Loading Overlay ─────────────────────────────────────────────────────────

@Composable
fun CosmosLoadingOverlay(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFC0C1FF), strokeWidth = 3.dp)
            }
        }
    }
}

// ── Error State ─────────────────────────────────────────────────────────────

@Composable
fun CosmosErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.ErrorOutline
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon, contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color(0xFFFF6B6B)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Something went wrong",
            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF494BD6)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Retry", fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ── Empty State ─────────────────────────────────────────────────────────────

@Composable
fun CosmosEmptyState(
    icon: ImageVector = Icons.Default.Inbox,
    title: String,
    subtitle: String = "",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon, contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = Color(0xFF494BD6).copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF494BD6)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(actionLabel, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ── Search Bar ──────────────────────────────────────────────────────────────

@Composable
fun CosmosSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search..."
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().height(52.dp),
        placeholder = { Text(placeholder, color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.5f)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.5f))
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF1C1C2E),
            unfocusedContainerColor = Color(0xFF1C1C2E),
            focusedBorderColor = Color(0xFF494BD6),
            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color(0xFFC0C1FF)
        )
    )
}

// ── Filter Chips ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CosmosFilterChips(
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelected(option) },
                label = { Text(option, fontSize = 12.sp) },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color(0xFF1C1C2E),
                    selectedContainerColor = Color(0xFF494BD6),
                    labelColor = Color.White.copy(alpha = 0.7f),
                    selectedLabelColor = Color.White
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = Color.White.copy(alpha = 0.1f),
                    selectedBorderColor = Color.Transparent,
                    enabled = true,
                    selected = isSelected
                )
            )
        }
    }
}

// ── Form Field ──────────────────────────────────────────────────────────────

@Composable
fun CosmosFormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp) },
            singleLine = singleLine,
            minLines = minLines,
            enabled = enabled,
            isError = error != null,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1C1C2E),
                unfocusedContainerColor = Color(0xFF1C1C2E),
                errorContainerColor = Color(0xFF1C1C2E),
                focusedBorderColor = Color(0xFF494BD6),
                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                errorBorderColor = Color(0xFFFF6B6B),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                errorTextColor = Color.White,
                cursorColor = Color(0xFFC0C1FF)
            )
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = error,
                color = Color(0xFFFF6B6B),
                fontSize = 12.sp
            )
        }
    }
}

// ── Confirm Dialog ──────────────────────────────────────────────────────────

@Composable
fun CosmosConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    dismissLabel: String = "Cancel",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = Color.White.copy(alpha = 0.7f)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) Color(0xFFFF6B6B) else Color(0xFF494BD6)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = Color(0xFF1C1C2E),
        shape = RoundedCornerShape(20.dp)
    )
}
