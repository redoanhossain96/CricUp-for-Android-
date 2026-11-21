package com.example.ui.screens.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import com.example.ui.theme.CricUpIcons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.di.AppContainer
import com.example.ui.components.MatchCard
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.WicketRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PublicLiveMatchesScreen(
    container: AppContainer,
    onNavigateToMatch: (String) -> Unit,
    onNavigateToCreateMatch: () -> Unit,
    onNavigateToAuth: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val allMatches by container.matchRepository.getAllMatchesFlow().collectAsState(initial = emptyList())
    val isGuest by container.authRepository.isGuestMode.collectAsState()

    var showGuestDialog by remember { mutableStateOf(false) }
    var searchCodeInput by remember { mutableStateOf("") }
    var searchedMatch by remember { mutableStateOf<Match?>(null) }
    var cloudSearchMatches by remember { mutableStateOf<List<Match>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchErrorMessage by remember { mutableStateOf<String?>(null) }

    var selectedFilter by remember { mutableStateOf("Live") }
    val filters = listOf("Live", "All", "Upcoming", "Completed")

    fun performSearch(rawQuery: String) {
        val trimmed = rawQuery.trim()
        if (trimmed.isBlank()) {
            searchedMatch = null
            cloudSearchMatches = emptyList()
            hasSearched = false
            searchErrorMessage = null
            return
        }
        val cleanCode = com.example.util.MatchCodeGenerator.normalizeCode(trimmed)
        isSearching = true
        hasSearched = true
        searchErrorMessage = null
        searchedMatch = null
        scope.launch {
            val queryMatches = container.matchRepository.searchMatchesByQueryFromCloud(trimmed)
            if (queryMatches.isNotEmpty()) {
                isSearching = false
                cloudSearchMatches = queryMatches
                searchedMatch = queryMatches.firstOrNull { m ->
                    cleanCode.isNotBlank() && (
                        com.example.util.MatchCodeGenerator.normalizeCode(m.matchCode) == cleanCode ||
                            com.example.util.MatchCodeGenerator.normalizeCode(m.code) == cleanCode
                        )
                }
                searchErrorMessage = null
            } else if (cleanCode.length in 4..10 && !trimmed.contains(' ')) {
                val result = container.matchRepository.searchMatchDetailed(cleanCode)
                isSearching = false
                when (result) {
                    is com.example.data.repository.MatchSearchResult.Success -> {
                        searchedMatch = result.match
                        cloudSearchMatches = listOf(result.match)
                        searchErrorMessage = null
                    }
                    is com.example.data.repository.MatchSearchResult.NotFound -> {
                        searchedMatch = null
                        cloudSearchMatches = emptyList()
                        searchErrorMessage = "এই কোড, টুর্নামেন্ট বা দলের নামে কোনো ম্যাচ পাওয়া যায়নি।"
                    }
                    is com.example.data.repository.MatchSearchResult.PrivateMatch -> {
                        searchedMatch = null
                        searchErrorMessage = "এই ম্যাচটি প্রাইভেট (Private Match - শুধুমাত্র ম্যাচ নির্মাতা দেখতে পারবেন)"
                    }
                    is com.example.data.repository.MatchSearchResult.PermissionDenied -> {
                        searchedMatch = null
                        searchErrorMessage = "Firebase Console-এ সিকিউরিটি রুলস পাবলিশ (Publish) করুন - Permission Denied"
                    }
                    is com.example.data.repository.MatchSearchResult.NetworkError -> {
                        searchedMatch = null
                        searchErrorMessage = "ইন্টারনেট সংযোগ চেক করুন (Check your internet connection)"
                    }
                    is com.example.data.repository.MatchSearchResult.Error -> {
                        searchedMatch = null
                        searchErrorMessage = result.message
                    }
                }
            } else {
                isSearching = false
                cloudSearchMatches = emptyList()
                searchErrorMessage = "\"$trimmed\" নামে কোনো ম্যাচ, টুর্নামেন্ট বা দল পাওয়া যায়নি।"
            }
        }
    }

    LaunchedEffect(searchCodeInput) {
        val trimmed = searchCodeInput.trim()
        if (trimmed.length >= 2) {
            delay(380L)
            performSearch(trimmed)
        }
    }

    val displayedMatches = remember(allMatches, cloudSearchMatches, selectedFilter, searchCodeInput) {
        val trimmedQuery = searchCodeInput.trim()
        if (trimmedQuery.isNotBlank()) {
            (allMatches + cloudSearchMatches)
                .distinctBy { it.id }
                .filter { com.example.util.MatchSearchHelper.matchesLiveSearchQuery(it, trimmedQuery) }
                .sortedWith(
                    compareBy<Match> {
                        when (it.status) {
                            MatchStatus.LIVE, MatchStatus.INNINGS_BREAK -> 0
                            MatchStatus.UPCOMING -> 1
                            MatchStatus.COMPLETED -> 2
                            else -> 3
                        }
                    }.thenByDescending { it.createdAt }
                )
        } else {
            when (selectedFilter) {
                "Live" -> allMatches.filter { it.status == MatchStatus.LIVE || it.status == MatchStatus.INNINGS_BREAK }
                "Upcoming" -> allMatches.filter { it.status == MatchStatus.UPCOMING }
                "Completed" -> allMatches.filter { it.status == MatchStatus.COMPLETED }
                else -> allMatches
            }
        }
    }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Public Live Header Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CricUpIcons.Sensors,
                                contentDescription = "Live",
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "LIVE CRICKET CENTRE",
                                    color = Color(0xFFE53935),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE53935))
                                )
                            }
                            Text(
                                text = "Real-Time Public Live Scores",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (isGuest) {
            item {
                com.example.ui.components.GuestVisitorBanner(
                    onNavigateToAuth = { showGuestDialog = true }
                )
            }
        }

        // 2. Search Match by Code, Tournament Name, or Team Name
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldYellow.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "ম্যাচ খুঁজুন (কোড / টুর্নামেন্ট / দলের নাম)",
                        color = com.example.ui.theme.adaptiveGoldColor(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.4.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchCodeInput,
                            onValueChange = {
                                searchCodeInput = it
                                searchedMatch = null
                                hasSearched = false
                                searchErrorMessage = null
                                if (it.isBlank()) {
                                    cloudSearchMatches = emptyList()
                                }
                            },
                            placeholder = { Text("কোড, টুর্নামেন্ট বা দলের নাম লিখুন", fontSize = 12.5.sp) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Search, contentDescription = "Search matches", tint = GoldYellow)
                            },
                            trailingIcon = {
                                if (searchCodeInput.isNotEmpty()) {
                                    IconButton(onClick = {
                                        searchCodeInput = ""
                                        searchedMatch = null
                                        cloudSearchMatches = emptyList()
                                        hasSearched = false
                                        searchErrorMessage = null
                                    }) {
                                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { performSearch(searchCodeInput) }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldYellow,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("search_match_by_code_input")
                        )

                        Button(
                            onClick = { performSearch(searchCodeInput) },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldYellow),
                            shape = RoundedCornerShape(10.dp),
                            enabled = searchCodeInput.isNotBlank() && !isSearching,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("search_match_by_code_btn")
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                            } else {
                                Text("খুঁজুন", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    val found = searchedMatch
                    if (hasSearched && searchErrorMessage != null && displayedMatches.isEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(WicketRed.copy(alpha = 0.12f))
                                .border(1.dp, WicketRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = WicketRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = searchErrorMessage ?: "কোনো ম্যাচ পাওয়া যায়নি।",
                                color = WicketRed,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (hasSearched && found != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(EmeraldGreen.copy(alpha = 0.1f))
                                .border(1.dp, EmeraldGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✓ EXACT MATCH FOUND", color = com.example.ui.theme.adaptiveEmeraldColor(), fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                                if (!found.isOfflineOnlyMatch() && found.matchCode.isNotBlank()) {
                                    Text("CODE: ${found.matchCode}", color = com.example.ui.theme.adaptiveGoldColor(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            MatchCard(
                                match = found,
                                onClick = { onNavigateToMatch(found.id) }
                            )
                        }
                    } else if (hasSearched && searchCodeInput.isNotBlank() && displayedMatches.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "সার্চ ফলাফল: ${displayedMatches.size}টি ম্যাচ পাওয়া গেছে",
                            color = com.example.ui.theme.adaptiveEmeraldColor(),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 3. Category Filter Chips (Live, All, Upcoming, Completed)
        if (searchCodeInput.isBlank()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filters, key = { it }) { f ->
                        FilterChip(
                            selected = selectedFilter == f,
                            onClick = { selectedFilter = f },
                            label = {
                                val count = when (f) {
                                    "Live" -> allMatches.count { it.status == MatchStatus.LIVE || it.status == MatchStatus.INNINGS_BREAK }
                                    "Upcoming" -> allMatches.count { it.status == MatchStatus.UPCOMING }
                                    "Completed" -> allMatches.count { it.status == MatchStatus.COMPLETED }
                                    else -> allMatches.size
                                }
                                Text("$f ($count)", fontWeight = if (selectedFilter == f) FontWeight.Bold else FontWeight.Normal)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (f == "Live") Color(0xFFE53935) else MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // 4. Matches List
        if (displayedMatches.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (searchCodeInput.isNotBlank()) {
                                "\"${searchCodeInput.trim()}\" এর সাথে কোনো ম্যাচ মেলেনি"
                            } else {
                                "No $selectedFilter matches available right now"
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (searchCodeInput.isNotBlank()) {
                                "অন্য ডিভাইসের ম্যাচ খুঁজতে উপরের 'খুঁজুন' বাটনে চাপ দিন।"
                            } else {
                                "Create a new match or search using match code, tournament or team name."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.5.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (isGuest) {
                                    showGuestDialog = true
                                } else {
                                    onNavigateToCreateMatch()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create New Match", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(displayedMatches, key = { it.id }) { match ->
                MatchCard(
                    match = match,
                    onClick = { onNavigateToMatch(match.id) },
                    modifier = Modifier.fillMaxWidth().testTag("match_item_${match.id}")
                )
            }
        }
    }

    if (showGuestDialog) {
        com.example.ui.components.GuestRestrictedDialog(
            actionName = "নতুন ম্যাচ তৈরি ও স্কোরিং করতে",
            container = container,
            onDismiss = { showGuestDialog = false },
            onNavigateToAuth = {
                showGuestDialog = false
                onNavigateToAuth()
            }
        )
    }
}
