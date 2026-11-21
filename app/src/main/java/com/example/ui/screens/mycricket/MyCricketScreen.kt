package com.example.ui.screens.mycricket

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Player
import com.example.data.model.PlayerRoles
import com.example.data.model.PlayerStats
import com.example.data.model.Team
import com.example.data.model.Tournament
import com.example.data.model.Umpire
import com.example.di.AppContainer
import com.example.ui.components.CricUpAvatar
import com.example.ui.components.MatchCard
import com.example.ui.theme.CardBorder
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.SlateCardBg
import com.example.ui.theme.SurfaceDarkNavy
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.theme.WicketRed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.example.ui.components.FullPlayerProfileDialog
import com.example.ui.components.TeamSquadBuilderDialog
import com.example.ui.theme.CricUpIcons
import com.example.ui.theme.adaptiveGoldColor
import kotlinx.coroutines.launch

@Composable
fun MyCricketScreen(
    initialTab: Int = 0,
    container: AppContainer,
    onNavigateToMatch: (String) -> Unit,
    onNavigateToCreateMatch: () -> Unit,
    onNavigateToAuth: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentUser by remember(container) { container.authRepository.getCurrentUserFlow() }.collectAsState()
    val currentUserId = currentUser?.uid?.takeIf { it.isNotBlank() }
        ?: container.authRepository.getCurrentUserId()
    val isGuest by container.authRepository.isGuestMode.collectAsState()
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf("My Matches", "My Tournaments", "My Teams", "My Players", "My Umpires", "Scorer Assignments")

    var showGuestDialog by remember { mutableStateOf(false) }
    var guestAttemptedAction by remember { mutableStateOf("অ্যাক্সেস") }

    fun checkGuest(actionName: String, action: () -> Unit) {
        if (isGuest) {
            guestAttemptedAction = actionName
            showGuestDialog = true
        } else {
            action()
        }
    }

    val myMatchesFlow = remember(currentUserId) { container.matchRepository.getMyMatchesFlow(currentUserId) }
    val myTeamsFlow = remember(currentUserId) { container.teamRepository.getMyTeamsFlow(currentUserId) }
    val myPlayersFlow = remember(currentUserId) { container.playerRepository.getMyPlayersFlow(currentUserId) }
    val myTournamentsFlow = remember(currentUserId) { container.tournamentRepository.getMyTournamentsFlow(currentUserId) }
    val myUmpiresFlow = remember(currentUserId) { container.umpireRepository.getMyUmpiresFlow(currentUserId) }

    val allMatches by myMatchesFlow.collectAsState(initial = emptyList())
    val allTeams by myTeamsFlow.collectAsState(initial = emptyList())
    val allPlayers by myPlayersFlow.collectAsState(initial = emptyList())
    val allTournaments by myTournamentsFlow.collectAsState(initial = emptyList())
    val allUmpires by myUmpiresFlow.collectAsState(initial = emptyList())

    var showCreateTeamDialog by remember { mutableStateOf(false) }
    var showCreatePlayerDialog by remember { mutableStateOf(false) }
    var showCreateTournamentDialog by remember { mutableStateOf(false) }
    var showCreateUmpireDialog by remember { mutableStateOf(false) }
    var showDuplicatePlayerWarning by remember { mutableStateOf(false) }
    var pendingPlayerToSave by remember { mutableStateOf<Player?>(null) }

    val primaryTint = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val onSurface = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Crisp Bordered Pill Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = primaryTint,
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
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) primaryTint else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) primaryTint else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                            ),
                            shadowElevation = if (isSelected) 2.dp else 0.dp
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else onSurface,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Viewer Mode Banner (Read-only for unregistered non-Gmail users)
            if (isGuest) {
                com.example.ui.components.GuestVisitorBanner(
                    onNavigateToAuth = { checkGuest("অ্যাপের সকল ফিচার ব্যবহার করতে") {} },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> MyMatchesList(allMatches, onNavigateToMatch, onNavigateToCreateMatch = { checkGuest("ম্যাচ তৈরি ও স্কোরিং করতে") { onNavigateToCreateMatch() } })
                1 -> MyTournamentsList(
                    tournaments = allTournaments,
                    allTeams = allTeams,
                    allPlayers = allPlayers,
                    container = container,
                    currentUserId = currentUserId,
                    isGuest = isGuest,
                    onRequireAuth = { action -> checkGuest(action) {} },
                    onCreateTournament = { checkGuest("টুর্নামেন্ট তৈরি করতে") { showCreateTournamentDialog = true } }
                )
                2 -> MyTeamsList(
                    teams = allTeams,
                    container = container,
                    isGuest = isGuest,
                    onRequireAuth = { action -> checkGuest(action) {} },
                    onAddTeam = { checkGuest("দল তৈরি করতে") { showCreateTeamDialog = true } }
                )
                3 -> MyPlayersList(
                    players = allPlayers,
                    container = container,
                    isGuest = isGuest,
                    onRequireAuth = { action -> checkGuest(action) {} },
                    onAddPlayer = { checkGuest("খেলোয়াড় যোগ করতে") { showCreatePlayerDialog = true } }
                )
                4 -> MyUmpiresList(
                    umpires = allUmpires,
                    container = container,
                    isGuest = isGuest,
                    onRequireAuth = { action -> checkGuest(action) {} },
                    onAddUmpire = { checkGuest("আম্পায়ার যোগ করতে") { showCreateUmpireDialog = true } }
                )
                5 -> ScorerAssignmentsList(allMatches, currentUserId, onNavigateToMatch)
            }
        }
    }

    // CREATE TEAM DIALOG (WITH FULL-DETAIL PLAYER ENTRY, DATABASE PICKER & SQUAD BUILDER)
    if (showCreateTeamDialog) {
        var teamName by remember { mutableStateOf("") }
        var shortName by remember { mutableStateOf("") }
        var homeGround by remember { mutableStateOf("") }
        var contactInfo by remember { mutableStateOf("") }
        var teamLogoUrl by remember { mutableStateOf<String?>(null) }
        var pendingPlayerName by remember { mutableStateOf("") }
        val squadDraftPlayers = remember { mutableStateListOf<Player>() }
        var showFullPlayerSubDialog by remember { mutableStateOf(false) }
        var editingDraftPlayer by remember { mutableStateOf<Player?>(null) }
        var showDbPickerSubDialog by remember { mutableStateOf(false) }

        val teamLogoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val encoded = com.example.util.ImagePickerHelper.compressUriToBase64Async(context, uri)
                    if (encoded != null) {
                        teamLogoUrl = encoded
                    } else {
                        Toast.makeText(context, "ছবি প্রসেস করা যায়নি, অন্য ছবি চেষ্টা করুন", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showCreateTeamDialog = false },
            title = { Text("নতুন দল ও স্কোয়াড তৈরি করুন", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp)) {
                    item {
                        Text("দলের তথ্য ও পূর্ণ বিবরণসহ খেলোয়াড় যুক্ত করে স্কোয়াড গঠন করুন।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.5.dp, if (teamLogoUrl != null) EmeraldGreen else MaterialTheme.colorScheme.outline, CircleShape)
                                    .clickable {
                                        teamLogoPicker.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (!teamLogoUrl.isNullOrEmpty()) {
                                    val resolvedTeamLogo = remember(teamLogoUrl) { com.example.util.ImageModelResolver.resolveForCoil(teamLogoUrl) }
                                    coil.compose.AsyncImage(
                                        model = resolvedTeamLogo,
                                        contentDescription = "Team Logo",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = CricUpIcons.SportsCricket,
                                            contentDescription = "Add Team Logo",
                                            tint = EmeraldGreen,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text("+লোগো", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = teamName,
                                    onValueChange = { teamName = it },
                                    label = { Text("Team Name (দলের নাম) *") },
                                    placeholder = { Text("দলের নাম লিখুন") },
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                                    modifier = Modifier.fillMaxWidth().testTag("team_name_input")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = shortName,
                            onValueChange = { shortName = it.take(4).uppercase() },
                            label = { Text("Short Code (সংক্ষিপ্ত কোড, যেমন: DHG) *") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = homeGround,
                            onValueChange = { homeGround = it },
                            label = { Text("Home Ground / Stadium (মাঠ)") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = contactInfo,
                            onValueChange = { contactInfo = it },
                            label = { Text("Contact Phone / Info (যোগাযোগ)") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("দলের স্কোয়াড গঠন (Squad Players):", color = adaptiveGoldColor(), fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Text("পূর্ণ বিবরণসহ নতুন খেলোয়াড় যোগ করুন অথবা ডেটাবেজ থেকে নির্বাচন করুন।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { showFullPlayerSubDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f).testTag("create_team_add_full_player_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = DeepNavyBg, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("পূর্ণ বিবরণসহ", color = DeepNavyBg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { showDbPickerSubDialog = true },
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldYellow),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f).testTag("create_team_pick_db_player_btn")
                            ) {
                                Icon(CricUpIcons.Group, contentDescription = null, tint = adaptiveGoldColor(), modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("ডেটাবেজ (${allPlayers.size})", color = adaptiveGoldColor(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = pendingPlayerName,
                                onValueChange = { pendingPlayerName = it },
                                label = { Text("দ্রুত নাম লিখুন (ঐচ্ছিক)") },
                                placeholder = { Text("যেমন: সাকিব, তামিম...") },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val trimmed = pendingPlayerName.trim()
                                    if (trimmed.isNotBlank()) {
                                        if (!squadDraftPlayers.any { it.fullName.equals(trimmed, ignoreCase = true) }) {
                                            val existing = allPlayers.find { it.fullName.trim().equals(trimmed, ignoreCase = true) }
                                            squadDraftPlayers.add(
                                                existing ?: Player(
                                                    id = java.util.UUID.randomUUID().toString(),
                                                    fullName = trimmed,
                                                    jerseyNumber = squadDraftPlayers.size + 1,
                                                    primaryRole = PlayerRoles.ALL_ROUNDER,
                                                    creatorId = currentUserId
                                                )
                                            )
                                            pendingPlayerName = ""
                                        } else {
                                            Toast.makeText(context, "এই খেলোয়াড় তালিকায় ইতিমধ্যেই যোগ করা হয়েছে", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("+ যোগ", color = EmeraldGreen)
                            }
                        }

                        if (squadDraftPlayers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("অন্তর্ভুক্ত স্কোয়াড (${squadDraftPlayers.size} জন):", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                squadDraftPlayers.forEach { dp ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                            CricUpAvatar(model = dp.photoUrl, size = 32.dp, fallbackText = dp.fullName.take(2))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(dp.fullName, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                Text(
                                                    "${dp.primaryRole} • #${dp.jerseyNumber} • ${dp.battingStyle}",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                        Row {
                                            IconButton(
                                                onClick = { editingDraftPlayer = dp },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Player", tint = adaptiveGoldColor(), modifier = Modifier.size(15.dp))
                                            }
                                            IconButton(
                                                onClick = { squadDraftPlayers.removeAll { it.id == dp.id } },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = WicketRed, modifier = Modifier.size(15.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (teamName.isBlank()) {
                            Toast.makeText(context, "দলের নাম লিখুন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val finalShort = if (shortName.isBlank()) teamName.take(3).uppercase() else shortName.trim()
                        val newTeamId = java.util.UUID.randomUUID().toString()
                        scope.launch {
                            val savedIds = mutableListOf<String>()
                            squadDraftPlayers.forEach { p ->
                                val linked = p.copy(teamIds = (p.teamIds + newTeamId).distinct())
                                container.playerRepository.savePlayer(linked)
                                savedIds.add(linked.id)
                            }
                            val newTeam = Team(
                                id = newTeamId,
                                name = teamName.trim(),
                                shortName = finalShort,
                                logoUrl = teamLogoUrl,
                                homeGround = homeGround.trim(),
                                contactInfo = contactInfo.trim(),
                                playerIds = savedIds.distinct(),
                                ownerId = currentUserId
                            )
                            container.teamRepository.saveTeam(newTeam)
                            showCreateTeamDialog = false
                            Toast.makeText(context, "দল ও ${savedIds.size} জনের স্কোয়াড সফলভাবে তৈরি ও সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("Save Team & Squad")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTeamDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )

        if (showFullPlayerSubDialog) {
            FullPlayerProfileDialog(
                initialPlayer = null,
                defaultJerseyNumber = squadDraftPlayers.size + 1,
                existingPlayers = allPlayers + squadDraftPlayers,
                creatorId = currentUserId,
                onDismiss = { showFullPlayerSubDialog = false },
                onConfirm = { player ->
                    if (!squadDraftPlayers.any { it.fullName.equals(player.fullName, ignoreCase = true) }) {
                        squadDraftPlayers.add(player)
                    }
                    showFullPlayerSubDialog = false
                }
            )
        }

        editingDraftPlayer?.let { dp ->
            FullPlayerProfileDialog(
                initialPlayer = dp,
                existingPlayers = allPlayers.filter { it.id != dp.id } + squadDraftPlayers.filter { it.id != dp.id },
                creatorId = currentUserId,
                onDismiss = { editingDraftPlayer = null },
                onConfirm = { updated ->
                    val idx = squadDraftPlayers.indexOfFirst { it.id == dp.id }
                    if (idx >= 0) squadDraftPlayers[idx] = updated
                    editingDraftPlayer = null
                }
            )
        }

        if (showDbPickerSubDialog) {
            var dbSearch by remember { mutableStateOf("") }
            val pickedIds = remember { mutableStateListOf<String>() }
            val available = allPlayers.filter { p -> squadDraftPlayers.none { it.id == p.id } }
            val filtered = available.filter {
                dbSearch.isBlank() ||
                    it.fullName.contains(dbSearch.trim(), ignoreCase = true) ||
                    it.primaryRole.contains(dbSearch.trim(), ignoreCase = true)
            }
            AlertDialog(
                onDismissRequest = { showDbPickerSubDialog = false },
                title = { Text("ডেটাবেজ থেকে খেলোয়াড় নির্বাচন", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                        OutlinedTextField(
                            value = dbSearch,
                            onValueChange = { dbSearch = it },
                            label = { Text("খেলোয়াড় খুঁজুন") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = EmeraldGreen) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (filtered.isEmpty()) {
                            Text("উপলব্ধ কোনো খেলোয়াড় নেই", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(filtered, key = { it.id }) { p ->
                                    val checked = pickedIds.contains(p.id)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (checked) EmeraldGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                            .clickable { if (checked) pickedIds.remove(p.id) else pickedIds.add(p.id) }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = { if (it) pickedIds.add(p.id) else pickedIds.remove(p.id) },
                                            colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen, checkmarkColor = DeepNavyBg)
                                        )
                                        CricUpAvatar(model = p.photoUrl, size = 34.dp, fallbackText = p.fullName.take(2))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(p.fullName, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                            Text("${p.primaryRole} • #${p.jerseyNumber} • ${p.battingStyle}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val chosen = available.filter { pickedIds.contains(it.id) }
                            squadDraftPlayers.addAll(chosen)
                            showDbPickerSubDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) {
                        Text("যোগ করুন (${pickedIds.size})", color = DeepNavyBg, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDbPickerSubDialog = false }) {
                        Text("বাতিল", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }

    // CREATE PLAYER DIALOG (FULL DETAILS)
    if (showCreatePlayerDialog) {
        FullPlayerProfileDialog(
            initialPlayer = null,
            defaultJerseyNumber = allPlayers.size + 1,
            existingPlayers = allPlayers,
            creatorId = currentUserId,
            onDismiss = { showCreatePlayerDialog = false },
            onConfirm = { player ->
                scope.launch {
                    container.playerRepository.savePlayer(player)
                    showCreatePlayerDialog = false
                    Toast.makeText(context, "খেলোয়াড় সফলভাবে যোগ করা হয়েছে!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // DUPLICATE PLAYER WARNING DIALOG
    val duplicateCandidate = if (showDuplicatePlayerWarning) pendingPlayerToSave else null
    if (duplicateCandidate != null) {
        val player = duplicateCandidate
        AlertDialog(
            onDismissRequest = {
                showDuplicatePlayerWarning = false
                pendingPlayerToSave = null
            },
            title = {
                Text("⚠️ Duplicate Player Name Detected", color = adaptiveGoldColor(), fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "A player named \"${player.fullName}\" already exists in your player database.",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Are you sure you want to add another player with this identical name? (Unique ID will be assigned to distinguish them in matches).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            container.playerRepository.savePlayer(player)
                            showDuplicatePlayerWarning = false
                            pendingPlayerToSave = null
                            showCreatePlayerDialog = false
                            Toast.makeText(context, "Player profile registered successfully!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("Confirm & Add")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDuplicatePlayerWarning = false
                    pendingPlayerToSave = null
                }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // CREATE UMPIRE DIALOG
    if (showCreateUmpireDialog) {
        var umpireName by remember { mutableStateOf("") }
        var association by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var umpirePhotoUrl by remember { mutableStateOf<String?>(null) }
        var certification by remember { mutableStateOf("Club / Local") }

        val umpirePhotoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val encoded = com.example.util.ImagePickerHelper.compressUriToBase64Async(context, uri)
                    if (encoded != null) {
                        umpirePhotoUrl = encoded
                    } else {
                        Toast.makeText(context, "ছবি প্রসেস করা যায়নি, অন্য ছবি চেষ্টা করুন", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showCreateUmpireDialog = false },
            title = { Text("Register Official Umpire", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Registered umpires are saved for selection in future matches.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.5.dp, if (umpirePhotoUrl != null) EmeraldGreen else MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable {
                                    umpirePhotoPicker.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!umpirePhotoUrl.isNullOrEmpty()) {
                                val resolvedUmpirePhoto = remember(umpirePhotoUrl) { com.example.util.ImageModelResolver.resolveForCoil(umpirePhotoUrl) }
                                coil.compose.AsyncImage(
                                    model = resolvedUmpirePhoto,
                                    contentDescription = "Umpire Photo",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Add Photo",
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text("+ছবি", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = umpireName,
                                onValueChange = { umpireName = it },
                                label = { Text("Umpire Full Name *") },
                                placeholder = { Text("e.g. Richard Kettleborough") },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                                modifier = Modifier.fillMaxWidth().testTag("umpire_name_input")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = association,
                        onValueChange = { association = it },
                        label = { Text("Association / League / City") },
                        placeholder = { Text("e.g. Dhaka Cricket Umpires Assoc.") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Contact Number (Optional)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Certification Level:", color = adaptiveGoldColor(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Local", "Level 1", "Level 2", "ICC/National").forEach { lvl ->
                            Button(
                                onClick = { certification = lvl },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (certification == lvl) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(lvl, fontSize = 9.sp, color = if (certification == lvl) DeepNavyBg else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (umpireName.isBlank()) {
                            Toast.makeText(context, "Please enter umpire name", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val umpire = Umpire(
                            fullName = umpireName.trim(),
                            photoUrl = umpirePhotoUrl,
                            association = association.trim(),
                            phone = phone.trim(),
                            certificationLevel = certification,
                            creatorId = currentUserId
                        )
                        scope.launch {
                            container.umpireRepository.saveUmpire(umpire)
                            showCreateUmpireDialog = false
                            Toast.makeText(context, "Umpire profile registered!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("Save Umpire")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateUmpireDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // CREATE TOURNAMENT DIALOG
    if (showCreateTournamentDialog) {
        var tName by remember { mutableStateOf("") }
        var tVenue by remember { mutableStateOf("") }
        var tFormat by remember { mutableStateOf("League") }
        var tOvers by remember { mutableStateOf("20") }
        var tLogoUrl by remember { mutableStateOf<String?>(null) }
        var isCompressingLogo by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreateTournamentDialog = false },
            title = { Text("Create Tournament", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TournamentLogoPickerRow(
                        logoUrl = tLogoUrl,
                        fallbackName = tName.ifBlank { "TR" },
                        isProcessing = isCompressingLogo,
                        onProcessingChange = { isCompressingLogo = it },
                        onLogoPicked = { tLogoUrl = it },
                        onLogoRemoved = { tLogoUrl = null }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tName,
                        onValueChange = { tName = it },
                        label = { Text("Tournament Name") },
                        placeholder = { Text("e.g. CricUp Premier Cup 2026") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tVenue,
                        onValueChange = { tVenue = it },
                        label = { Text("Venue / Region") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tOvers,
                        onValueChange = { tOvers = it },
                        label = { Text("Overs per Match") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tName.isBlank() || isCompressingLogo) return@Button
                        val t = Tournament(
                            name = tName.trim(),
                            venue = tVenue.trim(),
                            matchOvers = tOvers.toIntOrNull() ?: 20,
                            logoUrl = tLogoUrl,
                            creatorId = currentUserId
                        )
                        scope.launch {
                            container.tournamentRepository.saveTournament(t)
                            showCreateTournamentDialog = false
                            Toast.makeText(context, "Tournament published!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("Publish Tournament")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTournamentDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showGuestDialog) {
        com.example.ui.components.GuestRestrictedDialog(
            actionName = guestAttemptedAction,
            container = container,
            onDismiss = { showGuestDialog = false },
            onNavigateToAuth = {
                showGuestDialog = false
                onNavigateToAuth()
            }
        )
    }
}

@Composable
private fun MyMatchesList(
    matches: List<com.example.data.model.Match>,
    onNavigateToMatch: (String) -> Unit,
    onNavigateToCreateMatch: () -> Unit
) {
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(14.dp)) {
        item {
            Button(
                onClick = onNavigateToCreateMatch,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = DeepNavyBg)
                Spacer(modifier = Modifier.width(6.dp))
                Text("CREATE NEW MATCH", color = DeepNavyBg, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (matches.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("You have not created any matches yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(matches, key = { it.id }) { match ->
                MatchCard(match = match, onClick = { onNavigateToMatch(match.id) })
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun MyTeamsList(
    teams: List<Team>,
    container: AppContainer,
    isGuest: Boolean,
    onRequireAuth: (String) -> Unit,
    onAddTeam: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentUser by container.authRepository.getCurrentUserFlow().collectAsState()
    val currentUserId = currentUser?.uid?.takeIf { it.isNotBlank() }
        ?: container.authRepository.getCurrentUserId()
    val allPlayers by remember(currentUserId) {
        container.playerRepository.getMyPlayersFlow(currentUserId)
    }.collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    val filteredTeams = remember(teams, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) teams
        else teams.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.shortName.contains(q, ignoreCase = true) ||
                it.homeGround.contains(q, ignoreCase = true)
        }
    }

    var editingTeam by remember { mutableStateOf<Team?>(null) }
    var deletingTeam by remember { mutableStateOf<Team?>(null) }
    var squadManagerTeam by remember { mutableStateOf<Team?>(null) }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(14.dp)) {
        item {
            Button(
                onClick = onAddTeam,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_new_team_btn"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Icon(imageVector = CricUpIcons.Group, contentDescription = null, tint = DeepNavyBg)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "নতুন দল যোগ করুন (Add Team)",
                    color = DeepNavyBg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            MyCricketSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "দলের নাম বা কোড দিয়ে খুঁজুন...",
                testTag = "my_teams_search_input"
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (filteredTeams.isEmpty()) {
            item {
                if (searchQuery.isNotBlank()) {
                    MyCricketEmptySearchCard(
                        query = searchQuery,
                        entityLabel = "দল"
                    )
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = CricUpIcons.Group,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "আপনার নিজস্ব কোনো দল নেই",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "নতুন দল তৈরি করতে উপরের \"নতুন দল যোগ করুন (Add Team)\" বাটনে ট্যাপ করুন। আপনার Gmail অ্যাকাউন্টে সকল দল সুরক্ষিতভাবে সংরক্ষিত থাকবে।",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "আপনার দল (${filteredTeams.size})",
                    color = EmeraldGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(filteredTeams, key = { it.id }) { team ->
                val teamSquadPlayers = remember(team, allPlayers) {
                    allPlayers.filter { team.playerIds.contains(it.id) || it.teamIds.contains(team.id) }
                        .distinctBy { it.id }
                }
                val isOwner = team.ownerId == currentUserId || team.ownerId.isBlank()
                val isJoinedMember = currentUser?.joinedTeamIds?.contains(team.id) == true ||
                    team.playerIds.contains(currentUserId)

                MyTeamManagementCard(
                    team = team,
                    squadCount = teamSquadPlayers.size,
                    statusBadge = when {
                        isOwner -> "আপনার দল ✓"
                        isJoinedMember -> "সদস্য ✓"
                        else -> null
                    },
                    isJoinedOrOwned = true,
                    canLeave = !isOwner && isJoinedMember,
                    onEdit = {
                        if (isGuest) onRequireAuth("দল এডিট করতে")
                        else editingTeam = team
                    },
                    onDelete = {
                        if (isGuest) onRequireAuth("দল মুছে ফেলতে")
                        else deletingTeam = team
                    },
                    onManageSquad = {
                        if (isGuest) onRequireAuth("দলের স্কোয়াড ম্যানেজ করতে")
                        else squadManagerTeam = team
                    },
                    onJoin = {},
                    onLeave = {
                        if (isGuest) {
                            onRequireAuth("দলের সদস্যপদ পরিবর্তন করতে")
                        } else {
                            scope.launch {
                                container.toggleUserTeamMembership(team.id, join = false)
                                Toast.makeText(context, "${team.name} থেকে বের হয়েছেন", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }

    // FULL TEAM SQUAD BUILDER DIALOG
    squadManagerTeam?.let { activeTeam ->
        val latestTeam = teams.find { it.id == activeTeam.id } ?: activeTeam
        TeamSquadBuilderDialog(
            team = latestTeam,
            allPlayers = allPlayers,
            container = container,
            onDismiss = { squadManagerTeam = null }
        )
    }

    // EDIT TEAM DIALOG
    editingTeam?.let { team ->
        var editName by remember { mutableStateOf(team.name) }
        var editShortName by remember { mutableStateOf(team.shortName) }
        var editHomeGround by remember { mutableStateOf(team.homeGround) }
        var editContact by remember { mutableStateOf(team.contactInfo) }
        var editLogoUrl by remember { mutableStateOf(team.logoUrl) }
        val initialMemberIds = remember(team.id) {
            (team.playerIds + allPlayers.filter { it.teamIds.contains(team.id) }.map { it.id }).distinct()
        }
        var teamPlayerIds by remember { mutableStateOf(initialMemberIds) }
        var showFullPlayerInEdit by remember { mutableStateOf(false) }

        val editTeamLogoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val encoded = com.example.util.ImagePickerHelper.compressUriToBase64Async(context, uri)
                    if (encoded != null) {
                        editLogoUrl = encoded
                    } else {
                        Toast.makeText(context, "ছবি প্রসেস করা যায়নি, অন্য ছবি চেষ্টা করুন", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { editingTeam = null },
            title = { Text("দলের তথ্য ও স্কোয়াড সম্পাদনা", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.5.dp, if (editLogoUrl != null) EmeraldGreen else MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable {
                                    editTeamLogoPicker.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!editLogoUrl.isNullOrEmpty()) {
                                val resolvedEditLogo = remember(editLogoUrl) { com.example.util.ImageModelResolver.resolveForCoil(editLogoUrl) }
                                coil.compose.AsyncImage(
                                    model = resolvedEditLogo,
                                    contentDescription = "Team Logo",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = CricUpIcons.SportsCricket,
                                        contentDescription = "Add Logo",
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text("+লোগো", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("দলের লোগো পরিবর্তন (ঐচ্ছিক)", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("লোগো পরিবর্তন করতে ছবিতে ট্যাপ করুন", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            if (editLogoUrl != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "লোগো সরান (Remove)",
                                    color = WicketRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { editLogoUrl = null }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Team Name (দলের নাম)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editShortName,
                        onValueChange = { editShortName = it.take(4).uppercase() },
                        label = { Text("Short Code (সংক্ষিপ্ত কোড)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editHomeGround,
                        onValueChange = { editHomeGround = it },
                        label = { Text("Home Ground (হোম ভেন্যু)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editContact,
                        onValueChange = { editContact = it },
                        label = { Text("Contact Info (যোগাযোগ)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "দলের স্কোয়াড (${teamPlayerIds.size} জন)",
                            color = adaptiveGoldColor(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            TextButton(onClick = { showFullPlayerInEdit = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("+ পূর্ণ খেলোয়াড়", color = EmeraldGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = {
                                editingTeam = null
                                squadManagerTeam = team
                            }) {
                                Text("স্কোয়াড ম্যানেজার", color = adaptiveGoldColor(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    val assignedPlayers = allPlayers.filter { teamPlayerIds.contains(it.id) }
                    if (assignedPlayers.isEmpty()) {
                        Text("দলে এখনও কোনো খেলোয়াড় যুক্ত নেই। উপরের '+ পূর্ণ খেলোয়াড়' বা 'স্কোয়াড ম্যানেজার' চাপুন।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            assignedPlayers.forEach { p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        CricUpAvatar(model = p.photoUrl, size = 34.dp, fallbackText = p.fullName.take(2))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(p.fullName, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                            Text("${p.primaryRole} • #${p.jerseyNumber} • ${p.battingStyle}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            teamPlayerIds = teamPlayerIds.filter { it != p.id }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = WicketRed, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isBlank() || editShortName.isBlank()) {
                            Toast.makeText(context, "Please enter team name and short code", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val updated = team.copy(
                            name = editName.trim(),
                            shortName = editShortName.trim(),
                            homeGround = editHomeGround.trim(),
                            contactInfo = editContact.trim(),
                            logoUrl = editLogoUrl,
                            playerIds = teamPlayerIds.distinct()
                        )
                        scope.launch {
                            // Sync removed players and added players cleanly
                            val removedIds = initialMemberIds.filter { !teamPlayerIds.contains(it) }
                            removedIds.forEach { rid ->
                                allPlayers.find { it.id == rid }?.let { rp ->
                                    container.playerRepository.savePlayer(
                                        rp.copy(teamIds = rp.teamIds.filter { it != team.id })
                                    )
                                }
                            }
                            teamPlayerIds.forEach { aid ->
                                allPlayers.find { it.id == aid }?.let { ap ->
                                    if (!ap.teamIds.contains(team.id)) {
                                        container.playerRepository.savePlayer(
                                            ap.copy(teamIds = (ap.teamIds + team.id).distinct())
                                        )
                                    }
                                }
                            }
                            container.teamRepository.saveTeam(updated)
                            editingTeam = null
                            Toast.makeText(context, "দলের তথ্য ও স্কোয়াড সফলভাবে আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("আপডেট করুন (Update)")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTeam = null }) {
                    Text("বাতিল (Cancel)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )

        if (showFullPlayerInEdit) {
            FullPlayerProfileDialog(
                initialPlayer = null,
                defaultJerseyNumber = teamPlayerIds.size + 1,
                existingPlayers = allPlayers,
                creatorId = container.authRepository.getCurrentUserId(),
                attachedTeamIds = listOf(team.id),
                onDismiss = { showFullPlayerInEdit = false },
                onConfirm = { newP ->
                    scope.launch {
                        container.playerRepository.savePlayer(newP)
                        teamPlayerIds = (teamPlayerIds + newP.id).distinct()
                        showFullPlayerInEdit = false
                        Toast.makeText(context, "${newP.fullName} তৈরি ও স্কোয়াডে যোগ করা হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    // DELETE TEAM CONFIRMATION DIALOG
    deletingTeam?.let { team ->
        AlertDialog(
            onDismissRequest = { deletingTeam = null },
            title = { Text("দল মুছে ফেলবেন?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Text("আপনি কি নিশ্চিতভাবে '${team.name}' দলটি মুছে ফেলতে চান?", color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            container.teamRepository.deleteTeam(team.id)
                            if (editingTeam?.id == team.id) editingTeam = null
                            if (squadManagerTeam?.id == team.id) squadManagerTeam = null
                            deletingTeam = null
                            Toast.makeText(context, "দল সফলভাবে মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WicketRed)
                ) {
                    Text("মুছে ফেলুন (Delete)")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTeam = null }) {
                    Text("বাতিল", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun MyTeamManagementCard(
    team: Team,
    squadCount: Int,
    statusBadge: String?,
    isJoinedOrOwned: Boolean,
    canLeave: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onManageSquad: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("team_card_${team.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isJoinedOrOwned) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CricUpAvatar(
                        model = team.logoUrl,
                        size = 48.dp,
                        fallbackText = team.shortName.take(3),
                        borderColor = EmeraldGreen
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = team.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${team.shortName} • ${team.homeGround.ifBlank { "Home Ground" }}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = "👥 স্কোয়াড: $squadCount জন" + (statusBadge?.let { " • $it" } ?: ""),
                            color = EmeraldGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("edit_team_btn_${team.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Team",
                            tint = adaptiveGoldColor()
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_team_btn_${team.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Team",
                            tint = WicketRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onManageSquad,
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .testTag("team_squad_btn_${team.id}")
                ) {
                    Icon(
                        imageVector = CricUpIcons.Group,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "স্কোয়াড",
                        color = EmeraldGreen,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!isJoinedOrOwned) {
                    Button(
                        onClick = onJoin,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .testTag("join_team_btn_${team.id}")
                    ) {
                        Text(
                            text = "যোগ দিন",
                            color = DeepNavyBg,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                } else if (canLeave) {
                    OutlinedButton(
                        onClick = onLeave,
                        border = androidx.compose.foundation.BorderStroke(1.dp, WicketRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .testTag("leave_team_btn_${team.id}")
                    ) {
                        Text(
                            text = "ছেড়ে দিন",
                            color = WicketRed,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MyPlayersList(
    players: List<Player>,
    container: AppContainer,
    isGuest: Boolean,
    onRequireAuth: (String) -> Unit,
    onAddPlayer: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var editingPlayer by remember { mutableStateOf<Player?>(null) }
    var deletingPlayer by remember { mutableStateOf<Player?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val distinctPlayers = remember(players) { players.distinctBy { it.fullName.trim().lowercase() } }
    val filteredPlayers = remember(distinctPlayers, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) distinctPlayers
        else distinctPlayers.filter {
            it.fullName.contains(q, ignoreCase = true) ||
                it.primaryRole.contains(q, ignoreCase = true) ||
                it.battingStyle.contains(q, ignoreCase = true) ||
                it.bowlingStyle.contains(q, ignoreCase = true) ||
                "#${it.jerseyNumber}".contains(q, ignoreCase = true)
        }
    }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(14.dp)) {
        item {
            Button(
                onClick = onAddPlayer,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = DeepNavyBg)
                Spacer(modifier = Modifier.width(6.dp))
                Text("ADD NEW PLAYER (নতুন খেলোয়াড় যোগ করুন)", color = DeepNavyBg, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            MyCricketSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "খেলোয়াড়ের নাম লিখে খুঁজুন...",
                testTag = "my_players_search_input"
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (filteredPlayers.isEmpty()) {
            item {
                if (searchQuery.isNotBlank()) {
                    MyCricketEmptySearchCard(
                        query = searchQuery,
                        entityLabel = "খেলোয়াড়"
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("কোনো খেলোয়াড় সংরক্ষিত নেই", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "খেলোয়াড় প্রোফাইল তৈরি করতে উপরের বাটনে চাপ দিন। যেকোনো সময় খেলোয়াড়ের নাম, ভূমিকা ও জার্সি নম্বর আপডেট করা যাবে।",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            items(filteredPlayers, key = { it.id }) { p ->
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
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, EmeraldGreen.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!p.photoUrl.isNullOrEmpty()) {
                                val resolvedPlayerPhoto = remember(p.photoUrl) { com.example.util.ImageModelResolver.resolveForCoil(p.photoUrl) }
                                coil.compose.AsyncImage(
                                    model = resolvedPlayerPhoto,
                                    contentDescription = p.fullName,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = com.example.data.model.PlayerRoles.getRoleIcon(p.primaryRole),
                                    fontSize = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.fullName, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("${p.primaryRole} • #${p.jerseyNumber} • ${p.battingStyle} • ${p.bowlingStyle}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            if (p.stats.matches > 0) {
                                Text("${p.stats.runs} Runs (HS: ${p.stats.highestScore}) | ${p.stats.wickets} Wickets", color = adaptiveGoldColor(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Row {
                            IconButton(
                                onClick = {
                                    if (isGuest) onRequireAuth("খেলোয়াড় এডিট করতে")
                                    else editingPlayer = p
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Player", tint = adaptiveGoldColor())
                            }
                            IconButton(
                                onClick = {
                                    if (isGuest) onRequireAuth("খেলোয়াড় মুছে ফেলতে")
                                    else deletingPlayer = p
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Player", tint = WicketRed)
                            }
                        }
                    }
                }
            }
        }
    }

    // EDIT PLAYER DIALOG (FULL DETAILS)
    editingPlayer?.let { p ->
        FullPlayerProfileDialog(
            initialPlayer = p,
            existingPlayers = players,
            creatorId = container.authRepository.getCurrentUserId(),
            onDismiss = { editingPlayer = null },
            onConfirm = { updated ->
                scope.launch {
                    container.playerRepository.savePlayer(updated)
                    editingPlayer = null
                    Toast.makeText(context, "খেলোয়াড় তথ্য সফলভাবে আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // DELETE PLAYER CONFIRMATION DIALOG
    deletingPlayer?.let { p ->
        AlertDialog(
            onDismissRequest = { deletingPlayer = null },
            title = { Text("খেলোয়াড় মুছে ফেলবেন?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Text("আপনি কি নিশ্চিতভাবে '${p.fullName}' খেলোয়াড়কে মুছে ফেলতে চান?", color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            container.playerRepository.deletePlayer(p.id)
                            deletingPlayer = null
                            Toast.makeText(context, "খেলোয়াড় সফলভাবে মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WicketRed)
                ) {
                    Text("মুছে ফেলুন (Delete)")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPlayer = null }) {
                    Text("বাতিল", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun MyTournamentsList(
    tournaments: List<Tournament>,
    allTeams: List<Team>,
    allPlayers: List<Player>,
    container: AppContainer,
    currentUserId: String,
    isGuest: Boolean,
    onRequireAuth: (String) -> Unit,
    onCreateTournament: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editingTournament by remember { mutableStateOf<Tournament?>(null) }
    var deletingTournament by remember { mutableStateOf<Tournament?>(null) }
    var tournamentForNewTeam by remember { mutableStateOf<Tournament?>(null) }
    var tournamentForExistingTeam by remember { mutableStateOf<Tournament?>(null) }
    var editingTeamInTournament by remember { mutableStateOf<Team?>(null) }
    var deletingTeamInTournament by remember { mutableStateOf<Pair<Tournament, Team>?>(null) }

    // Player management inside team in tournament
    var teamForNewPlayer by remember { mutableStateOf<Pair<Tournament, Team>?>(null) }
    var editingPlayerInTeam by remember { mutableStateOf<Player?>(null) }
    var deletingPlayerInTeam by remember { mutableStateOf<Pair<Team, Player>?>(null) }

    val expandedTeamIds = remember { mutableStateListOf<String>() }
    var searchQuery by remember { mutableStateOf("") }
    val filteredTournaments = remember(tournaments, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) tournaments
        else tournaments.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.organizer.contains(q, ignoreCase = true) ||
                it.venue.contains(q, ignoreCase = true) ||
                it.format.contains(q, ignoreCase = true)
        }
    }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(14.dp)) {
        item {
            Button(
                onClick = onCreateTournament,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Icon(imageVector = CricUpIcons.WorkspacePremium, contentDescription = null, tint = DeepNavyBg)
                Spacer(modifier = Modifier.width(6.dp))
                Text("CREATE TOURNAMENT (টুর্নামেন্ট তৈরি)", color = DeepNavyBg, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            MyCricketSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "টুর্নামেন্টের নাম লিখে খুঁজুন...",
                testTag = "my_tournaments_search_input"
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (filteredTournaments.isEmpty()) {
            item {
                if (searchQuery.isNotBlank()) {
                    MyCricketEmptySearchCard(
                        query = searchQuery,
                        entityLabel = "টুর্নামেন্ট"
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(imageVector = CricUpIcons.WorkspacePremium, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("কোনো টুর্নামেন্ট সংরক্ষিত নেই", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "আপনার টুর্নামেন্ট তৈরি করতে উপরের বাটনে চাপ দিন। যেকোনো সময় টুর্নামেন্ট এডিট বা ডিলেট করা যাবে এবং টুর্নামেন্টের ভিতরেই টিম ও খেলোয়াড় তৈরি ও ম্যানেজ করা যাবে।",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            items(filteredTournaments, key = { it.id }) { t ->
                // Participating teams for this tournament
                val tournamentTeams = allTeams.filter { team ->
                    t.teamIds.contains(team.id) || t.standings.any { it.teamId == team.id }
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Header with Logo, Title & Action Buttons (Edit & Delete anytime)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            if (isGuest) onRequireAuth("টুর্নামেন্ট এডিট করতে")
                                            else editingTournament = t
                                        }
                                        .testTag("tournament_logo_${t.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CricUpAvatar(
                                        model = t.logoUrl,
                                        size = 50.dp,
                                        fallbackText = t.name.take(2).ifBlank { "TR" },
                                        borderColor = adaptiveGoldColor(),
                                        borderWidth = 1.8.dp
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(t.name, color = adaptiveGoldColor(), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "${t.format} • ${t.matchOvers} Overs • ${t.venue.ifBlank { "Venue TBA" }}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                    if (t.logoUrl.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            color = EmeraldGreen.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(0.8.dp, EmeraldGreen.copy(alpha = 0.45f)),
                                            modifier = Modifier
                                                .clickable {
                                                    if (isGuest) onRequireAuth("টুর্নামেন্ট লোগো যোগ করতে")
                                                    else editingTournament = t
                                                }
                                                .testTag("add_tournament_logo_chip_${t.id}")
                                        ) {
                                            Text(
                                                text = "+ লোগো যোগ করুন",
                                                color = EmeraldGreen,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Row {
                                IconButton(
                                    onClick = {
                                        if (isGuest) onRequireAuth("টুর্নামেন্ট এডিট করতে")
                                        else editingTournament = t
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Tournament", tint = adaptiveGoldColor())
                                }
                                IconButton(
                                    onClick = {
                                        if (isGuest) onRequireAuth("টুর্নামেন্ট মুছে ফেলতে")
                                        else deletingTournament = t
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Tournament", tint = WicketRed)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.8.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Tournament Teams Section Header with two buttons: Create Team & Add Existing
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "টুর্নামেন্টের দলসমূহ (${tournamentTeams.size} টি দল):",
                                color = EmeraldGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Add Existing Team
                                Button(
                                    onClick = {
                                        if (isGuest) onRequireAuth("টুর্নামেন্টে দল যুক্ত করতে")
                                        else tournamentForExistingTeam = t
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldYellow.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("+বিদ্যমান দল", fontSize = 9.sp, color = adaptiveGoldColor(), fontWeight = FontWeight.Bold)
                                }

                                // Create New Team for this tournament
                                Button(
                                    onClick = {
                                        if (isGuest) onRequireAuth("টুর্নামেন্টে নতুন দল তৈরি করতে")
                                        else tournamentForNewTeam = t
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = DeepNavyBg, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("+দল তৈরি", fontSize = 9.sp, color = DeepNavyBg, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (tournamentTeams.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "এই টুর্নামেন্টে এখনও কোনো দল যুক্ত করা হয়নি।\nউপরে '+দল তৈরি' চাপ দিয়ে নতুন দল বা '+বিদ্যমান দল' চাপ দিয়ে আগের দল যুক্ত করুন।",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            tournamentTeams.forEach { team ->
                                val isExpanded = expandedTeamIds.contains(team.id)
                                val teamPlayers = allPlayers.filter { p ->
                                    team.playerIds.contains(p.id) || p.teamIds.contains(team.id)
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        // Team Header Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        if (isExpanded) expandedTeamIds.remove(team.id)
                                                        else expandedTeamIds.add(team.id)
                                                    },
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (isExpanded) CricUpIcons.ExpandLess else CricUpIcons.ExpandMore,
                                                    contentDescription = "Toggle Squad",
                                                    tint = EmeraldGreen,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(EmeraldGreen.copy(alpha = 0.2f))
                                                        .border(1.dp, EmeraldGreen, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (!team.logoUrl.isNullOrEmpty()) {
                                                        val resolvedTourneyTeamLogo = remember(team.logoUrl) { com.example.util.ImageModelResolver.resolveForCoil(team.logoUrl) }
                                                        coil.compose.AsyncImage(
                                                            model = resolvedTourneyTeamLogo,
                                                            contentDescription = team.name,
                                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                        )
                                                    } else {
                                                        Text(team.shortName.take(3), color = EmeraldGreen, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(team.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    Text("${team.shortName} • ${teamPlayers.size} Players", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Quick Add Player button
                                                Button(
                                                    onClick = {
                                                        if (isGuest) onRequireAuth("দলে খেলোয়াড় যোগ করতে")
                                                        else teamForNewPlayer = Pair(t, team)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen.copy(alpha = 0.15f)),
                                                    shape = RoundedCornerShape(6.dp),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(26.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(12.dp))
                                                    Text("খেলোয়াড়", fontSize = 9.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                                                }

                                                IconButton(
                                                    onClick = {
                                                        if (isGuest) onRequireAuth("দল এডিট করতে")
                                                        else editingTeamInTournament = team
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Team", tint = adaptiveGoldColor(), modifier = Modifier.size(15.dp))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        if (isGuest) onRequireAuth("দল সরাতে")
                                                        else deletingTeamInTournament = Pair(t, team)
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Team", tint = WicketRed, modifier = Modifier.size(15.dp))
                                                }
                                            }
                                        }

                                        // Expandable Player List for this Team
                                        if (isExpanded) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "দলের খেলোয়াড় তালিকা (${teamPlayers.size} জন):",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = adaptiveGoldColor()
                                                )
                                                Text(
                                                    "ডাটাবেজে সংরক্ষিত ও যেকোনো ম্যাচে ব্যবহারযোগ্য",
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            if (teamPlayers.isEmpty()) {
                                                Text(
                                                    "কোনো খেলোয়াড় যুক্ত করা নেই। উপরের '+ খেলোয়াড়' চাপ দিয়ে নতুন খেলোয়াড় যুক্ত করুন।",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                )
                                            } else {
                                                teamPlayers.forEach { player ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 2.dp)
                                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                com.example.data.model.PlayerRoles.getRoleIcon(player.primaryRole),
                                                                fontSize = 13.sp
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Column {
                                                                Text(player.fullName, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                                Text("${player.primaryRole} • #${player.jerseyNumber}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.5.sp)
                                                            }
                                                        }

                                                        Row {
                                                            IconButton(
                                                                onClick = {
                                                                    if (isGuest) onRequireAuth("খেলোয়াড় এডিট করতে")
                                                                    else editingPlayerInTeam = player
                                                                },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(Icons.Default.Edit, contentDescription = "Edit Player", tint = adaptiveGoldColor(), modifier = Modifier.size(13.dp))
                                                            }
                                                            IconButton(
                                                                onClick = {
                                                                    if (isGuest) onRequireAuth("খেলোয়াড় সরাতে")
                                                                    else deletingPlayerInTeam = Pair(team, player)
                                                                },
                                                                modifier = Modifier.size(24.dp)
                                                            ) {
                                                                Icon(Icons.Default.Delete, contentDescription = "Delete Player", tint = WicketRed, modifier = Modifier.size(13.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 1. EDIT TOURNAMENT DIALOG (ANYTIME)
    editingTournament?.let { t ->
        var editName by remember(t.id) { mutableStateOf(t.name) }
        var editVenue by remember(t.id) { mutableStateOf(t.venue) }
        var editOvers by remember(t.id) { mutableStateOf(t.matchOvers.toString()) }
        var editFormat by remember(t.id) { mutableStateOf(t.format) }
        var editLogoUrl by remember(t.id) { mutableStateOf(t.logoUrl) }
        var isCompressingEditLogo by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { editingTournament = null },
            title = { Text("টুর্নামেন্ট সম্পাদনা / Edit Tournament", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TournamentLogoPickerRow(
                        logoUrl = editLogoUrl,
                        fallbackName = editName.ifBlank { t.name }.ifBlank { "TR" },
                        isProcessing = isCompressingEditLogo,
                        onProcessingChange = { isCompressingEditLogo = it },
                        onLogoPicked = { editLogoUrl = it },
                        onLogoRemoved = { editLogoUrl = null }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Tournament Name (টুর্নামেন্টের নাম)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editVenue,
                        onValueChange = { editVenue = it },
                        label = { Text("Venue (মাঠ / ভেন্যু)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editOvers,
                        onValueChange = { editOvers = it },
                        label = { Text("Overs per Match (ম্যাচের ওভার)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editFormat,
                        onValueChange = { editFormat = it },
                        label = { Text("Format (যেমন: League, Knockout)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isCompressingEditLogo) return@Button
                        if (editName.isBlank()) {
                            Toast.makeText(context, "টুর্নামেন্টের নাম লিখুন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val updated = t.copy(
                            name = editName.trim(),
                            venue = editVenue.trim(),
                            matchOvers = editOvers.toIntOrNull() ?: t.matchOvers,
                            format = editFormat.trim(),
                            logoUrl = editLogoUrl
                        )
                        scope.launch {
                            container.tournamentRepository.saveTournament(updated)
                            editingTournament = null
                            Toast.makeText(context, "টুর্নামেন্ট সফলভাবে আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("আপডেট করুন (Update)")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTournament = null }) {
                    Text("বাতিল (Cancel)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // 2. DELETE TOURNAMENT CONFIRMATION DIALOG (ANYTIME)
    deletingTournament?.let { t ->
        AlertDialog(
            onDismissRequest = { deletingTournament = null },
            title = { Text("টুর্নামেন্ট মুছে ফেলবেন?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Text("আপনি কি নিশ্চিতভাবে '${t.name}' টুর্নামেন্টটি মুছে ফেলতে চান? এটি স্থায়ীভাবে ডিলিট হয়ে যাবে।", color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            container.tournamentRepository.deleteTournament(t.id)
                            deletingTournament = null
                            Toast.makeText(context, "টুর্নামেন্ট সফলভাবে মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WicketRed)
                ) {
                    Text("মুছে ফেলুন (Delete)")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTournament = null }) {
                    Text("বাতিল", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // 3. ADD EXISTING TEAM TO TOURNAMENT DIALOG
    tournamentForExistingTeam?.let { tournament ->
        val availableTeams = allTeams.filter { team ->
            !tournament.teamIds.contains(team.id) && !tournament.standings.any { it.teamId == team.id }
        }

        AlertDialog(
            onDismissRequest = { tournamentForExistingTeam = null },
            title = { Text("টুর্নামেন্টে বিদ্যমান দল যুক্ত করুন", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                if (availableTeams.isEmpty()) {
                    Text("আপনার ডাটাবেজের সকল দল ইতিমধ্যেই এই টুর্নামেন্টে অন্তর্ভুক্ত আছে। নতুন দল তৈরি করতে '+দল তৈরি' বোতাম চাপুন।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                        items(availableTeams, key = { it.id }) { team ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .clickable {
                                        scope.launch {
                                            val updatedStanding = com.example.data.model.TournamentTeamStanding(
                                                teamId = team.id,
                                                teamName = team.name,
                                                shortName = team.shortName
                                            )
                                            val updatedTournament = tournament.copy(
                                                teamIds = (tournament.teamIds + team.id).distinct(),
                                                standings = (tournament.standings + updatedStanding).distinctBy { it.teamId }
                                            )
                                            container.tournamentRepository.saveTournament(updatedTournament)
                                            tournamentForExistingTeam = null
                                            Toast.makeText(context, "'${team.name}' টুর্নামেন্টে যুক্ত হয়েছে!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(EmeraldGreen.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(team.shortName.take(3), color = EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(team.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("${team.shortName} • ${team.homeGround}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                    }
                                }
                                Text("+যুক্ত করুন", color = EmeraldGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { tournamentForExistingTeam = null }) {
                    Text("বাতিল", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // 4. CREATE TEAM INSIDE TOURNAMENT (WITH SQUAD PLAYERS SAVED IN DATABASE)
    tournamentForNewTeam?.let { tournament ->
        var teamName by remember { mutableStateOf("") }
        var shortName by remember { mutableStateOf("") }
        var homeGround by remember { mutableStateOf(tournament.venue) }
        var contactInfo by remember { mutableStateOf("") }
        var teamLogoUrl by remember { mutableStateOf<String?>(null) }
        var pendingPlayerName by remember { mutableStateOf("") }
        val squadPlayerNames = remember { mutableStateListOf<String>() }

        val teamLogoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val encoded = com.example.util.ImagePickerHelper.compressUriToBase64Async(context, uri)
                    if (encoded != null) {
                        teamLogoUrl = encoded
                    } else {
                        Toast.makeText(context, "ছবি প্রসেস করা যায়নি, অন্য ছবি চেষ্টা করুন", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { tournamentForNewTeam = null },
            title = { Text("টুর্নামেন্টে নতুন দল তৈরি করুন", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    item {
                        Text("টুর্নামেন্ট: ${tournament.name}", color = adaptiveGoldColor(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("এই দল ও খেলোয়াড়দের প্রোফাইল স্থায়ীভাবে ডাটাবেজে জমা থাকবে এবং ভবিষ্যতে যেকোনো ম্যাচ ও টুর্নামেন্টে ব্যবহার করা যাবে।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.5.dp, if (teamLogoUrl != null) EmeraldGreen else MaterialTheme.colorScheme.outline, CircleShape)
                                    .clickable {
                                        teamLogoPicker.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (!teamLogoUrl.isNullOrEmpty()) {
                                    val resolvedAddTeamLogo = remember(teamLogoUrl) { com.example.util.ImageModelResolver.resolveForCoil(teamLogoUrl) }
                                    coil.compose.AsyncImage(
                                        model = resolvedAddTeamLogo,
                                        contentDescription = "Team Logo",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(CricUpIcons.SportsCricket, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                                        Text("+লোগো", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp)
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = teamName,
                                    onValueChange = { teamName = it },
                                    label = { Text("Team Name (দলের নাম) *") },
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = shortName,
                            onValueChange = { shortName = it.take(4).uppercase() },
                            label = { Text("Short Code (সংক্ষিপ্ত কোড, যেমন: DHG)") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = homeGround,
                            onValueChange = { homeGround = it },
                            label = { Text("Home Ground / Stadium") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("দলের খেলোয়াড় অন্তর্ভুক্তি (Squad Players):", color = adaptiveGoldColor(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("দল গঠনের সময়ই খেলোয়াড়দের নাম যুক্ত করুন।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = pendingPlayerName,
                                onValueChange = { pendingPlayerName = it },
                                label = { Text("খেলোয়াড়ের নাম") },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val trimmed = pendingPlayerName.trim()
                                    if (trimmed.isNotBlank()) {
                                        if (!squadPlayerNames.any { it.equals(trimmed, ignoreCase = true) }) {
                                            squadPlayerNames.add(trimmed)
                                            pendingPlayerName = ""
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("+ যোগ")
                            }
                        }

                        if (squadPlayerNames.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                squadPlayerNames.forEach { pName ->
                                    AssistChip(
                                        onClick = { squadPlayerNames.remove(pName) },
                                        label = { Text(pName, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp) },
                                        trailingIcon = {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = WicketRed, modifier = Modifier.size(16.dp))
                                        },
                                        colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (teamName.isBlank()) {
                            Toast.makeText(context, "দলের নাম লিখুন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val finalShort = if (shortName.isBlank()) teamName.take(3).uppercase() else shortName.trim()
                        val newTeamId = java.util.UUID.randomUUID().toString()
                        scope.launch {
                            val createdPlayerIds = mutableListOf<String>()
                            squadPlayerNames.forEachIndexed { index, pName ->
                                val p = Player(
                                    fullName = pName.trim(),
                                    teamIds = listOf(newTeamId),
                                    creatorId = currentUserId,
                                    jerseyNumber = index + 1,
                                    primaryRole = "All-rounder"
                                )
                                container.playerRepository.savePlayer(p)
                                createdPlayerIds.add(p.id)
                            }
                            val newTeam = Team(
                                id = newTeamId,
                                name = teamName.trim(),
                                shortName = finalShort,
                                logoUrl = teamLogoUrl,
                                homeGround = homeGround.trim(),
                                contactInfo = contactInfo.trim(),
                                playerIds = createdPlayerIds.filter { it.isNotBlank() },
                                ownerId = currentUserId
                            )
                            container.teamRepository.saveTeam(newTeam)

                            // Add team to this tournament's standings and teamIds
                            val updatedStanding = com.example.data.model.TournamentTeamStanding(
                                teamId = newTeamId,
                                teamName = newTeam.name,
                                shortName = newTeam.shortName
                            )
                            val updatedTournament = tournament.copy(
                                teamIds = (tournament.teamIds + newTeamId).distinct(),
                                standings = (tournament.standings + updatedStanding).distinctBy { it.teamId }
                            )
                            container.tournamentRepository.saveTournament(updatedTournament)

                            tournamentForNewTeam = null
                            Toast.makeText(context, "দল ও ${createdPlayerIds.size} জন খেলোয়াড় ডাটাবেজে ও টুর্নামেন্টে যুক্ত হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { tournamentForNewTeam = null }) {
                    Text("বাতিল", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // 5. EDIT TEAM IN TOURNAMENT DIALOG
    editingTeamInTournament?.let { team ->
        var editName by remember { mutableStateOf(team.name) }
        var editShortName by remember { mutableStateOf(team.shortName) }
        var editHomeGround by remember { mutableStateOf(team.homeGround) }
        var editContact by remember { mutableStateOf(team.contactInfo) }
        var editLogoUrl by remember { mutableStateOf(team.logoUrl) }

        val editTeamLogoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val encoded = com.example.util.ImagePickerHelper.compressUriToBase64Async(context, uri)
                    if (encoded != null) {
                        editLogoUrl = encoded
                    } else {
                        Toast.makeText(context, "ছবি প্রসেস করা যায়নি, অন্য ছবি চেষ্টা করুন", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { editingTeamInTournament = null },
            title = { Text("দলের তথ্য সম্পাদনা / Edit Team", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Team Name (দলের নাম)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editShortName,
                        onValueChange = { editShortName = it.take(4).uppercase() },
                        label = { Text("Short Code (সংক্ষিপ্ত কোড)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editHomeGround,
                        onValueChange = { editHomeGround = it },
                        label = { Text("Home Ground") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = team.copy(
                            name = editName.trim(),
                            shortName = editShortName.trim(),
                            homeGround = editHomeGround.trim(),
                            contactInfo = editContact.trim(),
                            logoUrl = editLogoUrl
                        )
                        scope.launch {
                            container.teamRepository.saveTeam(updated)
                            editingTeamInTournament = null
                            Toast.makeText(context, "দলের তথ্য ডাটাবেজে আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("আপডেট করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTeamInTournament = null }) {
                    Text("বাতিল", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // 6. DELETE TEAM FROM TOURNAMENT (OPTION TO REMOVE FROM TOURNAMENT OR PERMANENTLY DELETE)
    deletingTeamInTournament?.let { (tourney, team) ->
        AlertDialog(
            onDismissRequest = { deletingTeamInTournament = null },
            title = { Text("দল রিমুভ / মুছে ফেলবেন?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("আপনি '${team.name}' দলের ক্ষেত্রে কী করতে চান?", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• টুর্নামেন্ট থেকে বাদ দিন: দলটির প্রোফাইল আপনার ডাটাবেজে জমা থাকবে এবং ভবিষ্যতে অন্য ম্যাচে ব্যবহার করা যাবে।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• স্থায়ীভাবে মুছুন: দলটি ডাটাবেজ থেকে সম্পূর্ণ মুছে যাবে।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.5.sp)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Option 1: Remove from tournament only
                    Button(
                        onClick = {
                            scope.launch {
                                val updatedTourney = tourney.copy(
                                    teamIds = tourney.teamIds.filter { it != team.id },
                                    standings = tourney.standings.filter { it.teamId != team.id }
                                )
                                container.tournamentRepository.saveTournament(updatedTourney)
                                deletingTeamInTournament = null
                                Toast.makeText(context, "'${team.name}' টুর্নামেন্ট থেকে বাদ দেওয়া হয়েছে (ডাটাবেজে সংরক্ষিত রয়েছে)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldYellow)
                    ) {
                        Text("টুর্নামেন্ট থেকে বাদ", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Option 2: Delete permanently
                    Button(
                        onClick = {
                            scope.launch {
                                val updatedTourney = tourney.copy(
                                    teamIds = tourney.teamIds.filter { it != team.id },
                                    standings = tourney.standings.filter { it.teamId != team.id }
                                )
                                container.tournamentRepository.saveTournament(updatedTourney)
                                container.teamRepository.deleteTeam(team.id)
                                deletingTeamInTournament = null
                                Toast.makeText(context, "দল সম্পূর্ণভাবে মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WicketRed)
                    ) {
                        Text("স্থায়ীভাবে মুছুন", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTeamInTournament = null }) {
                    Text("বাতিল", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // 7. CREATE PLAYER FOR TEAM IN TOURNAMENT
    teamForNewPlayer?.let { (_, team) ->
        var pName by remember { mutableStateOf("") }
        var pJersey by remember { mutableStateOf((team.playerIds.size + 1).toString()) }
        var pRole by remember { mutableStateOf("All-rounder") }

        AlertDialog(
            onDismissRequest = { teamForNewPlayer = null },
            title = { Text("দলে নতুন খেলোয়াড় তৈরি করুন", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("দল: ${team.name}", color = adaptiveGoldColor(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("এই খেলোয়াড় স্থায়ীভাবে ডাটাবেজে জমা থাকবে এবং ভবিষ্যতে অন্যান্য ম্যাচেও পাওয়া যাবে।", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = pName,
                        onValueChange = { pName = it },
                        label = { Text("Player Full Name (খেলোয়াড়ের নাম) *") },
                        placeholder = { Text("যেমন: সাকিব আল হাসান") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pJersey,
                        onValueChange = { pJersey = it },
                        label = { Text("Jersey Number (জার্সি নং)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Role (খেলোয়াড়ের ভূমিকা):", color = adaptiveGoldColor(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Batter", "Bowler", "All-rounder", "Keeper").forEach { role ->
                            Button(
                                onClick = { pRole = role },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (pRole == role) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(role, fontSize = 9.sp, color = if (pRole == role) DeepNavyBg else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pName.isBlank()) {
                            Toast.makeText(context, "খেলোয়াড়ের নাম প্রদান করুন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val newPlayerId = java.util.UUID.randomUUID().toString()
                        val newPlayer = Player(
                            id = newPlayerId,
                            fullName = pName.trim(),
                            jerseyNumber = pJersey.toIntOrNull() ?: (team.playerIds.size + 1),
                            primaryRole = pRole,
                            teamIds = listOf(team.id),
                            creatorId = currentUserId
                        )
                        scope.launch {
                            container.playerRepository.savePlayer(newPlayer)
                            val updatedTeam = team.copy(playerIds = (team.playerIds + newPlayerId).distinct())
                            container.teamRepository.saveTeam(updatedTeam)
                            teamForNewPlayer = null
                            Toast.makeText(context, "'${pName.trim()}' খেলোয়াড় সফলভাবে দলে যুক্ত ও ডাটাবেজে সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { teamForNewPlayer = null }) {
                    Text("বাতিল", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // 8. EDIT PLAYER IN TEAM
    editingPlayerInTeam?.let { player ->
        var editPName by remember { mutableStateOf(player.fullName) }
        var editJersey by remember { mutableStateOf(player.jerseyNumber.toString()) }
        var editRole by remember { mutableStateOf(player.primaryRole) }

        AlertDialog(
            onDismissRequest = { editingPlayerInTeam = null },
            title = { Text("খেলোয়াড় সম্পাদনা / Edit Player", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editPName,
                        onValueChange = { editPName = it },
                        label = { Text("Player Name (খেলোয়াড়ের নাম)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editJersey,
                        onValueChange = { editJersey = it },
                        label = { Text("Jersey Number (জার্সি নম্বর)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Role:", color = adaptiveGoldColor(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Batter", "Bowler", "All-rounder", "Keeper").forEach { role ->
                            Button(
                                onClick = { editRole = role },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (editRole == role) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(role, fontSize = 9.sp, color = if (editRole == role) DeepNavyBg else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = player.copy(
                            fullName = editPName.trim(),
                            jerseyNumber = editJersey.toIntOrNull() ?: player.jerseyNumber,
                            primaryRole = editRole
                        )
                        scope.launch {
                            container.playerRepository.savePlayer(updated)
                            editingPlayerInTeam = null
                            Toast.makeText(context, "খেলোয়াড়ের তথ্য ডাটাবেজে আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("আপডেট করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPlayerInTeam = null }) {
                    Text("বাতিল", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // 9. DELETE PLAYER FROM TEAM
    deletingPlayerInTeam?.let { (team, player) ->
        AlertDialog(
            onDismissRequest = { deletingPlayerInTeam = null },
            title = { Text("খেলোয়াড় মুছে ফেলবেন?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Text("আপনি কি '${team.name}' দল ও ডাটাবেজ থেকে '${player.fullName}' খেলোয়াড়কে স্থায়ীভাবে মুছে ফেলতে চান?", color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val updatedTeam = team.copy(playerIds = team.playerIds.filter { it != player.id })
                            container.teamRepository.saveTeam(updatedTeam)
                            container.playerRepository.deletePlayer(player.id)
                            deletingPlayerInTeam = null
                            Toast.makeText(context, "খেলোয়াড় সফলভাবে মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WicketRed)
                ) {
                    Text("মুছে ফেলুন (Delete)")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPlayerInTeam = null }) {
                    Text("বাতিল", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun ScorerAssignmentsList(
    matches: List<com.example.data.model.Match>,
    currentUserId: String,
    onNavigateToMatch: (String) -> Unit
) {
    val assignedMatches = matches.filter { it.authorizedScorers.contains(currentUserId) }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(14.dp)) {
        item {
            Text("MATCHES ASSIGNED TO YOU FOR SCORING", color = adaptiveGoldColor(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (assignedMatches.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No matches currently assigned to you as official scorer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(assignedMatches, key = { it.id }) { m ->
                MatchCard(match = m, onClick = { onNavigateToMatch(m.id) })
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun MyUmpiresList(
    umpires: List<Umpire>,
    container: AppContainer,
    isGuest: Boolean,
    onRequireAuth: (String) -> Unit,
    onAddUmpire: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var deletingUmpire by remember { mutableStateOf<Umpire?>(null) }
    var editingUmpire by remember { mutableStateOf<Umpire?>(null) }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(14.dp)) {
        item {
            Button(
                onClick = onAddUmpire,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = DeepNavyBg)
                Spacer(modifier = Modifier.width(6.dp))
                Text("REGISTER OFFICIAL UMPIRE", color = DeepNavyBg, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (umpires.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No registered umpires yet.\nTap button above to register umpires for quick match assignments.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            items(umpires, key = { it.id }) { u ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, EmeraldGreen.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!u.photoUrl.isNullOrEmpty()) {
                                val resolvedUmpireItemPhoto = remember(u.photoUrl) { com.example.util.ImageModelResolver.resolveForCoil(u.photoUrl) }
                                coil.compose.AsyncImage(
                                    model = resolvedUmpireItemPhoto,
                                    contentDescription = u.name,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = u.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(EmeraldDark.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = u.certificationLevel,
                                        color = EmeraldGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (u.association.isNotBlank()) {
                                Text(
                                    text = u.association,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            if (u.phone.isNotBlank()) {
                                Text(
                                    text = "📞 ${u.phone}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row {
                            IconButton(onClick = {
                                if (isGuest) onRequireAuth("আম্পায়ার এডিট করতে")
                                else editingUmpire = u
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Umpire",
                                    tint = adaptiveGoldColor()
                                )
                            }
                            IconButton(onClick = {
                                if (isGuest) onRequireAuth("আম্পায়ার মুছে ফেলতে")
                                else deletingUmpire = u
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Umpire",
                                    tint = WicketRed.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // EDIT UMPIRE DIALOG
    editingUmpire?.let { u ->
        var editName by remember { mutableStateOf(u.fullName) }
        var editAssociation by remember { mutableStateOf(u.association) }
        var editPhone by remember { mutableStateOf(u.phone) }
        var editCertification by remember { mutableStateOf(u.certificationLevel) }
        var editPhotoUrl by remember { mutableStateOf(u.photoUrl) }

        val editUmpirePhotoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val encoded = com.example.util.ImagePickerHelper.compressUriToBase64Async(context, uri)
                    if (encoded != null) {
                        editPhotoUrl = encoded
                    } else {
                        Toast.makeText(context, "ছবি প্রসেস করা যায়নি, অন্য ছবি চেষ্টা করুন", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { editingUmpire = null },
            title = { Text("আম্পায়ার সম্পাদনা ও ছবি পরিবর্তন / Edit Umpire", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.5.dp, if (editPhotoUrl != null) EmeraldGreen else MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable {
                                    editUmpirePhotoPicker.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!editPhotoUrl.isNullOrEmpty()) {
                                val resolvedEditUmpirePhoto = remember(editPhotoUrl) { com.example.util.ImageModelResolver.resolveForCoil(editPhotoUrl) }
                                coil.compose.AsyncImage(
                                    model = resolvedEditUmpirePhoto,
                                    contentDescription = "Umpire Photo",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Add Photo",
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text("+ছবি", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("আম্পায়ারের ছবি (ঐচ্ছিক)", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("ছবি পরিবর্তন করতে ট্যাপ করুন", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            if (editPhotoUrl != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "ছবি সরান (Remove)",
                                    color = WicketRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { editPhotoUrl = null }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Umpire Full Name (আম্পায়ারের নাম) *") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editAssociation,
                        onValueChange = { editAssociation = it },
                        label = { Text("Association / League (বোর্ড বা লিগ)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Phone Number (ফোন নম্বর)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editCertification,
                        onValueChange = { editCertification = it },
                        label = { Text("Certification Level (সার্টিফিকেশন)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isBlank()) {
                            Toast.makeText(context, "Please enter umpire name", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val updated = u.copy(
                            fullName = editName.trim(),
                            association = editAssociation.trim(),
                            phone = editPhone.trim(),
                            certificationLevel = editCertification.trim(),
                            photoUrl = editPhotoUrl
                        )
                        scope.launch {
                            container.umpireRepository.saveUmpire(updated)
                            editingUmpire = null
                            Toast.makeText(context, "আম্পায়ার তথ্য সফলভাবে আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("আপডেট করুন (Update)")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingUmpire = null }) {
                    Text("বাতিল (Cancel)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    deletingUmpire?.let { u ->
        AlertDialog(
            onDismissRequest = { deletingUmpire = null },
            title = { Text("Delete Umpire Profile?", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to remove ${u.name} from your registered umpires?", color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            container.umpireRepository.deleteUmpire(u.id)
                            deletingUmpire = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WicketRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingUmpire = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun TournamentLogoPickerRow(
    logoUrl: String?,
    fallbackName: String,
    isProcessing: Boolean,
    onProcessingChange: (Boolean) -> Unit,
    onLogoPicked: (String?) -> Unit,
    onLogoRemoved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                onProcessingChange(true)
                val compressed = com.example.util.MediaHelper.compressAvatarToBase64(context, uri)
                onProcessingChange(false)
                if (compressed != null) {
                    onLogoPicked(compressed)
                } else {
                    Toast.makeText(context, "লোগো প্রসেস করা যায়নি, অন্য ছবি চেষ্টা করুন।", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val launchPicker = {
        logoPicker.launch(
            androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .clickable(enabled = !isProcessing) { launchPicker() }
                .testTag("tournament_logo_picker_avatar"),
            contentAlignment = Alignment.Center
        ) {
            CricUpAvatar(
                model = logoUrl,
                size = 62.dp,
                fallbackText = fallbackName.take(2).ifBlank { "TR" },
                borderColor = if (!logoUrl.isNullOrBlank()) EmeraldGreen else adaptiveGoldColor(),
                borderWidth = 2.dp
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "টুর্নামেন্ট লোগো (Logo)",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isProcessing) "ছবি ছোট ও অপ্টিমাইজ হচ্ছে..." else "ট্যাপ করে গ্যালারি থেকে লোগো বেছে নিন",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { launchPicker() },
                    enabled = !isProcessing,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("pick_tournament_logo_btn"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (logoUrl.isNullOrBlank()) "+ লোগো দিন" else "পরিবর্তন করুন",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldGreen
                    )
                }
                if (!logoUrl.isNullOrBlank()) {
                    TextButton(
                        onClick = onLogoRemoved,
                        enabled = !isProcessing,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("remove_tournament_logo_btn")
                    ) {
                        Text(
                            text = "মুছুন",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WicketRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MyCricketSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    testTag: String
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder, fontSize = 13.sp) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = EmeraldGreen
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedBorderColor = EmeraldGreen,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}

@Composable
private fun MyCricketEmptySearchCard(
    query: String,
    entityLabel: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "\"${query.trim()}\" নামে কোনো $entityLabel পাওয়া যায়নি",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
