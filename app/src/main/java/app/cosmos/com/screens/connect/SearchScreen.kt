package app.cosmos.com.screens.connect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cosmos.com.data.model.Member
import app.cosmos.com.ui.components.*
import app.cosmos.com.ui.theme.*
import app.cosmos.com.ui.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onProfileTap: (String) -> Unit,
    onBack: () -> Unit,
    searchViewModel: SearchViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val searchResults by searchViewModel.searchResults.collectAsState()
    val isSearching by searchViewModel.isSearching.collectAsState()

    CosmosAmbientBackground {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CosmosTopBar(title = "Search Profiles", onBack = onBack)

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchViewModel.updateQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                placeholder = { Text("Search by name, headline, etc.", color = CosmosOnSurfaceVariant.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = CosmosOnSurfaceVariant) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchViewModel.clearSearch() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = CosmosOnSurfaceVariant)
                        }
                    }
                },
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

            // Search Results
            Box(modifier = Modifier.fillMaxSize()) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = CosmosPrimary
                    )
                } else if (searchQuery.trim().length >= 2 && searchResults.isEmpty()) {
                    Text(
                        text = "No results found for \"$searchQuery\"",
                        color = CosmosOnSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (searchResults.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(searchResults) { member ->
                            SearchResultCard(member = member, onTap = { onProfileTap(member.id) })
                        }
                    }
                } else {
                    Text(
                        text = "Type to search for members...",
                        color = CosmosOnSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchResultCard(member: Member, onTap: () -> Unit) {
    CosmosGlassCard(
        modifier = Modifier.clickable(onClick = onTap)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CosmosAvatar(
                avatarUrl = member.avatarUrl,
                name = member.name,
                size = 56.dp,
                isLinkedInConnected = member.isLinkedInConnected
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = CosmosOnBackground,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = member.headline,
                    style = MaterialTheme.typography.bodySmall,
                    color = CosmosOnSurfaceVariant,
                    maxLines = 2
                )
                if (member.tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        member.tags.take(3).forEach { CosmosTagChip(text = "#$it") }
                    }
                }
            }
        }
    }
}
