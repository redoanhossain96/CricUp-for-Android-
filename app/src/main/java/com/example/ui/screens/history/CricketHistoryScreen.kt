package com.example.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.data.repository.MatchSearchResult
import com.example.di.AppContainer
import com.example.ui.components.MatchCard
import com.example.ui.theme.adaptiveGoldColor
import com.example.util.MatchCodeGenerator
import kotlinx.coroutines.launch

@Composable
fun CricketHistoryScreen(
    container: AppContainer,
    onNavigateToMatch: (String) -> Unit
) {
    val allMatches by container.matchRepository.getAllMatchesFlow().collectAsState(initial = emptyList())
    val currentUser by remember(container) { container.authRepository.getCurrentUserFlow() }.collectAsState()
    val currentUserId = currentUser?.uid?.takeIf { it.isNotBlank() }
        ?: container.authRepository.getCurrentUserId()
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var isSearchingCloudCode by remember { mutableStateOf(false) }
    var cloudSearchFeedback by remember { mutableStateOf<String?>(null) }

    val filterOptions = listOf("All", "Completed", "Upcoming", "My Matches", "Tournaments")

    val filteredMatches = remember(allMatches, selectedFilter, searchQuery, currentUserId) {
        val q = searchQuery.trim().lowercase()
        val normalizedCodeQuery = MatchCodeGenerator.normalizeCode(searchQuery)

        allMatches
            .filter { match ->
                val matchesFilter = when (selectedFilter) {
                    "Completed" -> match.status == MatchStatus.COMPLETED
                    "Upcoming" -> match.status == MatchStatus.UPCOMING
                    "My Matches" -> match.ownerId == currentUserId ||
                        match.effectiveOwnerUid == currentUserId ||
                        match.authorizedScorers.contains(currentUserId)
                    "Tournaments" -> !match.tournamentId.isNullOrEmpty()
                    else -> true
                }

                val matchesSearch = if (q.isEmpty()) true else {
                    val codeNorm = MatchCodeGenerator.normalizeCode(match.matchCode)
                    val altCodeNorm = MatchCodeGenerator.normalizeCode(match.code)
                    match.title.lowercase().contains(q) ||
                        match.teamA.name.lowercase().contains(q) ||
                        match.teamB.name.lowercase().contains(q) ||
                        match.venue.lowercase().contains(q) ||
                        match.tournamentName?.lowercase()?.contains(q) == true ||
                        (normalizedCodeQuery.isNotEmpty() && (
                            codeNorm.contains(normalizedCodeQuery) ||
                                altCodeNorm.contains(normalizedCodeQuery)
                            ))
                }

                matchesFilter && matchesSearch
            }
            .sortedByDescending { it.completedAt ?: it.updatedAt }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val goldAccent = adaptiveGoldColor()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(14.dp)
    ) {
        Text(
            text = "MATCH HISTORY & RECENT MATCHES",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "সাম্প্রতিক ও পূর্ববর্তী সকল ম্যাচ, স্কোরকার্ড এবং ইউনিক ম্যাচ কোড সার্চ",
            color = goldAccent,
            fontSize = 11.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar (supports Unique Match Code, Team, Venue, Title)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                cloudSearchFeedback = null
            },
            placeholder = {
                Text("ম্যাচ কোড (CR7K9M), দল, ভেন্যু বা নাম দিয়ে খুঁজুন...", fontSize = 12.sp, maxLines = 1)
            },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        cloudSearchFeedback = null
                    }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filters Row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(filterOptions, key = { it }) { filter ->
                val isSelected = selectedFilter == filter
                Button(
                    onClick = { selectedFilter = filter },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) primaryColor else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("filter_$filter")
                ) {
                    Text(
                        text = filter,
                        color = if (isSelected) onPrimaryColor else MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredMatches.isEmpty()) {
            val cleanCode = MatchCodeGenerator.normalizeCode(searchQuery)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (searchQuery.isBlank()) {
                            "এখনো কোনো ম্যাচ ইতিহাস নেই।"
                        } else {
                            "'$searchQuery' অনুযায়ী স্থানীয় ইতিহাসে কোনো ম্যাচ পাওয়া যায়নি।"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    if (cleanCode.length >= 4) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                isSearchingCloudCode = true
                                cloudSearchFeedback = null
                                scope.launch {
                                    when (val res = container.matchRepository.searchMatchDetailed(cleanCode)) {
                                        is MatchSearchResult.Success -> {
                                            isSearchingCloudCode = false
                                            onNavigateToMatch(res.match.id)
                                        }
                                        is MatchSearchResult.NotFound -> {
                                            isSearchingCloudCode = false
                                            cloudSearchFeedback = "কোড '$cleanCode' দিয়ে কোনো পাবলিক ম্যাচ পাওয়া যায়নি।"
                                        }
                                        is MatchSearchResult.PrivateMatch -> {
                                            isSearchingCloudCode = false
                                            cloudSearchFeedback = res.message
                                        }
                                        is MatchSearchResult.NetworkError -> {
                                            isSearchingCloudCode = false
                                            cloudSearchFeedback = res.message
                                        }
                                        is MatchSearchResult.PermissionDenied -> {
                                            isSearchingCloudCode = false
                                            cloudSearchFeedback = res.message
                                        }
                                        is MatchSearchResult.Error -> {
                                            isSearchingCloudCode = false
                                            cloudSearchFeedback = res.message
                                        }
                                    }
                                }
                            },
                            enabled = !isSearchingCloudCode,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("history_cloud_code_search_btn")
                        ) {
                            if (isSearchingCloudCode) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = "অনলাইনে কোড '$cleanCode' খুঁজুন",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    cloudSearchFeedback?.let { feedbackMsg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = feedbackMsg,
                            color = Color(0xFFE53935),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else {
            val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(filteredMatches, key = { it.id }) { match ->
                    MatchCard(match = match, onClick = { onNavigateToMatch(match.id) })
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
