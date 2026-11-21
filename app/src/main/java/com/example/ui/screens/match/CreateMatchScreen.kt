package com.example.ui.screens.match

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.theme.CricUpIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.InningsScorecard
import com.example.data.model.Match
import com.example.data.model.MatchPlayerSnapshot
import com.example.data.model.MatchStatus
import com.example.data.model.MatchTeamSnapshot
import com.example.data.model.Player
import com.example.data.model.Team
import com.example.di.AppContainer
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.WicketRed
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import com.example.ui.components.CreateMatchAddPlayerDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMatchScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onMatchCreated: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentUserFlow = remember { container.authRepository.getCurrentUserFlow() }
    val currentUser = currentUserFlow.collectAsState(initial = null).value
    val currentUserId = currentUser?.uid?.takeIf { it.isNotBlank() }
        ?: remember(container) { container.authRepository.getCurrentUserId() }
    val allTeamsFlow = remember(currentUserId) { container.teamRepository.getMyTeamsFlow(currentUserId) }
    val allPlayersFlow = remember(currentUserId) { container.playerRepository.getMyPlayersFlow(currentUserId) }
    val rawTeams by allTeamsFlow.collectAsState(initial = emptyList())
    val allPlayers by allPlayersFlow.collectAsState(initial = emptyList())
    val teams = remember(rawTeams, currentUserId, currentUser) {
        val joinedIds = currentUser?.joinedTeamIds.orEmpty().toSet()
        rawTeams
            .filter { !it.isArchived }
            .sortedByDescending { team ->
                when {
                    team.ownerId == currentUserId -> 2
                    team.playerIds.contains(currentUserId) || joinedIds.contains(team.id) -> 1
                    else -> 0
                }
            }
    }

    var currentStep by remember { mutableIntStateOf(1) }

    // Step 1: Info
    var matchTitle by remember { mutableStateOf("Club League Match") }
    var matchCategory by remember { mutableStateOf("League") }
    var venue by remember { mutableStateOf("Mirpur Stadium") }
    var isOfflineSoloMatch by remember { mutableStateOf(false) }

    // Step 2: Format & Overs
    var matchFormat by remember { mutableStateOf("T20") }
    var totalOvers by remember { mutableIntStateOf(20) }
    var customOversText by remember { mutableStateOf("20") }

    // Step 3: Teams
    var selectedTeamA by remember { mutableStateOf<Team?>(null) }
    var selectedTeamB by remember { mutableStateOf<Team?>(null) }

    // Step 4: Squad & Playing XI (Up to 16 squad players per team, selectable playing XI)
    var playersPerTeam by remember { mutableIntStateOf(11) }
    val squadA = remember { mutableStateListOf<Player>() }
    val squadB = remember { mutableStateListOf<Player>() }
    val selectedXI_A = remember { mutableStateListOf<Player>() }
    val selectedXI_B = remember { mutableStateListOf<Player>() }
    var captainAId by remember { mutableStateOf("") }
    var keeperAId by remember { mutableStateOf("") }
    var captainBId by remember { mutableStateOf("") }
    var keeperBId by remember { mutableStateOf("") }

    // Step 5: Toss
    var tossWinnerTeamId by remember { mutableStateOf("") }
    var tossDecision by remember { mutableStateOf("BAT") } // BAT or FIELD

    // Step 6: Umpires & Match Code
    var umpire1 by remember { mutableStateOf("") }
    var umpire2 by remember { mutableStateOf("") }
    var umpire3 by remember { mutableStateOf("") }
    var isCreatingMatch by remember { mutableStateOf(false) }
    val generatedMatchCode = remember { com.example.util.MatchCodeGenerator.generateCandidateCode() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "CREATE NEW MATCH",
                            color = GoldYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            "Step $currentStep of 6 • ${getStepTitle(currentStep)}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep > 1) currentStep-- else onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (currentStep) {
                    1 -> Step1MatchInfo(
                        title = matchTitle, onTitleChange = { matchTitle = it },
                        category = matchCategory, onCategoryChange = { matchCategory = it },
                        venue = venue, onVenueChange = { venue = it },
                        isOfflineSolo = isOfflineSoloMatch,
                        onOfflineSoloChange = { isOfflineSoloMatch = it }
                    )
                    2 -> Step2MatchFormat(
                        format = matchFormat,
                        overs = totalOvers,
                        customText = customOversText,
                        onCustomTextChange = {
                            customOversText = it
                            val parsed = it.toIntOrNull() ?: 20
                            totalOvers = parsed
                            matchFormat = "Custom ($parsed Ov)"
                        },
                        onFormatChange = { fmt, ov ->
                            matchFormat = fmt
                            totalOvers = ov
                            customOversText = ov.toString()
                        }
                    )
                    3 -> Step3TeamSelection(
                        teams = teams,
                        teamA = selectedTeamA,
                        teamB = selectedTeamB,
                        squadA = squadA,
                        squadB = squadB,
                        selectedXI_A = selectedXI_A,
                        selectedXI_B = selectedXI_B,
                        playersPerTeam = playersPerTeam,
                        onSelectTeamA = {
                            if (selectedTeamA?.id == it.id) {
                                selectedTeamA = null
                                squadA.clear()
                                selectedXI_A.clear()
                                captainAId = ""
                                keeperAId = ""
                            } else {
                                selectedTeamA = it
                                squadA.clear()
                                selectedXI_A.clear()
                                // Only load players assigned to Team A (either via player.teamIds or team.playerIds), never from Team B
                                val teamPlayers = allPlayers.filter { p ->
                                    (p.teamIds.contains(it.id) || it.playerIds.contains(p.id)) && !squadB.any { b -> b.id == p.id }
                                }.take(16)
                                if (teamPlayers.isNotEmpty()) {
                                    squadA.addAll(teamPlayers)
                                    selectedXI_A.addAll(teamPlayers.take(playersPerTeam))
                                }
                                captainAId = selectedXI_A.firstOrNull()?.id ?: ""
                                keeperAId = selectedXI_A.getOrNull(1)?.id ?: ""
                            }
                        },
                        onSelectTeamB = {
                            if (selectedTeamB?.id == it.id) {
                                selectedTeamB = null
                                squadB.clear()
                                selectedXI_B.clear()
                                captainBId = ""
                                keeperBId = ""
                            } else {
                                selectedTeamB = it
                                squadB.clear()
                                selectedXI_B.clear()
                                // Only load players assigned to Team B (either via player.teamIds or team.playerIds), never from Team A
                                val teamPlayers = allPlayers.filter { p ->
                                    (p.teamIds.contains(it.id) || it.playerIds.contains(p.id)) && !squadA.any { a -> a.id == p.id }
                                }.take(16)
                                if (teamPlayers.isNotEmpty()) {
                                    squadB.addAll(teamPlayers)
                                    selectedXI_B.addAll(teamPlayers.take(playersPerTeam))
                                }
                                captainBId = selectedXI_B.firstOrNull()?.id ?: ""
                                keeperBId = selectedXI_B.getOrNull(1)?.id ?: ""
                            }
                        },
                        onClearTeamA = {
                            selectedTeamA = null
                            squadA.clear()
                            selectedXI_A.clear()
                            captainAId = ""
                            keeperAId = ""
                        },
                        onClearTeamB = {
                            selectedTeamB = null
                            squadB.clear()
                            selectedXI_B.clear()
                            captainBId = ""
                            keeperBId = ""
                        },
                        container = container
                    )
                    4 -> Step4SquadAndCaptainSelection(
                        teamA = selectedTeamA,
                        teamB = selectedTeamB,
                        allPlayers = allPlayers,
                        playersPerTeam = playersPerTeam,
                        onPlayersPerTeamChange = { count ->
                            playersPerTeam = count
                            while (selectedXI_A.size > count) selectedXI_A.removeAt(selectedXI_A.size - 1)
                            while (selectedXI_B.size > count) selectedXI_B.removeAt(selectedXI_B.size - 1)
                        },
                        squadA = squadA,
                        squadB = squadB,
                        selectedXI_A = selectedXI_A,
                        selectedXI_B = selectedXI_B,
                        captainAId = captainAId,
                        onCaptainAChange = { captainAId = it },
                        keeperAId = keeperAId,
                        onKeeperAChange = { keeperAId = it },
                        captainBId = captainBId,
                        onCaptainBChange = { captainBId = it },
                        keeperBId = keeperBId,
                        onKeeperBChange = { keeperBId = it },
                        container = container
                    )
                    5 -> Step5Toss(
                        teamA = selectedTeamA,
                        teamB = selectedTeamB,
                        tossWinner = tossWinnerTeamId,
                        onTossWinnerChange = { tossWinnerTeamId = it },
                        decision = tossDecision,
                        onDecisionChange = { tossDecision = it }
                    )
                    6 -> Step6PreviewAndScorer(
                        title = matchTitle,
                        format = "$matchFormat ($totalOvers Ov)",
                        venue = venue,
                        teamA = selectedTeamA,
                        teamB = selectedTeamB,
                        playingXI_A = selectedXI_A,
                        playingXI_B = selectedXI_B,
                        captainAId = captainAId,
                        captainBId = captainBId,
                        tossWinner = tossWinnerTeamId,
                        decision = tossDecision,
                        umpire1 = umpire1,
                        onUmpire1Change = { umpire1 = it },
                        umpire2 = umpire2,
                        onUmpire2Change = { umpire2 = it },
                        umpire3 = umpire3,
                        onUmpire3Change = { umpire3 = it },
                        matchCode = generatedMatchCode,
                        isOfflineSolo = isOfflineSoloMatch,
                        creatorName = currentUser?.displayName ?: container.authRepository.getEffectiveUserDisplayName(),
                        creatorUid = currentUser?.uid ?: currentUserId,
                        container = container
                    )
                }
            }

            // Navigation Buttons (Back & Next)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 1) {
                    Button(
                        onClick = { if (!isCreatingMatch) currentStep-- },
                        enabled = !isCreatingMatch,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Previous")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (currentStep < 6) {
                            if (currentStep == 1 && matchTitle.isBlank()) {
                                Toast.makeText(context, "Please enter match title", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (currentStep == 3) {
                                if (selectedTeamA == null || selectedTeamB == null || selectedTeamA?.id == selectedTeamB?.id) {
                                    Toast.makeText(context, "দয়া করে দুটি আলাদা দল নির্বাচন করুন / Please select two distinct teams", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                            }
                            if (currentStep == 4) {
                                if (selectedXI_A.size < 2 || selectedXI_B.size < 2) {
                                    Toast.makeText(context, "উভয় দলের একাদশে কমপক্ষে ২ জন খেলোয়াড় থাকতে হবে (স্ট্রাইকার ও নন-স্ট্রাইকার)", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                if (selectedXI_A.size != selectedXI_B.size) {
                                    Toast.makeText(context, "উভয় দলের খেলার খেলোয়াড় সংখ্যা অবশ্যই সমান হতে হবে (Playing player count must be equal: Team A has ${selectedXI_A.size}, Team B has ${selectedXI_B.size})", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                if (captainAId.isBlank() || !selectedXI_A.any { it.id == captainAId }) {
                                    Toast.makeText(context, "দয়া করে টিম এ এর জন্য অধিনায়ক (Captain) নির্বাচন করুন", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (captainBId.isBlank() || !selectedXI_B.any { it.id == captainBId }) {
                                    Toast.makeText(context, "দয়া করে টিম বি এর জন্য অধিনায়ক (Captain) নির্বাচন করুন", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                            }
                            if (currentStep == 5 && tossWinnerTeamId.isBlank()) {
                                selectedTeamA?.let { tossWinnerTeamId = it.id }
                            }
                            currentStep++
                        } else {
                            if (isCreatingMatch) return@Button
                            // Finish & Save Match
                            val teamA = selectedTeamA
                            val teamB = selectedTeamB
                            if (teamA == null || teamB == null || teamA.id == teamB.id) {
                                Toast.makeText(context, "দয়া করে দুটি আলাদা দল নির্বাচন করুন", Toast.LENGTH_LONG).show()
                                currentStep = 3
                                return@Button
                            }
                            if (selectedXI_A.size < 2 || selectedXI_B.size < 2) {
                                Toast.makeText(context, "উভয় দলে কমপক্ষে ২ জন খেলোয়াড় নির্বাচন করুন", Toast.LENGTH_LONG).show()
                                currentStep = 4
                                return@Button
                            }

                            val effectiveUmpire1 = if (umpire1.trim().isBlank()) {
                                if (isOfflineSoloMatch) "Self / Solo" else ""
                            } else {
                                umpire1.trim()
                            }

                            if (effectiveUmpire1.isBlank()) {
                                Toast.makeText(context, "১ম আম্পায়ার / ম্যাচ পরিচালকের নাম প্রদান করা বাধ্যতামূলক", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            // Determine batting and bowling first from toss
                            val effectiveTossWinnerId = tossWinnerTeamId.ifBlank { teamA.id }
                            val isTeamAWinner = effectiveTossWinnerId == teamA.id
                            val winnerElectedBat = tossDecision == "BAT"

                            val battingFirstId = when {
                                isTeamAWinner && winnerElectedBat -> teamA.id
                                isTeamAWinner && !winnerElectedBat -> teamB.id
                                !isTeamAWinner && winnerElectedBat -> teamB.id
                                else -> teamA.id
                            }
                            val bowlingFirstId = if (battingFirstId == teamA.id) teamB.id else teamA.id

                            val playingXIListA = selectedXI_A
                                .distinctBy { it.id }
                                .distinctBy { it.fullName.trim().lowercase() }
                                .take(playersPerTeam).map { p ->
                                MatchPlayerSnapshot(
                                    id = p.id,
                                    name = p.fullName,
                                    role = p.primaryRole,
                                    isCaptain = p.id == captainAId,
                                    isWicketkeeper = p.id == keeperAId,
                                    photoUrl = p.photoUrl
                                )
                            }
                            val playingXIListB = selectedXI_B
                                .distinctBy { it.id }
                                .distinctBy { it.fullName.trim().lowercase() }
                                .take(playersPerTeam).map { p ->
                                MatchPlayerSnapshot(
                                    id = p.id,
                                    name = p.fullName,
                                    role = p.primaryRole,
                                    isCaptain = p.id == captainBId,
                                    isWicketkeeper = p.id == keeperBId,
                                    photoUrl = p.photoUrl
                                )
                            }

                            // Avoid duplicating Base64 photo blobs in fullSquad when already present in playingXI
                            val xiIdsA = playingXIListA.mapTo(HashSet()) { it.id }
                            val xiIdsB = playingXIListB.mapTo(HashSet()) { it.id }

                            val fullSquadListA = (if (squadA.isNotEmpty()) squadA else selectedXI_A)
                                .distinctBy { it.id }
                                .distinctBy { it.fullName.trim().lowercase() }
                                .take(16).map { p ->
                                MatchPlayerSnapshot(
                                    id = p.id,
                                    name = p.fullName,
                                    role = p.primaryRole,
                                    isCaptain = p.id == captainAId,
                                    isWicketkeeper = p.id == keeperAId,
                                    photoUrl = if (p.id in xiIdsA) null else p.photoUrl
                                )
                            }
                            val fullSquadListB = (if (squadB.isNotEmpty()) squadB else selectedXI_B)
                                .distinctBy { it.id }
                                .distinctBy { it.fullName.trim().lowercase() }
                                .take(16).map { p ->
                                MatchPlayerSnapshot(
                                    id = p.id,
                                    name = p.fullName,
                                    role = p.primaryRole,
                                    isCaptain = p.id == captainBId,
                                    isWicketkeeper = p.id == keeperBId,
                                    photoUrl = if (p.id in xiIdsB) null else p.photoUrl
                                )
                            }

                            val defaultMaxWickets = minOf(
                                if (playingXIListA.size > 1) playingXIListA.size - 1 else 10,
                                if (playingXIListB.size > 1) playingXIListB.size - 1 else 10
                            )

                            val resolvedOwnerUid = currentUser?.uid?.takeIf { it.isNotEmpty() } ?: currentUserId
                            val finalMatchCode = if (isOfflineSoloMatch) "OFFLINE" else generatedMatchCode
                            val finalMatchId = if (isOfflineSoloMatch) "OFFLINE_${java.util.UUID.randomUUID().toString().take(8).uppercase()}" else generatedMatchCode
                            val safeOvers = totalOvers.coerceIn(1, 500)
                            val newMatch = Match(
                                id = finalMatchId,
                                matchCode = finalMatchCode,
                                code = finalMatchCode,
                                title = matchTitle.trim().ifBlank { "${teamA.shortName} vs ${teamB.shortName}" },
                                category = matchCategory,
                                format = matchFormat,
                                totalOvers = safeOvers,
                                venue = venue.trim(),
                                teamA = MatchTeamSnapshot(teamA.id, teamA.name, teamA.shortName, teamA.logoUrl),
                                teamB = MatchTeamSnapshot(teamB.id, teamB.name, teamB.shortName, teamB.logoUrl),
                                team1CaptainId = captainAId,
                                team2CaptainId = captainBId,
                                fullSquad_A = fullSquadListA,
                                fullSquad_B = fullSquadListB,
                                playingXI_A = playingXIListA,
                                playingXI_B = playingXIListB,
                                maxWicketsCustom = defaultMaxWickets,
                                playersPerTeam = playersPerTeam,
                                umpire1 = effectiveUmpire1,
                                umpire2 = umpire2.trim(),
                                umpire3 = umpire3.trim(),
                                tossWinnerTeamId = effectiveTossWinnerId,
                                tossDecision = tossDecision,
                                battingFirstTeamId = battingFirstId,
                                bowlingFirstTeamId = bowlingFirstId,
                                status = MatchStatus.LIVE,
                                ownerId = resolvedOwnerUid,
                                ownerUid = resolvedOwnerUid,
                                isPublic = !isOfflineSoloMatch,
                                isOfflineScored = isOfflineSoloMatch,
                                ownerName = currentUser?.displayName ?: container.authRepository.getEffectiveUserDisplayName(),
                                authorizedScorers = emptyList(), // Strict creator ownership
                                firstInnings = InningsScorecard(
                                    inningsNumber = 1,
                                    battingTeamId = battingFirstId,
                                    battingTeamName = if (battingFirstId == teamA.id) teamA.name else teamB.name,
                                    bowlingTeamId = bowlingFirstId,
                                    bowlingTeamName = if (bowlingFirstId == teamA.id) teamA.name else teamB.name
                                )
                            )

                            isCreatingMatch = true
                            val u1Name = umpire1.trim()
                            val u2Name = umpire2.trim()
                            val u3Name = umpire3.trim()
                            scope.launch {
                                try {
                                    // Persist new umpire names safely without overwriting existing umpire photos or blocking match start
                                    if (u1Name.isNotBlank()) {
                                        runCatching {
                                            container.umpireRepository.saveUmpire(
                                                com.example.data.model.Umpire(fullName = u1Name, creatorId = resolvedOwnerUid)
                                            )
                                        }
                                    }
                                    if (u2Name.isNotBlank()) {
                                        runCatching {
                                            container.umpireRepository.saveUmpire(
                                                com.example.data.model.Umpire(fullName = u2Name, creatorId = resolvedOwnerUid)
                                            )
                                        }
                                    }
                                    if (u3Name.isNotBlank()) {
                                        runCatching {
                                            container.umpireRepository.saveUmpire(
                                                com.example.data.model.Umpire(fullName = u3Name, creatorId = resolvedOwnerUid)
                                            )
                                        }
                                    }
                                    val result = container.matchRepository.createMatch(newMatch)
                                    val createdId = result.getOrNull()
                                    if (!createdId.isNullOrBlank()) {
                                        Toast.makeText(context, "ম্যাচ সফলভাবে তৈরি হয়েছে!", Toast.LENGTH_SHORT).show()
                                        onMatchCreated(createdId)
                                    } else {
                                        val errMsg = result.exceptionOrNull()?.localizedMessage
                                        Toast.makeText(
                                            context,
                                            if (!errMsg.isNullOrBlank()) "ম্যাচ তৈরি ব্যর্থ: $errMsg" else "ম্যাচ তৈরি করতে সমস্যা হয়েছে, আবার চেষ্টা করুন",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } catch (t: Throwable) {
                                    android.util.Log.e("CreateMatchScreen", "Error starting match: ${t.message}", t)
                                    Toast.makeText(
                                        context,
                                        "ম্যাচ শুরু করতে সমস্যা হয়েছে: ${t.localizedMessage ?: "আবার চেষ্টা করুন"}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    isCreatingMatch = false
                                }
                            }
                        }
                    },
                    enabled = !isCreatingMatch,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("match_wizard_next_btn")
                ) {
                    if (isCreatingMatch) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ম্যাচ চালু হচ্ছে...",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = if (currentStep == 6) "Confirm & Create Match" else "Next Step",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun getStepTitle(step: Int): String = when (step) {
    1 -> "Basic Info"
    2 -> "Format & Overs"
    3 -> "Select Teams"
    4 -> "Squad & Playing XI"
    5 -> "Toss & Pitch"
    6 -> "Review & Scorer"
    else -> ""
}

@Composable
private fun Step1MatchInfo(
    title: String, onTitleChange: (String) -> Unit,
    category: String, onCategoryChange: (String) -> Unit,
    venue: String, onVenueChange: (String) -> Unit,
    isOfflineSolo: Boolean, onOfflineSoloChange: (Boolean) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text("STEP 1: MATCH INFORMATION", color = GoldYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))

            // Match Mode Selection Card (Solo Offline vs Online Live)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, if (isOfflineSolo) EmeraldGreen else GoldYellow.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("📡 ম্যাচ মোড নির্বাচন (Match Mode)", color = GoldYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onOfflineSoloChange(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isOfflineSolo) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isOfflineSolo) Color.White else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📱 একক / অফলাইন", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Solo Offline", fontSize = 10.sp)
                            }
                        }
                        Button(
                            onClick = { onOfflineSoloChange(false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isOfflineSolo) GoldYellow else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (!isOfflineSolo) Color.Black else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🌐 অনলাইন লাইভ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Live Broadcast", fontSize = 10.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isOfflineSolo)
                            "✓ সম্পূর্ণ অফলাইন: ইন্টারনেট ছাড়া শুধু নিজের ফোনে স্কোরিং সংরক্ষিত থাকবে। কোনো কোড বা ক্লাউড সংযোগের প্রয়োজন নেই।"
                        else
                            "✓ অনলাইন ব্রডকাস্ট: ৬-অক্ষরের ইউনিক ম্যাচ কোড তৈরি হবে যার মাধ্যমে দর্শকরা যেকোনো ফোন থেকে লাইভ দেখতে পারবে।",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("Match Title") },
                modifier = Modifier.fillMaxWidth().testTag("match_title_input")
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text("Match Category:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            val categories = listOf("Friendly", "League", "Knockout", "Semi Final", "Final", "Practice")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.take(3).forEach { cat ->
                    Button(
                        onClick = { onCategoryChange(cat) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (category == cat) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (category == cat) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(cat, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.drop(3).forEach { cat ->
                    Button(
                        onClick = { onCategoryChange(cat) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (category == cat) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (category == cat) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(cat, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = venue,
                onValueChange = onVenueChange,
                label = { Text("Venue / Ground") },
                modifier = Modifier.fillMaxWidth().testTag("match_venue_input")
            )
        }
    }
}

@Composable
private fun Step2MatchFormat(
    format: String,
    overs: Int,
    customText: String,
    onCustomTextChange: (String) -> Unit,
    onFormatChange: (String, Int) -> Unit
) {
    var isCustomSelected by remember { mutableStateOf(format.startsWith("Custom")) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text("STEP 2: MATCH FORMAT & OVERS", color = GoldYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                "Select from official standard cricket formats or set custom overs.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            val formats = listOf(
                "T10" to 10,
                "T20" to 20,
                "T25" to 25,
                "T30" to 30,
                "T40" to 40,
                "T50" to 50,
                "Test" to 90
            )

            formats.forEach { (fmt, ov) ->
                val isSelected = !isCustomSelected && format == fmt
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            isCustomSelected = false
                            onFormatChange(fmt, ov)
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) EmeraldGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        if (isSelected) 1.5.dp else 1.dp,
                        if (isSelected) EmeraldGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                fmt,
                                color = if (isSelected) EmeraldGreen else MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (fmt == "Test") "Test Match • 90 Overs per day" else "$ov Overs per side",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        if (isSelected) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = EmeraldGreen)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Custom Overs Option
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { isCustomSelected = true },
                colors = CardDefaults.cardColors(
                    containerColor = if (isCustomSelected) EmeraldGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    if (isCustomSelected) 1.5.dp else 1.dp,
                    if (isCustomSelected) EmeraldGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Custom Overs (কাস্টম ওভার)",
                                color = if (isCustomSelected) EmeraldGreen else MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Enter any number of overs for friendly or short-format matches",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                        if (isCustomSelected) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = EmeraldGreen)
                        }
                    }

                    if (isCustomSelected) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customText,
                            onValueChange = onCustomTextChange,
                            label = { Text("Total Overs per side") },
                            placeholder = { Text("e.g. 8, 12, 16, 25") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Step3TeamSelection(
    teams: List<Team>,
    teamA: Team?,
    teamB: Team?,
    squadA: MutableList<Player>,
    squadB: MutableList<Player>,
    selectedXI_A: MutableList<Player>,
    selectedXI_B: MutableList<Player>,
    playersPerTeam: Int,
    container: AppContainer,
    onSelectTeamA: (Team) -> Unit,
    onSelectTeamB: (Team) -> Unit,
    onClearTeamA: () -> Unit,
    onClearTeamB: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showCreateTeamDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var teamToRemove by remember { mutableStateOf<Team?>(null) }

    val filteredTeams = remember(teams, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) teams
        else teams.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.shortName.contains(q, ignoreCase = true) ||
                it.homeGround.contains(q, ignoreCase = true)
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text("STEP 3: SELECT OR CREATE TEAMS", color = GoldYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                "Pick Team A and Team B, or create your teams manually anytime.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = { showCreateTeamDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("create_match_add_team_btn"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "নতুন দল যোগ করুন (Create Team)",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("দলের নাম বা কোড দিয়ে খুঁজুন...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Teams",
                        tint = EmeraldGreen
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldGreen,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_match_team_search_input")
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (filteredTeams.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = CricUpIcons.Group, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "'$searchQuery' নামের কোনো দল পাওয়া যায়নি" else "কোনো দল সংরক্ষিত নেই",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (searchQuery.isBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "উপরের বাটনে চাপ দিয়ে Team A ও Team B তৈরি করুন।",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        items(filteredTeams, key = { it.id }) { team ->
            val isTeamA = teamA?.id == team.id
            val isTeamB = teamB?.id == team.id

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("create_match_team_card_${team.id}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (isTeamA || isTeamB) 1.5.dp else 1.dp,
                    color = if (isTeamA) EmeraldGreen else if (isTeamB) GoldYellow else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, if (isTeamA) EmeraldGreen else if (isTeamB) GoldYellow else MaterialTheme.colorScheme.outline, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!team.logoUrl.isNullOrEmpty()) {
                                    val resolvedTeamLogo = remember(team.logoUrl) { com.example.util.ImageModelResolver.resolveForCoil(team.logoUrl) }
                                    coil.compose.AsyncImage(
                                        model = resolvedTeamLogo,
                                        contentDescription = team.name,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Text(team.shortName.take(3), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = team.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${team.shortName} • ${team.homeGround.ifBlank { "Home Ground" }}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = { teamToRemove = team },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("create_match_remove_team_btn_${team.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove Team",
                                tint = WicketRed
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onSelectTeamA(team) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTeamA) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isTeamA) Color.Black else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .testTag("select_team_a_btn_${team.id}")
                        ) {
                            if (isTeamA) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text("Team A", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onSelectTeamB(team) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTeamB) GoldYellow else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isTeamB) Color.Black else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .testTag("select_team_b_btn_${team.id}")
                        ) {
                            if (isTeamB) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text("Team B", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // REMOVE TEAM CONFIRMATION DIALOG
    teamToRemove?.let { targetTeam ->
        AlertDialog(
            onDismissRequest = { teamToRemove = null },
            title = {
                Text(
                    "দল রিমুভ করবেন?",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "আপনি কি '${targetTeam.name}' দলটি তালিকা থেকে মুছে ফেলতে চান?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            if (teamA?.id == targetTeam.id) onClearTeamA()
                            if (teamB?.id == targetTeam.id) onClearTeamB()
                            container.teamRepository.deleteTeam(targetTeam.id)
                            teamToRemove = null
                            Toast.makeText(context, "'${targetTeam.name}' রিমুভ করা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WicketRed),
                    modifier = Modifier.testTag("confirm_remove_team_dialog_btn")
                ) {
                    Text("রিমুভ করুন (Remove)", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { teamToRemove = null }) {
                    Text("বাতিল", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // CREATE TEAM INLINE DIALOG
    if (showCreateTeamDialog) {
        var newTeamName by remember { mutableStateOf("") }
        var newShortCode by remember { mutableStateOf("") }
        var newHomeGround by remember { mutableStateOf("") }
        var newTeamLogoUrl by remember { mutableStateOf<String?>(null) }
        var pendingPlayerName by remember { mutableStateOf("") }
        val newTeamPlayerNames = remember { mutableStateListOf<String>() }

        val teamLogoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val encoded = com.example.util.ImagePickerHelper.compressUriToBase64Async(context, uri)
                    if (encoded != null) {
                        newTeamLogoUrl = encoded
                    } else {
                        Toast.makeText(context, "ছবি প্রসেস করা যায়নি, অন্য ছবি চেষ্টা করুন", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showCreateTeamDialog = false },
            title = { Text("নতুন দল তৈরি করুন / Create Team", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, if (newTeamLogoUrl != null) EmeraldGreen else MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable {
                                    teamLogoPicker.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!newTeamLogoUrl.isNullOrEmpty()) {
                                val resolvedNewLogo = remember(newTeamLogoUrl) { com.example.util.ImageModelResolver.resolveForCoil(newTeamLogoUrl) }
                                coil.compose.AsyncImage(
                                    model = resolvedNewLogo,
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
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text("+লোগো", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = newTeamName,
                                onValueChange = { newTeamName = it },
                                label = { Text("Team Name (দলের নাম) *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newShortCode,
                        onValueChange = { newShortCode = it.take(4).uppercase() },
                        label = { Text("Short Code (সংক্ষিপ্ত কোড, যেমন: DHG)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newHomeGround,
                        onValueChange = { newHomeGround = it },
                        label = { Text("Home Ground (হোম মাঠ / ভেন্যু)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "দলের খেলোয়াড় অন্তর্ভুক্তি (Squad Players):",
                        color = GoldYellow,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "দলের সাথে সাথেই খেলোয়াড় যোগ করুন, যাতে সরাসরি ম্যাচে সিলেক্ট হয়ে যায়।",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = pendingPlayerName,
                            onValueChange = { pendingPlayerName = it },
                            label = { Text("খেলোয়াড়ের নাম") },
                            placeholder = { Text("যেমন: তামিম, সাকিব...") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val trimmed = pendingPlayerName.trim()
                                if (trimmed.isNotBlank()) {
                                    if (!newTeamPlayerNames.any { it.equals(trimmed, ignoreCase = true) }) {
                                        newTeamPlayerNames.add(trimmed)
                                        pendingPlayerName = ""
                                    } else {
                                        Toast.makeText(context, "খেলোয়াড় ইতিমধ্যেই যোগ করা হয়েছে", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+ যোগ")
                        }
                    }

                    if (newTeamPlayerNames.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "অন্তর্ভুক্ত খেলোয়াড় (${newTeamPlayerNames.size} জন):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            newTeamPlayerNames.forEach { pName ->
                                AssistChip(
                                    onClick = { newTeamPlayerNames.remove(pName) },
                                    label = { Text(pName, fontSize = 11.sp) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = Color.Red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTeamName.isBlank()) {
                            Toast.makeText(context, "দলের নাম লিখুন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val finalShort = if (newShortCode.isBlank()) newTeamName.take(3).uppercase() else newShortCode.trim()
                        val newTeamId = java.util.UUID.randomUUID().toString()
                        val currentUid = container.authRepository.getCurrentUserId()

                        scope.launch {
                            val createdPlayerObjects = mutableListOf<Player>()
                            newTeamPlayerNames.forEachIndexed { index, pName ->
                                val player = Player(
                                    fullName = pName.trim(),
                                    teamIds = listOf(newTeamId),
                                    creatorId = currentUid,
                                    jerseyNumber = index + 1,
                                    primaryRole = "All-rounder"
                                )
                                container.playerRepository.savePlayer(player)
                                createdPlayerObjects.add(player)
                            }

                            val created = Team(
                                id = newTeamId,
                                name = newTeamName.trim(),
                                shortName = finalShort,
                                logoUrl = newTeamLogoUrl,
                                homeGround = newHomeGround.trim(),
                                playerIds = createdPlayerObjects.map { it.id },
                                ownerId = currentUid
                            )
                            container.teamRepository.saveTeam(created)

                            if (teamA == null) {
                                onSelectTeamA(created)
                                squadA.clear()
                                squadA.addAll(createdPlayerObjects)
                                selectedXI_A.clear()
                                selectedXI_A.addAll(createdPlayerObjects.take(playersPerTeam))
                            } else if (teamB == null) {
                                onSelectTeamB(created)
                                squadB.clear()
                                squadB.addAll(createdPlayerObjects)
                                selectedXI_B.clear()
                                selectedXI_B.addAll(createdPlayerObjects.take(playersPerTeam))
                            }

                            showCreateTeamDialog = false
                            Toast.makeText(context, "দল ও ${createdPlayerObjects.size} জন খেলোয়াড় সফলভাবে তৈরি ও নির্বাচিত হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("তৈরি করুন (Create)", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTeamDialog = false }) {
                    Text("বাতিল (Cancel)")
                }
            }
        )
    }
}

@Composable
private fun Step4SquadAndCaptainSelection(
    teamA: Team?,
    teamB: Team?,
    allPlayers: List<Player>,
    playersPerTeam: Int,
    onPlayersPerTeamChange: (Int) -> Unit,
    squadA: MutableList<Player>,
    squadB: MutableList<Player>,
    selectedXI_A: MutableList<Player>,
    selectedXI_B: MutableList<Player>,
    captainAId: String, onCaptainAChange: (String) -> Unit,
    keeperAId: String, onKeeperAChange: (String) -> Unit,
    captainBId: String, onCaptainBChange: (String) -> Unit,
    keeperBId: String, onKeeperBChange: (String) -> Unit,
    container: AppContainer
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tabIndex by remember { mutableIntStateOf(0) } // 0 for Team A, 1 for Team B
    var showAddPlayerDialog by remember { mutableStateOf(false) }
    var addPlayerInitialTab by remember { mutableIntStateOf(0) } // 0 = Database, 1 = New Player

    val currentTeam = if (tabIndex == 0) teamA else teamB
    val currentSquad = if (tabIndex == 0) squadA else squadB
    val currentXI = if (tabIndex == 0) selectedXI_A else selectedXI_B
    val currentCaptainId = if (tabIndex == 0) captainAId else captainBId
    val currentKeeperId = if (tabIndex == 0) keeperAId else keeperBId
    val onCaptainChange = if (tabIndex == 0) onCaptainAChange else onCaptainBChange
    val onKeeperChange = if (tabIndex == 0) onKeeperAChange else onKeeperBChange

    Column(modifier = Modifier.fillMaxSize()) {
        Text("STEP 4: MATCH SQUAD & PLAYING PLAYERS", color = GoldYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))

        // Compact Players per team selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Players/Team: $playersPerTeam",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = GoldYellow
            )
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val counts = listOf(5, 6, 7, 8, 9, 10, 11, 12, 14, 16)
                items(counts, key = { it }) { cnt ->
                    val isSel = playersPerTeam == cnt
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onPlayersPerTeamChange(cnt) },
                        color = if (isSel) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSel) EmeraldGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = if (cnt == 11) "11(XI)" else "$cnt",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontSize = 10.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Tabs for Team A vs Team B
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { tabIndex = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tabIndex == 0) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (tabIndex == 0) Color.White else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("${teamA?.shortName ?: "Team A"} (${squadA.size} Sq / ${selectedXI_A.size} XI)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { tabIndex = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tabIndex == 1) GoldYellow else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (tabIndex == 1) Color.Black else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("${teamB?.shortName ?: "Team B"} (${squadB.size} Sq / ${selectedXI_B.size} XI)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Info bar + Add Player button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${currentTeam?.shortName ?: "Team"}: Playing Squad (${currentXI.size}/16)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Button(
                onClick = {
                    addPlayerInitialTab = 0
                    showAddPlayerDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (tabIndex == 0) EmeraldGreen else GoldYellow),
                shape = RoundedCornerShape(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("create_match_add_player_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = if (tabIndex == 0) Color.White else Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Add Player (নতুন / ডেটাবেজ)",
                    color = if (tabIndex == 0) Color.White else Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val otherSquad = if (tabIndex == 0) squadB else squadA
        val otherXI = if (tabIndex == 0) selectedXI_B else selectedXI_A
        val otherTeam = if (tabIndex == 0) teamB else teamA
        // Show players strictly assigned to this team or manually added to currentSquad for this match
        val combinedPlayers = (allPlayers + currentSquad)
            .distinctBy { it.fullName.trim().lowercase() }
            .filter { player ->
                val notInOther = !otherSquad.any { it.id == player.id } && !otherXI.any { it.id == player.id }
                val matchesCurrent = if (currentTeam != null && currentTeam.id.isNotBlank()) {
                    player.teamIds.contains(currentTeam.id) ||
                        currentTeam.playerIds.contains(player.id) ||
                        currentSquad.any { it.id == player.id }
                } else true

                notInOther && matchesCurrent
            }

        if (combinedPlayers.isEmpty() && currentXI.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${currentTeam?.name ?: "এই দলে"} কোনো খেলোয়াড় নেই", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "নিচের বাটন থেকে নতুন খেলোয়াড় তৈরি করুন অথবা Player Database থেকে খেলোয়াড় সিলেক্ট করুন।",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                addPlayerInitialTab = 0
                                showAddPlayerDialog = true
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldYellow),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("ডেটাবেজ থেকে", color = GoldYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                addPlayerInitialTab = 1
                                showAddPlayerDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+ নতুন খেলোয়াড়", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Player List (Shows only players belonging to this team)
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(combinedPlayers, key = { it.id }) { player ->
                val isInXI = currentXI.any { it.id == player.id }
                val isCaptain = currentCaptainId == player.id
                val isKeeper = currentKeeperId == player.id

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isInXI) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        if (isInXI) 1.5.dp else 1.dp,
                        if (isInXI) EmeraldGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Click to toggle Playing XI
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (isInXI) {
                                        currentXI.removeAll { it.id == player.id }
                                    } else {
                                        if (currentXI.size < playersPerTeam) {
                                            currentXI.add(player)
                                            if (!currentSquad.any { it.id == player.id }) {
                                                currentSquad.add(player)
                                            }
                                        } else {
                                            Toast.makeText(context, "এই ম্যাচে প্রতি দলে সর্বোচ্চ $playersPerTeam জন খেলতে পারবেন। অতিরিক্ত খেলোয়াড় নিতে উপরের স্লাইডার থেকে সংখ্যা বাড়ান।", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(if (isInXI) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, if (isInXI) EmeraldGreen else MaterialTheme.colorScheme.outline, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isInXI) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            com.example.ui.components.CricUpAvatar(
                                model = player.photoUrl,
                                size = 40.dp,
                                fallbackText = player.fullName.take(2),
                                fallbackIcon = Icons.Default.Person
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        player.fullName,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    if (isCaptain || isKeeper) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            color = if (isCaptain) GoldYellow else EmeraldGreen,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            val badgeText = when {
                                                isCaptain && isKeeper -> " C • WK "
                                                isCaptain -> " C "
                                                else -> " WK "
                                            }
                                            Text(
                                                text = badgeText,
                                                color = if (isCaptain) Color.Black else Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "${player.primaryRole} • #${player.jerseyNumber}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Captain & Keeper Toggle Badges
                        if (isInXI) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { onCaptainChange(if (isCaptain) "" else player.id) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isCaptain) GoldYellow else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isCaptain) Color.Black else MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("C", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }

                                Button(
                                    onClick = { onKeeperChange(if (isKeeper) "" else player.id) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isKeeper) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isKeeper) Color.White else MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("WK", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ADD PLAYER DIALOG (BOTH NEW FULL-DETAIL PLAYER + SELECT FROM PLAYER DATABASE)
    if (showAddPlayerDialog) {
        CreateMatchAddPlayerDialog(
            currentTeam = currentTeam,
            allPlayers = allPlayers,
            currentSquad = currentSquad,
            currentXI = currentXI,
            otherSquad = if (tabIndex == 0) squadB else squadA,
            otherXI = if (tabIndex == 0) selectedXI_B else selectedXI_A,
            playersPerTeam = playersPerTeam,
            currentCaptainId = currentCaptainId,
            onCaptainChange = onCaptainChange,
            container = container,
            initialTab = addPlayerInitialTab,
            onDismiss = { showAddPlayerDialog = false }
        )
    }
}

@Composable
private fun Step5Toss(
    teamA: Team?,
    teamB: Team?,
    tossWinner: String,
    onTossWinnerChange: (String) -> Unit,
    decision: String,
    onDecisionChange: (String) -> Unit
) {
    val winnerName = if (tossWinner == teamA?.id) teamA?.name else if (tossWinner == teamB?.id) teamB?.name else "Select Winner"
    val battingTeam = if (tossWinner == teamA?.id) {
        if (decision == "BAT") teamA?.name else teamB?.name
    } else {
        if (decision == "BAT") teamB?.name else teamA?.name
    }
    val bowlingTeam = if (battingTeam == teamA?.name) teamB?.name else teamA?.name

    Column(modifier = Modifier.fillMaxSize()) {
        Text("STEP 5: TOSS RESULT & DECISION", color = GoldYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(14.dp))

        Text("Who won the toss?", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onTossWinnerChange(teamA?.id ?: "") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tossWinner == teamA?.id) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (tossWinner == teamA?.id) Color.White else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(teamA?.name ?: "Team A", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { onTossWinnerChange(teamB?.id ?: "") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tossWinner == teamB?.id) GoldYellow else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (tossWinner == teamB?.id) Color.Black else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(teamB?.name ?: "Team B", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Toss Decision:", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onDecisionChange("BAT") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (decision == "BAT") EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (decision == "BAT") Color.White else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Bat First (ব্যাট)", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { onDecisionChange("FIELD") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (decision == "FIELD") EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (decision == "FIELD") Color.White else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Field First (ফিল্ডিং)", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Toss Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("🪙 TOSS SUMMARY", color = GoldYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$winnerName won the toss and elected to ${if (decision == "BAT") "bat" else "field"} first.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "1st Innings: $battingTeam will BAT. $bowlingTeam will BOWL.",
                    fontSize = 12.sp,
                    color = EmeraldGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun Step6PreviewAndScorer(
    title: String,
    format: String,
    venue: String,
    teamA: Team?,
    teamB: Team?,
    playingXI_A: List<Player>,
    playingXI_B: List<Player>,
    captainAId: String,
    captainBId: String,
    tossWinner: String,
    decision: String,
    umpire1: String,
    onUmpire1Change: (String) -> Unit,
    umpire2: String,
    onUmpire2Change: (String) -> Unit,
    umpire3: String,
    onUmpire3Change: (String) -> Unit,
    matchCode: String,
    isOfflineSolo: Boolean = false,
    creatorName: String,
    creatorUid: String,
    container: AppContainer
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val savedUmpires by remember(creatorUid) {
        container.umpireRepository.getMyUmpiresFlow(creatorUid)
    }.collectAsState(initial = emptyList())
    val winnerName = if (tossWinner == teamA?.id) teamA?.name else teamB?.name
    val captainAName = playingXI_A.firstOrNull { it.id == captainAId }?.fullName ?: "Not Assigned"
    val captainBName = playingXI_B.firstOrNull { it.id == captainBId }?.fullName ?: "Not Assigned"
    var showCreateUmpireModal by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text("STEP 6: UMPIRES & MATCH PREVIEW", color = GoldYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))

            // Match Officials / Umpires Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("⚖️ ম্যাচ পরিচালক ও আম্পায়ার (Match Officials)", color = EmeraldGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("১ম আম্পায়ার বাধ্যতামূলক, ২য় ও ৩য় আম্পায়ার ঐচ্ছিক", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select Saved Umpire:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.material3.TextButton(onClick = { showCreateUmpireModal = true }) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("+ নতুন আম্পায়ার ও ছবি", color = EmeraldGreen, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    val distinctUmpires = remember(savedUmpires) {
                        savedUmpires
                            .filter { it.fullName.isNotBlank() }
                            .distinctBy { it.fullName.trim().lowercase() }
                            .distinctBy { it.id.ifBlank { "name_${it.fullName.trim().lowercase()}" } }
                    }

                    if (distinctUmpires.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(distinctUmpires, key = { u -> "u1_${u.id.ifBlank { u.fullName.trim().lowercase() }}" }) { u ->
                                FilterChip(
                                    selected = umpire1 == u.fullName,
                                    onClick = {
                                        if (umpire1 == u.fullName) onUmpire1Change("")
                                        else onUmpire1Change(u.fullName)
                                    },
                                    leadingIcon = if (!u.photoUrl.isNullOrEmpty()) {
                                        {
                                            val resolvedUmpire1 = remember(u.photoUrl) { com.example.util.ImageModelResolver.resolveForCoil(u.photoUrl) }
                                            coil.compose.AsyncImage(
                                                model = resolvedUmpire1,
                                                contentDescription = u.fullName,
                                                modifier = Modifier.size(34.dp).clip(CircleShape),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        }
                                    } else null,
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    runCatching { container.umpireRepository.deleteUmpire(u.id) }
                                                    if (umpire1 == u.fullName) onUmpire1Change("")
                                                    if (umpire2 == u.fullName) onUmpire2Change("")
                                                    if (umpire3 == u.fullName) onUmpire3Change("")
                                                    Toast.makeText(context, "${u.fullName} মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Delete Umpire", tint = Color(0xFFFF5252), modifier = Modifier.size(12.dp))
                                        }
                                    },
                                    label = { Text(u.fullName, fontSize = 11.sp) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = umpire1,
                        onValueChange = onUmpire1Change,
                        label = { Text("১ম আম্পায়ার / ম্যাচ পরিচালক (Umpire 1) *") },
                        placeholder = { Text("আম্পায়ারের নাম লিখুন (বাধ্যতামূলক)") },
                        modifier = Modifier.fillMaxWidth().testTag("umpire1_input"),
                        singleLine = true,
                        isError = umpire1.trim().isBlank(),
                        supportingText = {
                            if (umpire1.trim().isBlank()) {
                                Text("বাধ্যতামূলক ফিল্ড (Mandatory)", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val availableForUmpire2 = distinctUmpires.filter { !it.fullName.trim().equals(umpire1.trim(), ignoreCase = true) }
                    if (availableForUmpire2.isNotEmpty()) {
                        Text("Select 2nd Umpire (২য় আম্পায়ার বাছুন - ঐচ্ছিক):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableForUmpire2, key = { u -> "u2_${u.id.ifBlank { u.fullName.trim().lowercase() }}" }) { u ->
                                FilterChip(
                                    selected = umpire2 == u.fullName,
                                    onClick = {
                                        if (umpire2 == u.fullName) onUmpire2Change("")
                                        else onUmpire2Change(u.fullName)
                                    },
                                    leadingIcon = if (!u.photoUrl.isNullOrEmpty()) {
                                        {
                                            val resolvedUmpire2 = remember(u.photoUrl) { com.example.util.ImageModelResolver.resolveForCoil(u.photoUrl) }
                                            coil.compose.AsyncImage(
                                                model = resolvedUmpire2,
                                                contentDescription = u.fullName,
                                                modifier = Modifier.size(34.dp).clip(CircleShape),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        }
                                    } else null,
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    runCatching { container.umpireRepository.deleteUmpire(u.id) }
                                                    if (umpire1 == u.fullName) onUmpire1Change("")
                                                    if (umpire2 == u.fullName) onUmpire2Change("")
                                                    if (umpire3 == u.fullName) onUmpire3Change("")
                                                    Toast.makeText(context, "${u.fullName} মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Delete Umpire", tint = Color(0xFFFF5252), modifier = Modifier.size(12.dp))
                                        }
                                    },
                                    label = { Text(u.fullName, fontSize = 10.5.sp) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    OutlinedTextField(
                        value = umpire2,
                        onValueChange = onUmpire2Change,
                        label = { Text("২য় আম্পায়ার (Umpire 2) - ঐচ্ছিক") },
                        placeholder = { Text("২য় আম্পায়ারের নাম (অপশনাল)") },
                        modifier = Modifier.fillMaxWidth().testTag("umpire2_input"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val availableForUmpire3 = distinctUmpires.filter { 
                        !it.fullName.trim().equals(umpire1.trim(), ignoreCase = true) && 
                        !it.fullName.trim().equals(umpire2.trim(), ignoreCase = true) 
                    }
                    if (availableForUmpire3.isNotEmpty()) {
                        Text("Select 3rd Umpire (৩য় আম্পায়ার বাছুন - ঐচ্ছিক):", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableForUmpire3, key = { u -> "u3_${u.id.ifBlank { u.fullName.trim().lowercase() }}" }) { u ->
                                FilterChip(
                                    selected = umpire3 == u.fullName,
                                    onClick = {
                                        if (umpire3 == u.fullName) onUmpire3Change("")
                                        else onUmpire3Change(u.fullName)
                                    },
                                    leadingIcon = if (!u.photoUrl.isNullOrEmpty()) {
                                        {
                                            val resolvedUmpire3 = remember(u.photoUrl) { com.example.util.ImageModelResolver.resolveForCoil(u.photoUrl) }
                                            coil.compose.AsyncImage(
                                                model = resolvedUmpire3,
                                                contentDescription = u.fullName,
                                                modifier = Modifier.size(34.dp).clip(CircleShape),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        }
                                    } else null,
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    runCatching { container.umpireRepository.deleteUmpire(u.id) }
                                                    if (umpire1 == u.fullName) onUmpire1Change("")
                                                    if (umpire2 == u.fullName) onUmpire2Change("")
                                                    if (umpire3 == u.fullName) onUmpire3Change("")
                                                    Toast.makeText(context, "${u.fullName} মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Delete Umpire", tint = Color(0xFFFF5252), modifier = Modifier.size(12.dp))
                                        }
                                    },
                                    label = { Text(u.fullName, fontSize = 10.5.sp) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    OutlinedTextField(
                        value = umpire3,
                        onValueChange = onUmpire3Change,
                        label = { Text("৩য় আম্পায়ার / ম্যাচ রেফারি (Umpire 3) - ঐচ্ছিক") },
                        placeholder = { Text("৩য় আম্পায়ার বা রেফারির নাম (অপশনাল)") },
                        modifier = Modifier.fillMaxWidth().testTag("umpire3_input"),
                        singleLine = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("$format • $venue", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Team A: ${teamA?.name} (${playingXI_A.distinctBy { it.id }.size} players)", color = EmeraldGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Captain (C): $captainAName", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Team B: ${teamB?.name} (${playingXI_B.distinctBy { it.id }.size} players)", color = GoldYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Captain (C): $captainBName", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Toss: $winnerName elected to ${if (decision == "BAT") "bat" else "field"} first",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldYellow.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔒 MATCH CREATOR CONTROL", color = GoldYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Surface(color = EmeraldGreen.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                            Text("Exclusive Scorer", color = EmeraldGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Match Creator: $creatorName",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Only you (the Match Creator) will have permission to score or modify this match. No other user can score this match.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
                    Spacer(modifier = Modifier.height(10.dp))

                    if (isOfflineSolo) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("ম্যাচ মোড (MATCH MODE)", color = EmeraldGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "📱 একক / অফলাইন ম্যাচ",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "সম্পূর্ণ অফলাইনে ডিভাইস মেমোরিতে সেভ থাকবে",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            Surface(color = EmeraldGreen.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                Text("Solo Offline", color = EmeraldGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("UNIQUE MATCH CODE", color = EmeraldGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = matchCode,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 2.sp
                                )
                            }
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
                                Text("Public Viewer Code", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCreateUmpireModal) {
        var uName by remember { mutableStateOf("") }
        var uAssociation by remember { mutableStateOf("") }
        var uPhone by remember { mutableStateOf("") }
        var uPhotoUrl by remember { mutableStateOf<String?>(null) }
        var uCert by remember { mutableStateOf("Club / Local") }

        val newUmpirePhotoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val encoded = com.example.util.ImagePickerHelper.compressUriToBase64Async(context, uri)
                    if (encoded != null) {
                        uPhotoUrl = encoded
                    } else {
                        Toast.makeText(context, "ছবি প্রসেস করা যায়নি, অন্য ছবি চেষ্টা করুন", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showCreateUmpireModal = false },
            title = { Text("নতুন আম্পায়ার নিবন্ধন ও ছবি যুক্তকরণ", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, if (uPhotoUrl != null) EmeraldGreen else MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable {
                                    newUmpirePhotoPicker.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!uPhotoUrl.isNullOrEmpty()) {
                                val resolvedNewUmpirePhoto = remember(uPhotoUrl) { com.example.util.ImageModelResolver.resolveForCoil(uPhotoUrl) }
                                coil.compose.AsyncImage(
                                    model = resolvedNewUmpirePhoto,
                                    contentDescription = "Umpire Photo",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(22.dp))
                                    Text("+ছবি", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = uName,
                                onValueChange = { uName = it },
                                label = { Text("আম্পায়ারের পুরো নাম *") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uAssociation,
                        onValueChange = { uAssociation = it },
                        label = { Text("Association / League (বোর্ড বা লিগ)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uPhone,
                        onValueChange = { uPhone = it },
                        label = { Text("Phone Number (ফোন নম্বর - ঐচ্ছিক)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (uName.trim().isBlank()) {
                            Toast.makeText(context, "আম্পায়ারের নাম লিখুন", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val currentUserId = creatorUid.ifBlank { container.authRepository.getCurrentUserId() }
                        val newUmpire = com.example.data.model.Umpire(
                            fullName = uName.trim(),
                            association = uAssociation.trim(),
                            phone = uPhone.trim(),
                            photoUrl = uPhotoUrl,
                            certificationLevel = uCert,
                            creatorId = currentUserId
                        )
                        scope.launch {
                            try {
                                container.umpireRepository.saveUmpire(newUmpire)
                                onUmpire1Change(newUmpire.fullName)
                                showCreateUmpireModal = false
                                Toast.makeText(context, "আম্পায়ার সংরক্ষিত ও নির্বাচিত হয়েছে!", Toast.LENGTH_SHORT).show()
                            } catch (t: Throwable) {
                                Toast.makeText(context, "আম্পায়ার সেভ করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("সংরক্ষণ করুন")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateUmpireModal = false }) {
                    Text("বাতিল")
                }
            }
        )
    }
}
