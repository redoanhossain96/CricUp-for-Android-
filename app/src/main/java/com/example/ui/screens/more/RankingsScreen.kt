package com.example.ui.screens.more

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Player
import com.example.data.model.Team
import com.example.di.AppContainer
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.adaptiveEmeraldColor
import com.example.ui.theme.adaptiveGoldColor
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingsScreen(
    container: AppContainer,
    onBack: () -> Unit
) {
    val currentUser by remember(container) { container.authRepository.getCurrentUserFlow() }.collectAsState()
    val currentUserId = currentUser?.uid?.takeIf { it.isNotBlank() }
        ?: remember(container) { container.authRepository.getCurrentUserId() }
    val players by remember(currentUserId) {
        container.playerRepository.getMyPlayersFlow(currentUserId)
    }.collectAsState(initial = emptyList())
    val teams by remember(currentUserId) {
        container.teamRepository.getMyTeamsFlow(currentUserId)
    }.collectAsState(initial = emptyList())

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Top Batters", "Top Bowlers", "Most Sixes", "Team Standings")

    val goldColor = adaptiveGoldColor()
    val emeraldColor = adaptiveEmeraldColor()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Player & Team Statistics",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = goldColor,
                edgePadding = 8.dp,
                divider = {},
                indicator = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 6.dp)
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                            ),
                            shadowElevation = if (isSelected) 2.dp else 0.dp
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    val filtered = players.filter { it.stats.runs > 0 || it.stats.ballsFaced > 0 || it.stats.matches > 0 }
                        .sortedByDescending { it.stats.runs }
                    if (filtered.isEmpty()) {
                        EmptyStatsView(message = "এখনো কোনো ব্যাটারের পরিসংখ্যান নেই।\nনতুন ম্যাচ স্কোরিং শুরু করলে ব্যাটারদের রান ও স্ট্রাইক রেট এখানে দেখতে পাবেন।")
                    } else {
                        RankingsList(filtered) { p -> "${p.stats.runs} Runs (SR: ${String.format(Locale.US, "%.1f", p.stats.strikeRate)})" }
                    }
                }
                1 -> {
                    val filtered = players.filter { it.stats.wickets > 0 || it.stats.oversBowled > 0 }
                        .sortedByDescending { it.stats.wickets }
                    if (filtered.isEmpty()) {
                        EmptyStatsView(message = "এখনো কোনো বোলারের পরিসংখ্যান নেই।\nনতুন ম্যাচে বল ও উইকেট এন্ট্রি দিলে বোলারদের উইকেট ও ইকোনমি এখানে দেখতে পাবেন।")
                    } else {
                        RankingsList(filtered) { p -> "${p.stats.wickets} Wkts (Econ: ${String.format(Locale.US, "%.1f", p.stats.bowlingEconomy)})" }
                    }
                }
                2 -> {
                    val filtered = players.filter { it.stats.sixes > 0 || it.stats.fours > 0 }
                        .sortedByDescending { it.stats.sixes }
                    if (filtered.isEmpty()) {
                        EmptyStatsView(message = "এখনো কোনো বাউন্ডারি রেকর্ড নেই।\nম্যাচে ৪ ও ৬ হাঁকালে বাউন্ডারি লিডারবোর্ড তৈরি হবে।")
                    } else {
                        RankingsList(filtered) { p -> "${p.stats.sixes} Sixes, ${p.stats.fours} Fours" }
                    }
                }
                3 -> {
                    val filtered = teams.filter { it.matchesPlayed > 0 }
                        .sortedByDescending { it.matchesWon }
                    if (filtered.isEmpty()) {
                        EmptyStatsView(message = "এখনো কোনো দলের ম্যাচ রেকর্ড নেই।\nম্যাচ সমাপ্ত হলে দলের জয়-পরাজয় ও পয়েন্ট টেবিল এখানে দেখতে পাবেন।")
                    } else {
                        TeamRankingsList(filtered)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStatsView(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("📊", fontSize = 28.sp)
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "কোনো পরিসংখ্যান নেই (No Records)",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun RankingsList(players: List<Player>, statText: (Player) -> String) {
    val goldColor = adaptiveGoldColor()
    val emeraldColor = adaptiveEmeraldColor()
    LazyColumn(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        itemsIndexed(players) { index, player ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (index == 0) GoldYellow else emeraldColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#${index + 1}",
                            color = if (index == 0) Color.Black else emeraldColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(player.fullName, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(player.primaryRole, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                    Text(statText(player), color = goldColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TeamRankingsList(teams: List<Team>) {
    val goldColor = adaptiveGoldColor()
    val emeraldColor = adaptiveEmeraldColor()
    LazyColumn(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        itemsIndexed(teams) { index, team ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#${index + 1}", color = goldColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(team.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Played: ${team.matchesPlayed} • Home: ${team.homeGround}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                    Text("Won ${team.matchesWon}", color = emeraldColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
