package com.example.ui.screens.scoring

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.zIndex
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import com.example.ui.theme.CricUpIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BallEvent
import com.example.data.model.DismissalType
import com.example.data.model.ExtraType
import com.example.data.model.InningsScorecard
import com.example.data.model.Match
import com.example.data.model.MatchPlayerSnapshot
import com.example.data.model.MatchStatus
import com.example.data.model.PlayerRoles
import com.example.di.AppContainer
import com.example.ui.components.BallBadge
import com.example.ui.components.CricUpAvatar
import com.example.ui.components.SyncStatusChip
import com.example.ui.theme.BoundaryBlue
import com.example.ui.theme.CardBorder
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.LiveRed
import com.example.ui.theme.SixerPurple
import com.example.ui.theme.SlateCardBg
import com.example.ui.theme.StadiumCyanLight
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextWhite
import com.example.ui.theme.WicketRed
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScoringScreen(
    matchId: String,
    container: AppContainer,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val matchFlow = remember(matchId) { container.matchRepository.getMatchFlowById(matchId) }
    val matchState by matchFlow.collectAsState(initial = null)
    val pendingCountFlow = remember(matchId) { container.scoringRepository.getPendingCountFlow(matchId) }
    val pendingCount by pendingCountFlow.collectAsState(initial = 0)
    val activeInningsNum = matchState?.currentInningsNumber ?: 1
    val ballEventsFlow = remember(matchId, activeInningsNum) {
        container.scoringRepository.getBallEventsFlow(matchId, activeInningsNum)
    }
    val ballEvents by ballEventsFlow.collectAsState(initial = emptyList())

    val currentUser by remember(container) { container.authRepository.getCurrentUserFlow() }.collectAsState()
    val currentUserId = currentUser?.uid?.takeIf { it.isNotBlank() }
        ?: container.authRepository.getCurrentUserId()

    var showWicketDialog by remember { mutableStateOf(false) }
    var showNewBatsmanDialog by remember { mutableStateOf(false) }
    var showOpeningSelectionDialog by remember { mutableStateOf(false) }
    var showEditBallDialog by remember { mutableStateOf(false) }
    var selectedBallToEdit by remember { mutableStateOf<BallEvent?>(null) }
    var showBowlerDialog by remember { mutableStateOf(false) }
    var showNoBallDialog by remember { mutableStateOf(false) }
    var showWideDialog by remember { mutableStateOf(false) }
    var showByesLegByesDialog by remember { mutableStateOf(false) }
    var showPenaltyDialog by remember { mutableStateOf(false) }
    var selectedByeType by remember { mutableStateOf(ExtraType.BYE) }
    var showManualOverDialog by remember { mutableStateOf(false) }
    var showReconciliationDialog by remember { mutableStateOf(false) }
    var showEndInningsDialog by remember { mutableStateOf(false) }
    var showFullOverTimelineDialog by remember { mutableStateOf(false) }
    var showSubScorersDialog by remember { mutableStateOf(false) }
    var isRecordingBallAction by remember { mutableStateOf(false) }
    var activeAnimationEvent by remember { mutableStateOf<com.example.ui.components.ScoreAnimationEvent?>(null) }

    val sharedPrefs = remember { context.getSharedPreferences("cricup_prefs", android.content.Context.MODE_PRIVATE) }
    var isCommentaryEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_commentary_enabled", true))
    }
    var commentaryLang by remember {
        mutableStateOf(sharedPrefs.getString("pref_commentary_lang", "bn") ?: "bn")
    }

    val latestBallEvent by container.scoringRepository.latestBallEventFlow.collectAsState(initial = null)
    var showCommentaryPopup by remember { mutableStateOf(false) }
    var activeCommentaryBall by remember { mutableStateOf<BallEvent?>(null) }

    LaunchedEffect(latestBallEvent, isCommentaryEnabled) {
        val ball = latestBallEvent
        if (isCommentaryEnabled && ball != null && ball.matchId == matchId && ball.commentary.isNotBlank()) {
            activeCommentaryBall = ball
            showCommentaryPopup = true
            kotlinx.coroutines.delay(4500)
            showCommentaryPopup = false
        } else if (!isCommentaryEnabled) {
            showCommentaryPopup = false
        }
    }

    LaunchedEffect(matchId) {
        try {
            container.scoringRepository.reconcileMatchFromLocalBalls(matchId)
            container.scoringRepository.syncPendingEvents(matchId)
        } catch (t: Throwable) {
            android.util.Log.w("LiveScoring", "Auto sync/reconcile on start: ${t.message}")
        }
    }

    val match = matchState

    if (match == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading Match Data...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val isHeadScorer = match.isOfflineScored || !match.isPublic || (currentUserId.isNotBlank() && (
        match.effectiveOwnerUid.isBlank() ||
        match.isHeadScorer(currentUserId) ||
        match.effectiveOwnerUid.startsWith("guest_")
    ))
    val isSubScorer = !isHeadScorer && match.canUserScore(currentUserId)
    val canScore = isHeadScorer || isSubScorer
    if (!canScore) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Access Restricted",
                        tint = WicketRed,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "Match Scorer Control",
                        color = GoldYellow,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Scoring operations for this match are restricted to Head Scorer (${match.ownerName}) and authorized Sub Scorers.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    if (!match.isOfflineOnlyMatch() && match.matchCode.isNotBlank()) {
                        Text(
                            "You can follow this match in real-time on the Public Live Scoreboard using Match Code: ${match.matchCode}",
                            color = EmeraldGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Back to Scorecard", color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    val activeInnings = match.activeInnings
    val battingTeam = if (match.currentInningsNumber == 1) {
        if (match.battingFirstTeamId == match.teamA.id) match.teamA else match.teamB
    } else {
        if (match.battingFirstTeamId == match.teamA.id) match.teamB else match.teamA
    }
    val bowlingTeam = if (battingTeam.id == match.teamA.id) match.teamB else match.teamA

    val allDbPlayers by remember(currentUserId) {
        container.playerRepository.getMyPlayersFlow(currentUserId)
    }.collectAsState(initial = emptyList())
    val dbPlayersById = remember(allDbPlayers) { allDbPlayers.associateBy { it.id } }
    val dbPlayersByName = remember(allDbPlayers) {
        allDbPlayers.associateBy { it.fullName.trim().lowercase() }
    }

    // Strictly only players selected for this match (playing XI or selected match squad)
    val battingSquad = remember(
        match.currentInningsNumber,
        match.battingFirstTeamId,
        match.playingXI_A,
        match.playingXI_B,
        match.fullSquad_A,
        match.fullSquad_B,
        battingTeam.id,
        match.team1CaptainId,
        match.team2CaptainId,
        allDbPlayers
    ) {
        val matchSelectedBatters = if (match.currentInningsNumber == 1) {
            val xi = if (match.battingFirstTeamId == match.teamA.id) match.playingXI_A else match.playingXI_B
            val sq = if (match.battingFirstTeamId == match.teamA.id) match.fullSquad_A else match.fullSquad_B
            if (xi.isNotEmpty()) xi else sq
        } else {
            val xi = if (match.battingFirstTeamId == match.teamA.id) match.playingXI_B else match.playingXI_A
            val sq = if (match.battingFirstTeamId == match.teamA.id) match.fullSquad_B else match.fullSquad_A
            if (xi.isNotEmpty()) xi else sq
        }
        val baseList = if (matchSelectedBatters.isNotEmpty()) {
            matchSelectedBatters.map { p ->
                val dbMatch = (if (p.id.isNotBlank()) dbPlayersById[p.id] else null)
                    ?: dbPlayersByName[p.name.trim().lowercase()]
                p.copy(photoUrl = dbMatch?.photoUrl ?: p.photoUrl)
            }
        } else {
            allDbPlayers.filter { it.teamIds.contains(battingTeam.id) }.map { p ->
                MatchPlayerSnapshot(
                    id = p.id,
                    name = p.fullName,
                    role = p.primaryRole,
                    isCaptain = p.id == match.team1CaptainId || p.id == match.team2CaptainId,
                    photoUrl = p.photoUrl
                )
            }
        }
        baseList.distinctBy { if (it.id.isNotBlank()) it.id else it.name.trim().lowercase() }.filter { it.name.isNotBlank() }
    }

    // Strictly only players selected for this match (playing XI or selected match squad)
    val bowlingSquad = remember(
        match.currentInningsNumber,
        match.battingFirstTeamId,
        match.playingXI_A,
        match.playingXI_B,
        match.fullSquad_A,
        match.fullSquad_B,
        bowlingTeam.id,
        match.team1CaptainId,
        match.team2CaptainId,
        allDbPlayers
    ) {
        val matchSelectedBowlers = if (match.currentInningsNumber == 1) {
            val xi = if (match.battingFirstTeamId == match.teamA.id) match.playingXI_B else match.playingXI_A
            val sq = if (match.battingFirstTeamId == match.teamA.id) match.fullSquad_B else match.fullSquad_A
            if (xi.isNotEmpty()) xi else sq
        } else {
            val xi = if (match.battingFirstTeamId == match.teamA.id) match.playingXI_A else match.playingXI_B
            val sq = if (match.battingFirstTeamId == match.teamA.id) match.fullSquad_A else match.fullSquad_B
            if (xi.isNotEmpty()) xi else sq
        }
        val baseList = if (matchSelectedBowlers.isNotEmpty()) {
            matchSelectedBowlers.map { p ->
                val dbMatch = (if (p.id.isNotBlank()) dbPlayersById[p.id] else null)
                    ?: dbPlayersByName[p.name.trim().lowercase()]
                p.copy(photoUrl = dbMatch?.photoUrl ?: p.photoUrl)
            }
        } else {
            allDbPlayers.filter { it.teamIds.contains(bowlingTeam.id) }.map { p ->
                MatchPlayerSnapshot(
                    id = p.id,
                    name = p.fullName,
                    role = p.primaryRole,
                    isCaptain = p.id == match.team1CaptainId || p.id == match.team2CaptainId,
                    photoUrl = p.photoUrl
                )
            }
        }
        baseList.distinctBy { if (it.id.isNotBlank()) it.id else it.name.trim().lowercase() }.filter { it.name.isNotBlank() }
    }

    val captainIds = remember(
        match.team1CaptainId,
        match.team2CaptainId,
        match.playingXI_A,
        match.playingXI_B,
        match.fullSquad_A,
        match.fullSquad_B
    ) {
        buildSet {
            if (match.team1CaptainId.isNotBlank()) add(match.team1CaptainId)
            if (match.team2CaptainId.isNotBlank()) add(match.team2CaptainId)
            match.playingXI_A.forEach { if (it.isCaptain && it.id.isNotBlank()) add(it.id) }
            match.playingXI_B.forEach { if (it.isCaptain && it.id.isNotBlank()) add(it.id) }
            match.fullSquad_A.forEach { if (it.isCaptain && it.id.isNotBlank()) add(it.id) }
            match.fullSquad_B.forEach { if (it.isCaptain && it.id.isNotBlank()) add(it.id) }
        }
    }
    val isPlayerCaptain: (String) -> Boolean = remember(captainIds) {
        { pId -> pId.isNotBlank() && captainIds.contains(pId) }
    }

    // Auto-prompt opening batsmen & bowler if not set at start of innings
    LaunchedEffect(match.currentInningsNumber, match.currentStrikerId, match.status) {
        if (match.currentStrikerId.isNullOrEmpty() && (match.status == MatchStatus.LIVE || match.status == MatchStatus.UPCOMING)) {
            showOpeningSelectionDialog = true
        }
    }

    // Auto-prompt mandatory bowler change when an over finishes (currentBowlerId is null)
    LaunchedEffect(match.currentBowlerId, match.status, showOpeningSelectionDialog) {
        if (!showOpeningSelectionDialog && match.currentBowlerId.isNullOrEmpty() &&
            (match.status == MatchStatus.LIVE || match.status == MatchStatus.UPCOMING) &&
            !match.currentStrikerId.isNullOrEmpty()
        ) {
            showBowlerDialog = true
        }
    }

    val allUmpires by remember(currentUserId) {
        container.umpireRepository.getMyUmpiresFlow(currentUserId)
    }.collectAsState(initial = emptyList())
    val umpirePhotosByName = remember(allUmpires) {
        allUmpires.associate { it.fullName.trim().lowercase() to it.photoUrl }
    }
    val striker = remember(activeInnings, match.currentStrikerId) {
        activeInnings?.batters?.find { it.playerId == match.currentStrikerId }
    }
    val nonStriker = remember(activeInnings, match.currentNonStrikerId) {
        activeInnings?.batters?.find { it.playerId == match.currentNonStrikerId }
    }
    val bowler = remember(activeInnings, match.currentBowlerId) {
        activeInnings?.bowlers?.find { it.playerId == match.currentBowlerId }
    }

    val strikerPhoto = remember(battingSquad, dbPlayersById, dbPlayersByName, match.currentStrikerId, match.currentStrikerName) {
        val nameKey = match.currentStrikerName?.trim()?.lowercase()
        battingSquad.find { it.id == match.currentStrikerId || (nameKey != null && it.name.trim().lowercase() == nameKey) }?.photoUrl
            ?: dbPlayersById[match.currentStrikerId]?.photoUrl
            ?: nameKey?.let { dbPlayersByName[it]?.photoUrl }
    }
    val nonStrikerPhoto = remember(battingSquad, dbPlayersById, dbPlayersByName, match.currentNonStrikerId, match.currentNonStrikerName) {
        val nameKey = match.currentNonStrikerName?.trim()?.lowercase()
        battingSquad.find { it.id == match.currentNonStrikerId || (nameKey != null && it.name.trim().lowercase() == nameKey) }?.photoUrl
            ?: dbPlayersById[match.currentNonStrikerId]?.photoUrl
            ?: nameKey?.let { dbPlayersByName[it]?.photoUrl }
    }
    val bowlerPhoto = remember(bowlingSquad, dbPlayersById, dbPlayersByName, match.currentBowlerId, match.currentBowlerName) {
        val nameKey = match.currentBowlerName?.trim()?.lowercase()
        bowlingSquad.find { it.id == match.currentBowlerId || (nameKey != null && it.name.trim().lowercase() == nameKey) }?.photoUrl
            ?: dbPlayersById[match.currentBowlerId]?.photoUrl
            ?: nameKey?.let { dbPlayersByName[it]?.photoUrl }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = EmeraldGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen)
                            ) {
                                Text(
                                    text = "✏️ You are scoring",
                                    color = EmeraldGreen,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (!match.isOfflineOnlyMatch() && match.matchCode.isNotBlank()) {
                                Text(
                                    text = "• ${match.matchCode}",
                                    color = GoldYellow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = match.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    // Ball-by-ball commentary quick toggle
                    IconButton(
                        onClick = {
                            val newState = !isCommentaryEnabled
                            isCommentaryEnabled = newState
                            sharedPrefs.edit().putBoolean("pref_commentary_enabled", newState).apply()
                            Toast.makeText(
                                context,
                                if (newState) "🎙️ ধারাভাষ্য পপ-আপ চালু (ON)" else "🔇 ধারাভাষ্য পপ-আপ বন্ধ (OFF)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Text(
                            text = if (isCommentaryEnabled) "🎙️" else "🔇",
                            fontSize = 18.sp
                        )
                    }

                    // Commentary Language selector (বাংলা / EN)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = EmeraldGreen.copy(alpha = 0.18f),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clickable {
                                val newLang = if (commentaryLang == "bn") "en" else "bn"
                                commentaryLang = newLang
                                sharedPrefs.edit().putString("pref_commentary_lang", newLang).apply()
                                Toast.makeText(
                                    context,
                                    if (newLang == "bn") "ধারাভাষ্যের ভাষা: বাংলা" else "Commentary: English",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    ) {
                        Text(
                            text = if (commentaryLang == "bn") "বাংলা" else "EN",
                            color = GoldYellow,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }

                    if (match.isOfflineOnlyMatch()) {
                        Surface(
                            color = EmeraldGreen.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "OFFLINE",
                                    color = EmeraldGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        if (isHeadScorer) {
                            val subCount = match.authorizedScorers.size
                            IconButton(
                                onClick = { showSubScorersDialog = true },
                                modifier = Modifier.testTag("live_sub_scorers_btn")
                            ) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Icon(
                                        imageVector = CricUpIcons.GroupAdd,
                                        contentDescription = "Manage Sub Scorers",
                                        tint = GoldYellow
                                    )
                                    if (subCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(EmeraldGreen),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = subCount.toString(),
                                                color = Color.Black,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (isSubScorer) {
                            Surface(
                                color = EmeraldGreen.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = "SUB",
                                    color = EmeraldGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                        if (match.matchCode.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    com.example.util.MatchShareHelper.shareMatch(context, match)
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = "Share Match Code", tint = GoldYellow)
                            }
                        }
                        SyncStatusChip(
                            isOffline = false,
                            pendingCount = pendingCount,
                            onManualSync = {
                                scope.launch {
                                    try {
                                        container.scoringRepository.reconcileMatchFromLocalBalls(match.id)
                                        val count = container.scoringRepository.syncPendingEvents(match.id)
                                        Toast.makeText(context, "ক্লাউডে সফলভাবে সিঙ্ক হয়েছে! ($count events)", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "সিঙ্ক সম্পন্ন হয়েছে", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
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
            // 0. DEDICATED FIXED-HEIGHT POPUP SLOT SO SCORE PANEL NEVER SHIFTS UP OR DOWN
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = activeAnimationEvent == null && showCommentaryPopup && activeCommentaryBall != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                        .fillMaxWidth()
                ) {
                    activeCommentaryBall?.let { ball ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = DeepNavyBg.copy(alpha = 0.96f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.2.dp,
                                if (ball.isWicket) WicketRed else GoldYellow
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    BallBadge(
                                        text = ball.displayBadgeText,
                                        size = 24.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Ov ${ball.overNumber}.${ball.ballInOver} • ${com.example.util.CommentaryHelper.formatCommentary(ball, commentaryLang)}",
                                        color = TextWhite,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 3,
                                        lineHeight = 14.5.sp,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { showCommentaryPopup = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Commentary",
                                        tint = TextLightGrey,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                com.example.ui.components.ScoreEventAnimationBanner(
                    event = activeAnimationEvent,
                    onDismiss = { activeAnimationEvent = null },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
            // 1. MAIN BROADCAST SCOREBOARD
            val surfaceBg = MaterialTheme.colorScheme.surface
            val scoreboardBorderBrush = remember {
                Brush.horizontalGradient(listOf(EmeraldNeon, StadiumCyanLight, GoldYellow))
            }
            val scoreboardFillBrush = remember(surfaceBg) {
                Brush.verticalGradient(
                    colors = listOf(
                        EmeraldDark.copy(alpha = 0.12f),
                        surfaceBg
                    )
                )
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    scoreboardBorderBrush
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scoreboardFillBrush)
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // 1. Header Row: Batting team & Innings, Overs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
                                com.example.ui.components.CricUpAvatar(
                                    model = battingTeam.logoUrl,
                                    size = 40.dp,
                                    fallbackText = battingTeam.shortName,
                                    fallbackIcon = CricUpIcons.SportsCricket,
                                    borderColor = EmeraldGreen
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${battingTeam.name} (${match.currentInningsNumber}${if (match.currentInningsNumber == 1) "st" else "nd"} Inn)",
                                    color = EmeraldGreen,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Overs: ${activeInnings?.oversFormatted ?: "0.0"} / ${match.totalOvers}",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 2. Score & Key Rates Row: Score on left, CRR & Projected on right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${activeInnings?.totalRuns ?: 0}/${activeInnings?.wickets ?: 0}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val maxW = match.getMaxWicketsForInnings(match.currentInningsNumber)
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Text(
                                        text = " Max $maxW W ",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val crr = activeInnings?.runRate ?: 0.0
                                Text(
                                    text = "CRR: " + String.format(Locale.US, "%.2f", crr),
                                    color = GoldDark,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (match.currentInningsNumber == 1) {
                                    val projected = activeInnings?.calculateProjectedScore(match.totalOvers) ?: 0
                                    Text(
                                        text = "Proj: $projected",
                                        color = EmeraldGreen,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 3. Status Bar: Reconciliation + Extras breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isReconciled = activeInnings?.isScoreReconciled == true
                            Surface(
                                color = if (isReconciled) EmeraldGreen.copy(alpha = 0.2f) else GoldYellow.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(4.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isReconciled) EmeraldGreen else GoldYellow),
                                modifier = Modifier.clickable { showReconciliationDialog = true }
                            ) {
                                Text(
                                    text = if (isReconciled) " MCC Reconciled ✓ " else " MCC Audit ⚠ ",
                                    fontSize = 10.sp,
                                    color = if (isReconciled) EmeraldGreen else GoldYellow,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }

                            Text(
                                text = "Extras: ${activeInnings?.totalExtras ?: 0} (wd ${activeInnings?.wides ?: 0}, nb ${activeInnings?.noBalls ?: 0}, b ${activeInnings?.byes ?: 0}, lb ${activeInnings?.legByes ?: 0})",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        // 4. Over summary row (Current over balls + mid-over bowler spell label)
                        val currentLegal = activeInnings?.legalBalls ?: 0
                        val sortedEvents = remember(ballEvents) { ballEvents.sortedBy { it.ballSeq } }
                        val lastIsCountedLegal = sortedEvents.lastOrNull()?.let {
                            it.isLegalDelivery && it.dismissalType != DismissalType.RETIRED_HURT && it.dismissalType != DismissalType.RETIRED_OUT
                        } == true
                        val targetOverIdx = if (currentLegal > 0 && currentLegal % 6 == 0 && lastIsCountedLegal) {
                            (currentLegal / 6) - 1
                        } else {
                            currentLegal / 6
                        }
                        val thisOverBalls = remember(sortedEvents, targetOverIdx) {
                            sortedEvents.filter { it.overNumber == targetOverIdx }
                        }
                        val thisOverBowlers = remember(thisOverBalls) {
                            thisOverBalls.map { it.bowlerName }.filter { it.isNotBlank() }.distinct()
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "This Over: ", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        items(
                                            items = thisOverBalls.ifEmpty { sortedEvents.takeLast(6) },
                                            key = { it.id }
                                        ) { ball ->
                                            BallBadge(
                                                text = ball.displayBadgeText,
                                                size = 30.dp,
                                                onClick = {
                                                    selectedBallToEdit = ball
                                                    showEditBallDialog = true
                                                }
                                            )
                                        }
                                    }
                                }
                                if (thisOverBowlers.size > 1) {
                                    Text(
                                        text = "🎯 বোলার স্পেল: ${thisOverBowlers.joinToString(" + ")}",
                                        color = GoldYellow,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            TextButton(
                                onClick = { showFullOverTimelineDialog = true },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("full_over_timeline_btn")
                            ) {
                                Text("All Overs 📋", color = EmeraldGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // 5. Match Umpires Row (with photo avatars)
                        if (match.umpire1.isNotBlank() || match.umpire2.isNotBlank() || match.umpire3.isNotBlank()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Text("Officials:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                }
                                if (match.umpire1.isNotBlank()) {
                                    item(key = "ump1") {
                                        val u1Photo = umpirePhotosByName[match.umpire1.trim().lowercase()]
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            com.example.ui.components.CricUpAvatar(
                                                model = u1Photo,
                                                size = 28.dp,
                                                fallbackText = match.umpire1.take(2),
                                                fallbackIcon = Icons.Default.Person
                                            )
                                            Text(match.umpire1, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                        }
                                    }
                                }
                                if (match.umpire2.isNotBlank()) {
                                    item(key = "ump2") {
                                        val u2Photo = umpirePhotosByName[match.umpire2.trim().lowercase()]
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text("•", color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
                                            com.example.ui.components.CricUpAvatar(
                                                model = u2Photo,
                                                size = 28.dp,
                                                fallbackText = match.umpire2.take(2),
                                                fallbackIcon = Icons.Default.Person
                                            )
                                            Text(match.umpire2, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                        }
                                    }
                                }
                                if (match.umpire3.isNotBlank()) {
                                    item(key = "ump3") {
                                        val u3Photo = umpirePhotosByName[match.umpire3.trim().lowercase()]
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text("•", color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
                                            com.example.ui.components.CricUpAvatar(
                                                model = u3Photo,
                                                size = 28.dp,
                                                fallbackText = match.umpire3.take(2),
                                                fallbackIcon = Icons.Default.Person
                                            )
                                            Text("${match.umpire3} (3rd)", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. CONTINUOUS 2ND INNINGS REQUIREMENT PANEL (DIRECTLY BELOW SCOREBOARD)
            if (match.currentInningsNumber == 2 && match.targetRuns != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val target = match.targetRuns
                val runsNeeded = match.getRunsRemaining()
                val ballsLeft = match.getBallsRemaining()
                val rrr = match.getRequiredRunRate()
                val crr = activeInnings?.runRate ?: 0.0

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🎯 TARGET: $target",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (runsNeeded > 0) "Need $runsNeeded runs in $ballsLeft balls" else "TARGET ACHIEVED!",
                                color = if (runsNeeded > 0) MaterialTheme.colorScheme.onSurface else EmeraldGreen,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "RRR: " + String.format(Locale.US, "%.2f", rrr),
                                color = GoldDark,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "CRR: " + String.format(Locale.US, "%.2f", crr),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Overs Left: ${ballsLeft / 6}.${ballsLeft % 6}",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. BATTERS & BOWLER STATUS CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Striker
                    val isStrikerCapt = isPlayerCaptain(match.currentStrikerId ?: "")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
                            com.example.ui.components.CricUpAvatar(
                                model = strikerPhoto,
                                size = 32.dp,
                                fallbackText = match.currentStrikerName?.take(2),
                                fallbackIcon = Icons.Default.Person,
                                borderColor = EmeraldGreen
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${match.currentStrikerName ?: "Striker"} *",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (isStrikerCapt) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(color = GoldYellow, shape = RoundedCornerShape(3.dp)) {
                                    Text(" C ", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "${striker?.runs ?: 0} (${striker?.balls ?: 0})",
                                    color = EmeraldGreen,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                                Surface(
                                    color = GoldYellow.copy(alpha = 0.18f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.8.dp, GoldYellow.copy(alpha = 0.65f))
                                ) {
                                    Text(
                                        text = "SR ${String.format(Locale.US, "%.1f", striker?.strikeRate ?: 0.0)}",
                                        color = GoldDark,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "4s:${striker?.fours ?: 0} • 6s:${striker?.sixes ?: 0}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }

                    // Non-Striker
                    val isNonStrikerCapt = isPlayerCaptain(match.currentNonStrikerId ?: "")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
                            com.example.ui.components.CricUpAvatar(
                                model = nonStrikerPhoto,
                                size = 32.dp,
                                fallbackText = match.currentNonStrikerName?.take(2),
                                fallbackIcon = Icons.Default.Person
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${match.currentNonStrikerName ?: "Non-Striker"}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (isNonStrikerCapt) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(color = GoldYellow, shape = RoundedCornerShape(3.dp)) {
                                    Text(" C ", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "${nonStriker?.runs ?: 0} (${nonStriker?.balls ?: 0})",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = "SR ${String.format(Locale.US, "%.1f", nonStriker?.strikeRate ?: 0.0)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = "4s:${nonStriker?.fours ?: 0} • 6s:${nonStriker?.sixes ?: 0}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))

                    // Bowler
                    val isBowlerCapt = isPlayerCaptain(match.currentBowlerId ?: "")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f, fill = false), verticalAlignment = Alignment.CenterVertically) {
                            com.example.ui.components.CricUpAvatar(
                                model = bowlerPhoto,
                                size = 32.dp,
                                fallbackText = match.currentBowlerName?.take(2),
                                fallbackIcon = CricUpIcons.SportsCricket,
                                borderColor = StadiumCyanLight
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${match.currentBowlerName ?: "Bowler"}",
                                color = StadiumCyanLight,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (isBowlerCapt) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(color = GoldYellow, shape = RoundedCornerShape(3.dp)) {
                                    Text(" C ", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${bowler?.oversString ?: "0.0"} ov • ${bowler?.runsConceded ?: 0}R • ${bowler?.wickets ?: 0}W (E: ${String.format(Locale.US, "%.1f", bowler?.economy ?: 0.0)})",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. SCORING PAD CONTROLS
            val isAuthorizedScorer = match.canUserScore(currentUserId)

            val checkCanScore: () -> Boolean = {
                if (!isAuthorizedScorer) {
                    Toast.makeText(context, "শুধুমাত্র Head Scorer (${match.ownerName.ifEmpty { "ম্যাচ স্কোরার" }}) বা অনুমোদিত Sub Scorers স্কোর এন্ট্রি করতে পারবেন।", Toast.LENGTH_SHORT).show()
                    false
                } else {
                    val maxWkts = match.getMaxWicketsForInnings(match.currentInningsNumber)
                    when {
                        match.status == MatchStatus.INNINGS_BREAK -> {
                            Toast.makeText(context, "১ম ইনিংস সমাপ্ত হয়েছে! ২য় ইনিংস শুরু করতে কনফার্ম করুন।", Toast.LENGTH_SHORT).show()
                            false
                        }
                        match.status == MatchStatus.COMPLETED -> {
                            Toast.makeText(context, "ম্যাচ ইতোমধ্যে সম্পন্ন হয়েছে!", Toast.LENGTH_SHORT).show()
                            false
                        }
                        activeInnings != null && activeInnings.wickets >= maxWkts -> {
                            Toast.makeText(context, "দল অল-আউট হয়েছে! ইনিংস সমাপ্ত।", Toast.LENGTH_SHORT).show()
                            false
                        }
                        activeInnings != null && (activeInnings.legalBalls / 6) >= match.totalOvers -> {
                            Toast.makeText(context, "নির্ধারিত ওভার সমাপ্ত হয়েছে! ইনিংস সমাপ্ত।", Toast.LENGTH_SHORT).show()
                            false
                        }
                        match.currentBowlerId.isNullOrEmpty() -> {
                            Toast.makeText(context, "ওভার সমাপ্ত! পরবর্তী বোলার নির্বাচন করুন (Select new bowler first)", Toast.LENGTH_SHORT).show()
                            showBowlerDialog = true
                            false
                        }
                        else -> true
                    }
                }
            }

            if (!isAuthorizedScorer) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp)
                        .padding(vertical = 10.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, StadiumCyanLight.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = CricUpIcons.Visibility,
                            contentDescription = null,
                            tint = StadiumCyanLight,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "👁️ লাইভ দর্শক মোড (Live Spectator Mode)",
                            color = GoldYellow,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "এই ম্যাচের স্কোরিং শুধুমাত্র অনুমোদিত স্কোরার (${match.ownerName.ifEmpty { "ম্যাচ স্কোরার" }}) এর নিয়ন্ত্রণে রয়েছে। স্কোর আপডেট হওয়া মাত্রই আপনার ফোনে স্বয়ংক্রিয়ভাবে লাইভ দেখতে পাবেন।",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.5.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { showFullOverTimelineDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("বল বাই বল ওভার টাইমলাইন 📋", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. NORMAL RUNS ROW: Dot (0), 1, 2, 3, 4, 5, 6
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val runOptions = listOf(0, 1, 2, 3, 4, 5, 6)
                        runOptions.forEach { run ->
                            val isFour = run == 4
                            val isSix = run == 6
                            val btnBg = when {
                                isFour -> BoundaryBlue
                                isSix -> SixerPurple
                                run == 0 -> MaterialTheme.colorScheme.surfaceVariant
                                else -> MaterialTheme.colorScheme.surface
                            }
                            val btnTextColor = when {
                                isFour || isSix -> Color.White
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(btnBg)
                                    .border(1.dp, if (isFour || isSix) Color.Transparent else MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isRecordingBallAction) return@clickable
                                        if (!checkCanScore()) return@clickable
                                        isRecordingBallAction = true
                                        if (run == 4) {
                                            activeAnimationEvent = com.example.ui.components.ScoreAnimationEvent(
                                                type = com.example.ui.components.ScoreEventType.FOUR,
                                                title = "FOUR! 🏏 4 RUNS",
                                                subtitle = "Boundary scored by ${match.currentStrikerName ?: "Batter"}"
                                            )
                                        } else if (run == 6) {
                                            activeAnimationEvent = com.example.ui.components.ScoreAnimationEvent(
                                                type = com.example.ui.components.ScoreEventType.SIX,
                                                title = "SIX! 🚀 MAXIMUM 6 RUNS",
                                                subtitle = "Massive six over the ropes by ${match.currentStrikerName ?: "Batter"}"
                                            )
                                        }
                                        scope.launch {
                                            try {
                                                val res = container.scoringRepository.recordBallEvent(
                                                    match = match,
                                                    runsOffBat = run,
                                                    extraType = ExtraType.NONE,
                                                    extraRuns = 0,
                                                    isWicket = false,
                                                    dismissalType = null,
                                                    dismissedPlayerId = null,
                                                    dismissedPlayerName = null,
                                                    incomingBatterId = null,
                                                    incomingBatterName = null,
                                                    assistingPlayerName = null,
                                                    scorerId = currentUserId
                                                )
                                                if (res.isFailure) {
                                                    Toast.makeText(context, res.exceptionOrNull()?.localizedMessage ?: "স্কোর রেকর্ড করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("LiveScoring", "Error recording run $run", e)
                                                Toast.makeText(context, "ত্রুটি: ${e.localizedMessage ?: "অপ্রত্যাশিত সমস্যা"}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isRecordingBallAction = false
                                            }
                                        }
                                    }
                                    .testTag("score_btn_$run"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (run == 0) "•" else "$run",
                                    color = btnTextColor,
                                    fontSize = if (run == 0) 22.sp else 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // 2. EXTRAS ROW: WIDE, NO BALL, BYE, LEG BYE, PENALTY (Black text for sharp legibility)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // WIDE
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(GoldYellow.copy(alpha = 0.85f))
                                .border(1.dp, GoldYellow, RoundedCornerShape(8.dp))
                            .clickable {
                                if (!checkCanScore()) return@clickable
                                showWideDialog = true
                            }
                            .testTag("extra_btn_WIDE"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "WIDE",
                                color = Color.Black,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // NO BALL
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(GoldYellow.copy(alpha = 0.85f))
                                .border(1.dp, GoldYellow, RoundedCornerShape(8.dp))
                            .clickable {
                                if (!checkCanScore()) return@clickable
                                showNoBallDialog = true
                            }
                            .testTag("extra_btn_NO_BALL"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "NO BALL",
                                color = Color.Black,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // BYE
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(GoldYellow.copy(alpha = 0.85f))
                                .border(1.dp, GoldYellow, RoundedCornerShape(8.dp))
                            .clickable {
                                if (!checkCanScore()) return@clickable
                                selectedByeType = ExtraType.BYE
                                showByesLegByesDialog = true
                            }
                            .testTag("extra_btn_BYE"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "BYE",
                                color = Color.Black,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // LEG BYE
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(GoldYellow.copy(alpha = 0.85f))
                                .border(1.dp, GoldYellow, RoundedCornerShape(8.dp))
                            .clickable {
                                if (!checkCanScore()) return@clickable
                                selectedByeType = ExtraType.LEG_BYE
                                showByesLegByesDialog = true
                            }
                            .testTag("extra_btn_LEG_BYE"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "LEG BYE",
                                color = Color.Black,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // PENALTY (Req 18)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(GoldYellow.copy(alpha = 0.85f))
                                .border(1.dp, GoldYellow, RoundedCornerShape(8.dp))
                            .clickable {
                                if (!checkCanScore()) return@clickable
                                showPenaltyDialog = true
                            }
                            .testTag("extra_btn_PENALTY"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "PENALTY",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // 3. PRIMARY WICKET BUTTON
                    Button(
                        onClick = {
                            if (!checkCanScore()) return@Button
                            showWicketDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("score_btn_wicket"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WicketRed),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = "⚡ OUT / WICKET",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // 4. UTILITY ACTIONS: Undo, Swap Strike, Switch Bowler, End Over, End Innings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // UNDO
                        Button(
                            onClick = {
                                if (isRecordingBallAction) return@Button
                                isRecordingBallAction = true
                                scope.launch {
                                    try {
                                        val result = container.scoringRepository.undoLastBall(match, scorerId = currentUserId)
                                        if (result.isSuccess) {
                                            Toast.makeText(context, "Last delivery undone", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, result.exceptionOrNull()?.message ?: "Undo failed", Toast.LENGTH_SHORT).show()
                                        }
                                    } finally {
                                        isRecordingBallAction = false
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .testTag("undo_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(imageVector = CricUpIcons.Undo, contentDescription = "Undo", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Undo", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
                        }

                        // SWAP STRIKE
                        Button(
                            onClick = {
                                scope.launch {
                                    container.scoringRepository.swapStrike(match.id)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .testTag("swap_strike_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(imageVector = CricUpIcons.SwapHoriz, contentDescription = "Swap", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Strike", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
                        }

                        // CHANGE BOWLER
                        Button(
                            onClick = { showBowlerDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .testTag("change_bowler_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Bowler", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Bowler", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
                        }

                        // MANUAL OVER END
                        Button(
                            onClick = { showManualOverDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .testTag("manual_over_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text("Over ⏱", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }

                        // END INNINGS
                        Button(
                            onClick = { showEndInningsDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .testTag("end_innings_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark)
                        ) {
                            Text("End 🏁", fontSize = 10.5.sp, color = Color.White, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }

    // FULL OVER-BY-OVER TIMELINE DIALOG
    if (showFullOverTimelineDialog) {
        AlertDialog(
            onDismissRequest = { showFullOverTimelineDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Over Timeline (ওভার বিবরণ)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Surface(
                        color = EmeraldGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${ballEvents.size} বল",
                            color = EmeraldGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            },
            text = {
                Box(modifier = Modifier.height(380.dp).fillMaxWidth()) {
                    com.example.ui.components.OverTimelineList(ballEvents = ballEvents)
                }
            },
            confirmButton = {
                TextButton(onClick = { showFullOverTimelineDialog = false }) {
                    Text("Close", fontWeight = FontWeight.Bold, color = EmeraldGreen)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // OPENING SELECTION MODAL (Individual Step-by-Step Selection: Striker -> Non-Striker -> Bowler)
    if (showOpeningSelectionDialog) {
        var openingStep by remember { mutableIntStateOf(1) } // 1: Striker, 2: Non-Striker, 3: Bowler
        var selectedStriker by remember { mutableStateOf<MatchPlayerSnapshot?>(null) }
        var selectedNonStriker by remember { mutableStateOf<MatchPlayerSnapshot?>(null) }
        var selectedBowler by remember { mutableStateOf<MatchPlayerSnapshot?>(null) }
        var isStartingInnings by remember { mutableStateOf(false) }

        val nonStrikerCandidates = remember(selectedStriker, battingSquad) {
            battingSquad.filter { it.id != selectedStriker?.id }
        }

        AlertDialog(
            onDismissRequest = { /* Must complete selection before starting innings */ },
            title = {
                Column {
                    Text(
                        text = when (openingStep) {
                            1 -> "১ম ধাপ: স্ট্রাইকার ব্যাটার নির্বাচন"
                            2 -> "২য় ধাপ: নন-স্ট্রাইকার ব্যাটার নির্বাচন"
                            else -> "৩য় ধাপ: উদ্বোধনী বোলার নির্বাচন"
                        },
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = when (openingStep) {
                            1 -> "ব্যাটিং দল: ${battingTeam.name} (${battingSquad.size} জনের তালিকা)"
                            2 -> "ব্যাটিং দল: ${battingTeam.name} • স্ট্রাইকার: ${selectedStriker?.name ?: "সিলেক্টেড"}"
                            else -> "বোলিং দল: ${bowlingTeam.name} (${bowlingSquad.size} জনের তালিকা)"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    when (openingStep) {
                        1 -> {
                            item {
                                Text(
                                    text = "১. স্ট্রাইকে থাকা ওপেনিং ব্যাটার সিলেক্ট করুন:",
                                    color = GoldYellow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            items(battingSquad) { p ->
                                val isSelected = selectedStriker?.id == p.id
                                val isCapt = isPlayerCaptain(p.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) EmeraldGreen.copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable { selectedStriker = p }
                                        .padding(vertical = 6.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CricUpAvatar(
                                        model = p.photoUrl,
                                        size = 28.dp,
                                        fallbackText = p.name.take(2),
                                        fallbackIcon = Icons.Default.Person,
                                        borderColor = if (isSelected) EmeraldGreen else Color.Transparent
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${PlayerRoles.getRoleIcon(p.role)} ${p.name}",
                                        color = if (isSelected) EmeraldGreen else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.5.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isCapt) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(color = GoldYellow, shape = RoundedCornerShape(4.dp)) {
                                            Text(" (C) ", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        2 -> {
                            item {
                                Text(
                                    text = "২. অপর প্রান্তে থাকা নন-স্ট্রাইকার ওপেনিং ব্যাটার সিলেক্ট করুন:",
                                    color = GoldYellow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            items(nonStrikerCandidates) { p ->
                                val isSelected = selectedNonStriker?.id == p.id
                                val isCapt = isPlayerCaptain(p.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) EmeraldGreen.copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable { selectedNonStriker = p }
                                        .padding(vertical = 6.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CricUpAvatar(
                                        model = p.photoUrl,
                                        size = 28.dp,
                                        fallbackText = p.name.take(2),
                                        fallbackIcon = Icons.Default.Person,
                                        borderColor = if (isSelected) EmeraldGreen else Color.Transparent
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${PlayerRoles.getRoleIcon(p.role)} ${p.name}",
                                        color = if (isSelected) EmeraldGreen else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.5.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isCapt) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(color = GoldYellow, shape = RoundedCornerShape(4.dp)) {
                                            Text(" (C) ", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        3 -> {
                            item {
                                Text(
                                    text = "৩. প্রথম ওভারের উদ্বোধনী বোলার সিলেক্ট করুন:",
                                    color = GoldYellow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            items(bowlingSquad) { p ->
                                val isSelected = selectedBowler?.id == p.id
                                val isCapt = isPlayerCaptain(p.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) GoldYellow.copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable { selectedBowler = p }
                                        .padding(vertical = 6.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CricUpAvatar(
                                        model = p.photoUrl,
                                        size = 28.dp,
                                        fallbackText = p.name.take(2),
                                        fallbackIcon = CricUpIcons.SportsCricket,
                                        borderColor = if (isSelected) GoldYellow else Color.Transparent
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${PlayerRoles.getRoleIcon(p.role)} ${p.name}",
                                        color = if (isSelected) GoldYellow else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.5.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isCapt) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(color = GoldYellow, shape = RoundedCornerShape(4.dp)) {
                                            Text(" (C) ", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = GoldYellow, modifier = Modifier.size(18.dp))
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
                        when (openingStep) {
                            1 -> {
                                if (selectedStriker == null) {
                                    Toast.makeText(context, "দয়া করে স্ট্রাইকার নির্বাচন করুন", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                openingStep = 2
                            }
                            2 -> {
                                if (selectedNonStriker == null) {
                                    Toast.makeText(context, "দয়া করে নন-স্ট্রাইকার নির্বাচন করুন", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                openingStep = 3
                            }
                            3 -> {
                                if (isStartingInnings) return@Button
                                val striker = selectedStriker
                                val nonStriker = selectedNonStriker
                                val bowler = selectedBowler
                                if (striker == null || nonStriker == null || bowler == null) {
                                    Toast.makeText(context, "দয়া করে সকল খেলোয়াড় নির্বাচন করুন", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isStartingInnings = true
                                scope.launch {
                                    try {
                                        val res = container.scoringRepository.selectOpeningPlayers(
                                            matchId = match.id,
                                            strikerId = striker.id,
                                            strikerName = striker.name,
                                            nonStrikerId = nonStriker.id,
                                            nonStrikerName = nonStriker.name,
                                            bowlerId = bowler.id,
                                            bowlerName = bowler.name
                                        )
                                        if (res.isSuccess) {
                                            showOpeningSelectionDialog = false
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "ত্রুটি: ${res.exceptionOrNull()?.localizedMessage ?: "ম্যাচ শুরু করতে সমস্যা হয়েছে"}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } catch (t: Throwable) {
                                        android.util.Log.e("LiveScoring", "Opening player selection failed: ${t.message}", t)
                                        Toast.makeText(context, "ত্রুটি: ${t.localizedMessage ?: "সমস্যা হয়েছে"}", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isStartingInnings = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isStartingInnings,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text(
                        text = when {
                            isStartingInnings -> "চালু হচ্ছে..."
                            openingStep == 1 -> "পরবর্তী: নন-স্ট্রাইকার ➔"
                            openingStep == 2 -> "পরবর্তী: বোলার নির্বাচন ➔"
                            else -> "ম্যাচ শুরু করুন 🏏"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                if (openingStep > 1) {
                    TextButton(onClick = { openingStep-- }) {
                        Text("⬅️ পূর্ববর্তী", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // 10 DISMISSAL TYPES WICKET DIALOG
    if (showWicketDialog) {
        var selectedDismissal by remember { mutableStateOf(DismissalType.BOWLED) }
        var dismissedBatterId by remember { mutableStateOf(match.currentStrikerId ?: "") }
        var dismissedBatterName by remember { mutableStateOf(match.currentStrikerName ?: "") }
        var fielderName by remember { mutableStateOf("") }
        var newBatterId by remember { mutableStateOf("") }
        var newBatterName by remember { mutableStateOf("") }
        var completedRunsOnWicket by remember { mutableIntStateOf(0) }
        var wicketDeliveryExtraType by remember { mutableStateOf(ExtraType.NONE) }

        val maxWickets = match.getMaxWicketsForInnings(match.currentInningsNumber)
        val currentWickets = activeInnings?.wickets ?: 0
        val isRetHurtSelected = selectedDismissal == DismissalType.RETIRED_HURT
        val isAllOutDelivery = !isRetHurtSelected && ((currentWickets + 1) >= maxWickets)

        val eligibleNewBatters = battingSquad.filter { p ->
            p.id != match.currentStrikerId &&
                    p.id != match.currentNonStrikerId &&
                    !match.dismissedPlayerIds.contains(p.id)
        }

        Dialog(
            onDismissRequest = { showWicketDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.90f),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CardBorder)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. FIXED HEADER (Never scrolls)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Record Wicket / Dismissal (আউট)",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        IconButton(
                            onClick = { showWicketDialog = false },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    // 2. SCROLLABLE BODY
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Section A: Dismissal Types List
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Dismissal Type (12 Types)",
                                color = GoldYellow,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "আইসিসি নিয়ম অনুযায়ী আউটের ধরন নির্বাচন করুন:",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )

                            val orderedDismissals = listOf(
                                DismissalType.BOWLED,
                                DismissalType.CAUGHT,
                                DismissalType.CAUGHT_BEHIND,
                                DismissalType.RUN_OUT,
                                DismissalType.LBW,
                                DismissalType.STUMPED,
                                DismissalType.RETIRED_HURT,
                                DismissalType.RETIRED_OUT,
                                DismissalType.MANKAD,
                                DismissalType.HIT_WICKET,
                                DismissalType.HANDLED_BALL,
                                DismissalType.OBSTRUCTING_FIELD,
                                DismissalType.HIT_BALL_TWICE,
                                DismissalType.TIMED_OUT
                            )

                            orderedDismissals.forEach { type ->
                                val isSelected = selectedDismissal == type
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedDismissal = type },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) WicketRed.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) WicketRed else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Text(text = type.icon, fontSize = 28.sp)

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = type.displayName,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                lineHeight = 22.sp
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            val subtitleText = when (type) {
                                                DismissalType.RETIRED_HURT -> "🏥 অসুস্থতাজনিত বিশ্রাম • পুনরায় নামতে পারবে • Wicket ও Ball কাউন্ট হবে না"
                                                DismissalType.RETIRED_OUT -> "🚶 ইচ্ছে করে বিশ্রাম / একেবারেই বের হয়ে যাবে • Wicket কাউন্ট হবে, Ball কাউন্ট হবে না"
                                                else -> if (type.isBowlerWicket) "Bowler Credit • বোলার উইকেট পাবে" else "No Bowler Credit • বোলারের উইকেট নয়"
                                            }
                                            Text(
                                                text = subtitleText,
                                                color = if (type == DismissalType.RETIRED_HURT) GoldYellow else if (type.isBowlerWicket) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .border(
                                                    width = 2.dp,
                                                    color = if (isSelected) WicketRed else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                                                    shape = CircleShape
                                                )
                                                .background(if (isSelected) WicketRed else Color.Transparent),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (selectedDismissal == DismissalType.RETIRED_HURT || selectedDismissal == DismissalType.RETIRED_OUT) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = GoldYellow.copy(alpha = 0.12f)),
                                    border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldYellow)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "🏥 অবসর গ্রহণের ধরন নিশ্চিত করুন (Retired Choice):",
                                            color = GoldYellow,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "ব্যাটার অসুস্থতাজনিত বিশ্রামে যাচ্ছেন (পরে নামতে পারবেন) নাকি ইচ্ছে করে একেবারে বের হয়ে যাচ্ছেন? উভয় ক্ষেত্রেই নিচ থেকে নতুন ব্যাটার নির্বাচন করতে হবে।",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val isHurt = selectedDismissal == DismissalType.RETIRED_HURT
                                            Button(
                                                onClick = { selectedDismissal = DismissalType.RETIRED_HURT },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isHurt) EmeraldGreen else MaterialTheme.colorScheme.surface,
                                                    contentColor = if (isHurt) Color.White else MaterialTheme.colorScheme.onSurface
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isHurt) EmeraldGreen else MaterialTheme.colorScheme.outline)
                                            ) {
                                                Text("অসুস্থ বিশ্রাম\n(Wicket ও Ball নয়)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                            }
                                            val isRetOut = selectedDismissal == DismissalType.RETIRED_OUT
                                            Button(
                                                onClick = { selectedDismissal = DismissalType.RETIRED_OUT },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isRetOut) WicketRed else MaterialTheme.colorScheme.surface,
                                                    contentColor = if (isRetOut) Color.White else MaterialTheme.colorScheme.onSurface
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isRetOut) WicketRed else MaterialTheme.colorScheme.outline)
                                            ) {
                                                Text("ইচ্ছে করে বের\n(Wicket পরবে, Ball নয়)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Section B: Dismissed Batsman Toggle
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Dismissed Batsman (কোন ব্যাটার আউট হয়েছেন):",
                                color = GoldYellow,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val isStriker = dismissedBatterId == match.currentStrikerId
                                Button(
                                    onClick = {
                                        dismissedBatterId = match.currentStrikerId ?: ""
                                        dismissedBatterName = match.currentStrikerName ?: ""
                                    },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isStriker) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        contentColor = if (isStriker) Color.White else MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = if (!isStriker) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)) else null
                                ) {
                                    Text(
                                        text = "Striker: ${match.currentStrikerName ?: "N/A"}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                val isNonStriker = dismissedBatterId == match.currentNonStrikerId
                                Button(
                                    onClick = {
                                        dismissedBatterId = match.currentNonStrikerId ?: ""
                                        dismissedBatterName = match.currentNonStrikerName ?: ""
                                    },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isNonStriker) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        contentColor = if (isNonStriker) Color.White else MaterialTheme.colorScheme.onSurface
                                    ),
                                    border = if (!isNonStriker) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)) else null
                                ) {
                                    Text(
                                        text = "Non-Striker: ${match.currentNonStrikerName ?: "N/A"}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Section C1: Completed Runs on Run Out (Req 1 & 19)
                        if (selectedDismissal == DismissalType.RUN_OUT) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "🏃 সম্পন্নকৃত রান / Completed Runs Before Run Out:",
                                    color = EmeraldGreen,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "আউটের আগে ব্যাটাররা সম্পন্ন করা রান সিলেক্ট করুন (০, ১, ২ বা ৩ রান):",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.5.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(0, 1, 2, 3).forEach { r ->
                                        val isRSelected = completedRunsOnWicket == r
                                        Button(
                                            onClick = { completedRunsOnWicket = r },
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isRSelected) EmeraldGreen else MaterialTheme.colorScheme.surface
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isRSelected) EmeraldGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = if (r == 0) "০ রান" else "$r রান",
                                                color = if (isRSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "বল ডেলিভারির ধরন / Delivery Type on this ball:",
                                    color = GoldYellow,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val types = listOf(
                                        Pair("Normal (ব্যাট)", ExtraType.NONE),
                                        Pair("Wide (ওয়াইড)", ExtraType.WIDE),
                                        Pair("No Ball", ExtraType.NO_BALL),
                                        Pair("Bye (বাই)", ExtraType.BYE),
                                        Pair("Leg Bye", ExtraType.LEG_BYE)
                                    )
                                    items(types) { (lbl, xtra) ->
                                        val isXSelected = wicketDeliveryExtraType == xtra
                                        Surface(
                                            color = if (isXSelected) EmeraldGreen else SlateCardBg,
                                            shape = RoundedCornerShape(8.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isXSelected) EmeraldGreen else CardBorder),
                                            modifier = Modifier.clickable { wicketDeliveryExtraType = xtra }
                                        ) {
                                            Text(
                                                text = lbl,
                                                fontSize = 11.5.sp,
                                                color = if (isXSelected) Color.White else TextLightGrey,
                                                fontWeight = if (isXSelected) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Section C2: Fielder / Catcher / Keeper (Req 3)
                        if (selectedDismissal == DismissalType.CAUGHT || selectedDismissal == DismissalType.CAUGHT_BEHIND ||
                            selectedDismissal == DismissalType.RUN_OUT || selectedDismissal == DismissalType.STUMPED) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Fielder / Catcher / Keeper Name (ফিল্ডার / কিপার নির্বাচন):",
                                    color = EmeraldGreen,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (bowlingSquad.isNotEmpty()) {
                                    Text(
                                        text = "ফিল্ডিং স্কোয়াড থেকে সরাসরি বেছে নিন:",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.5.sp
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(bowlingSquad) { f ->
                                            val isChosen = fielderName == f.name
                                            Surface(
                                                color = if (isChosen) EmeraldGreen else SlateCardBg,
                                                shape = RoundedCornerShape(8.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isChosen) EmeraldGreen else CardBorder),
                                                modifier = Modifier.clickable {
                                                    fielderName = if (isChosen) "" else f.name
                                                }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    if (!f.photoUrl.isNullOrEmpty()) {
                                                        val resolvedFielderPhoto = remember(f.photoUrl) { com.example.util.ImageModelResolver.resolveForCoil(f.photoUrl) }
                                                        coil.compose.AsyncImage(
                                                            model = resolvedFielderPhoto,
                                                            contentDescription = f.name,
                                                            modifier = Modifier.size(20.dp).clip(CircleShape),
                                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                    }
                                                    Text(
                                                        text = f.name,
                                                        fontSize = 11.5.sp,
                                                        color = if (isChosen) Color.White else TextLightGrey,
                                                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                }

                                OutlinedTextField(
                                    value = fielderName,
                                    onValueChange = { fielderName = it },
                                    placeholder = { Text("সহায়তাকারী ফিল্ডারের নাম লিখুন বা উপরের তালিকা থেকে চাপুন", fontSize = 13.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = EmeraldGreen,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Section D: All-out Banner OR Incoming Batter Selection
                        if (isAllOutDelivery) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = WicketRed.copy(alpha = 0.15f)),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, WicketRed),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("🚨 শেষ উইকেট! দল অল-আউট (ALL OUT)", color = WicketRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "দলে ${battingSquad.size} জন খেলোয়াড় থাকায় সর্বোচ্চ ${maxWickets} উইকেটে দল অল-আউট হবে (${currentWickets + 1}/$maxWickets Wkts)। এই আউট নিশ্চিত করলেই ১ম ইনিংস সমাপ্ত বা ম্যাচ সমাপ্ত হবে।",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.5.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "পরবর্তী ব্যাটার নির্বাচন করুন (Incoming Batter)",
                                    color = EmeraldGreen,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${eligibleNewBatters.size} জন ব্যাটার এখনও ব্যাটিং করেননি:",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp
                                )

                                if (eligibleNewBatters.isEmpty()) {
                                    Text(
                                        text = "⚠️ কোনো ব্যাটার বাকি নেই (অল-আউট)",
                                        color = WicketRed,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    eligibleNewBatters.forEach { p ->
                                        val isSelected = newBatterId == p.id
                                        val isCapt = isPlayerCaptain(p.id)
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    newBatterId = p.id
                                                    newBatterName = p.name
                                                },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) EmeraldGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) EmeraldGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CricUpAvatar(
                                                    model = p.photoUrl,
                                                    size = 32.dp,
                                                    fallbackText = p.name.take(2),
                                                    fallbackIcon = Icons.Default.Person,
                                                    borderColor = if (isSelected) EmeraldGreen else Color.Transparent
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "${PlayerRoles.getRoleIcon(p.role)} ${p.name}",
                                                    color = if (isSelected) EmeraldGreen else MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 16.sp,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (isCapt) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(color = GoldYellow, shape = RoundedCornerShape(4.dp)) {
                                                        Text(" (C) ", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                                    }
                                                }
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = EmeraldGreen,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. FIXED FOOTER (Always visible)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    val isValidationMet = isAllOutDelivery || eligibleNewBatters.isEmpty() || newBatterId.isNotBlank()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!isValidationMet) {
                            Text(
                                text = "⚠️ অনুগ্রহ করে পরবর্তী ব্যাটার নির্বাচন করুন",
                                color = GoldYellow,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        Button(
                            onClick = {
                                if (!isValidationMet) return@Button

                                val incomingId = if (!isAllOutDelivery && newBatterId.isNotBlank()) newBatterId else null
                                val incomingName = if (!isAllOutDelivery && newBatterName.isNotBlank()) newBatterName else null

                                val isRetHurt = selectedDismissal == DismissalType.RETIRED_HURT
                                val isRunOut = selectedDismissal == DismissalType.RUN_OUT

                                val runsBat = if (isRunOut && wicketDeliveryExtraType == ExtraType.NONE) completedRunsOnWicket else 0
                                val xtraType = if (isRunOut) wicketDeliveryExtraType else ExtraType.NONE
                                val xtraRuns = if (isRunOut) {
                                    when (wicketDeliveryExtraType) {
                                        ExtraType.WIDE, ExtraType.NO_BALL -> 1 + completedRunsOnWicket
                                        ExtraType.BYE, ExtraType.LEG_BYE -> completedRunsOnWicket
                                        else -> 0
                                    }
                                } else 0

                                if (!isRetHurt) {
                                    activeAnimationEvent = com.example.ui.components.ScoreAnimationEvent(
                                        type = com.example.ui.components.ScoreEventType.WICKET,
                                        title = "WICKET! ⚡ OUT!",
                                        subtitle = "${selectedDismissal.displayName} — $dismissedBatterName"
                                    )
                                }
                                scope.launch {
                                    try {
                                        val res = container.scoringRepository.recordBallEvent(
                                            match = match,
                                            runsOffBat = runsBat,
                                            extraType = xtraType,
                                            extraRuns = xtraRuns,
                                            isWicket = if (isRetHurt) false else true,
                                            dismissalType = selectedDismissal,
                                            dismissedPlayerId = dismissedBatterId,
                                            dismissedPlayerName = dismissedBatterName,
                                            incomingBatterId = incomingId,
                                            incomingBatterName = incomingName,
                                            assistingPlayerName = fielderName.ifBlank { null },
                                            scorerId = currentUserId
                                        )
                                        if (res.isFailure) {
                                            Toast.makeText(context, res.exceptionOrNull()?.localizedMessage ?: "উইকেট রেকর্ড করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
                                        }
                                        showWicketDialog = false
                                    } catch (e: Exception) {
                                        android.util.Log.e("LiveScoring", "Error recording wicket", e)
                                        Toast.makeText(context, "ত্রুটি: ${e.localizedMessage ?: "অপ্রত্যাশিত সমস্যা"}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = isValidationMet,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WicketRed,
                                disabledContainerColor = Color.Gray.copy(alpha = 0.35f),
                                contentColor = Color.White,
                                disabledContentColor = Color.LightGray.copy(alpha = 0.7f)
                            )
                        ) {
                            Text(
                                text = when {
                                    isRetHurtSelected -> "নিশ্চিত করুন ও নতুন ব্যাটার নামান 🏥"
                                    isAllOutDelivery -> "আউট ও ইনিংস সমাপ্ত করুন 🛑"
                                    else -> "Confirm Out & Continue"
                                },
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        TextButton(
                            onClick = { showWicketDialog = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text(
                                text = "Cancel (বাতিল)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    val targetBallToEdit = selectedBallToEdit
    if (showEditBallDialog && targetBallToEdit != null) {
        val ball = targetBallToEdit
        var editRuns by remember { mutableIntStateOf(ball.runsOffBat) }
        var editExtraType by remember { mutableStateOf(ball.extraType) }
        var editExtraRuns by remember { mutableIntStateOf(ball.extraRuns) }
        var editIsWicket by remember { mutableStateOf(ball.isWicket) }
        var reason by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showEditBallDialog = false },
            title = {
                Text("Edit Delivery #${ball.ballSeq} (Audit Logged)", color = GoldYellow, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Ball: Over ${ball.overNumber}.${ball.ballInOver}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    Text("Runs: $editRuns", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0, 1, 2, 3, 4, 6).forEach { r ->
                            Button(
                                onClick = { editRuns = r },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (editRuns == r) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Text("$r", fontSize = 12.sp, color = if (editRuns == r) Color.White else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Extra Type: ${editExtraType.name}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(ExtraType.NONE, ExtraType.WIDE, ExtraType.NO_BALL, ExtraType.BYE).forEach { et ->
                            Button(
                                onClick = {
                                    editExtraType = et
                                    if (et != ExtraType.NONE && editExtraRuns == 0) editExtraRuns = 1
                                    if (et == ExtraType.NONE) editExtraRuns = 0
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (editExtraType == et) GoldYellow else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Text(et.shortName.ifEmpty { "0" }, fontSize = 10.sp, color = if (editExtraType == et) Color.White else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Correction Reason (Required)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reason.isBlank()) {
                            Toast.makeText(context, "Please enter a reason for correction", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch {
                            try {
                                container.scoringRepository.editBallEvent(
                                    match = match,
                                    ballId = ball.id,
                                    newRunsOffBat = editRuns,
                                    newExtraType = editExtraType,
                                    newExtraRuns = editExtraRuns,
                                    newIsWicket = editIsWicket,
                                    newDismissalType = if (editIsWicket) DismissalType.BOWLED else null,
                                    reason = reason,
                                    userId = currentUserId,
                                    userName = "Scorer ($currentUserId)"
                                )
                                showEditBallDialog = false
                                Toast.makeText(context, "Ball corrected and scorecard recalculated!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "ত্রুটি: ${e.localizedMessage ?: "সংশোধন করা যায়নি"}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("Recalculate", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditBallDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // SUB SCORERS MANAGEMENT DIALOG (Head Scorer)
    if (showSubScorersDialog) {
        com.example.ui.components.SubScorerManagementDialog(
            match = match,
            container = container,
            onDismiss = { showSubScorersDialog = false }
        )
    }

    // CHANGE BOWLER DIALOG (Enforces consecutive over rule)
    if (showBowlerDialog) {
        val lastLegalBall = ballEvents.lastOrNull {
            it.isLegalDelivery && it.dismissalType != DismissalType.RETIRED_HURT && it.dismissalType != DismissalType.RETIRED_OUT
        }
        val lastOverBowlerId = if (activeInnings != null && activeInnings.legalBalls > 0 && activeInnings.legalBalls % 6 == 0) {
            lastLegalBall?.bowlerId
        } else null

        AlertDialog(
            onDismissRequest = {
                // Scorer must select bowler to continue
                showBowlerDialog = false
            },
            title = {
                Column {
                    Text(
                        text = if (lastOverBowlerId != null) "ওভার সমাপ্ত! নতুন বোলার নির্বাচন করুন" else "বোলার নির্বাচন করুন (Select Bowler)",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Bowling: ${bowlingTeam.name} (${bowlingSquad.size} জন বোলার) • নিয়ম: পর পর দুই ওভার একই বোলার বল করতে পারবে না",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    items(bowlingSquad, key = { it.id }) { p ->
                        val isPreviousBowler = p.id == lastOverBowlerId
                        val isCurrentBowler = p.id == match.currentBowlerId
                        val isCapt = isPlayerCaptain(p.id)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isCurrentBowler) EmeraldGreen.copy(alpha = 0.15f)
                                    else if (isPreviousBowler) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    else Color.Transparent
                                )
                                .clickable(enabled = !isPreviousBowler) {
                                    scope.launch {
                                        try {
                                            container.scoringRepository.selectBowler(
                                                matchId = match.id,
                                                bowlerId = p.id,
                                                bowlerName = p.name
                                            )
                                            showBowlerDialog = false
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "বোলার নির্বাচন করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            CricUpAvatar(
                                model = p.photoUrl,
                                size = 28.dp,
                                fallbackText = p.name.take(2),
                                fallbackIcon = CricUpIcons.SportsCricket,
                                borderColor = if (isCurrentBowler) EmeraldGreen else Color.Transparent
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${PlayerRoles.getRoleIcon(p.role)} ${p.name}",
                                        color = if (isPreviousBowler) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        else if (isCurrentBowler) EmeraldGreen
                                        else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isCurrentBowler) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isCapt) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(color = GoldYellow, shape = RoundedCornerShape(4.dp)) {
                                            Text(" (C) ", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                                if (isPreviousBowler) {
                                    Text(
                                        text = "পূর্ববর্তী ওভারে বল করেছেন (পর পর দুই ওভার নিষিদ্ধ)",
                                        color = WicketRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            if (isCurrentBowler) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBowlerDialog = false }) {
                    Text("বন্ধ করুন (Close)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // NO BALL CUSTOMIZATION DIALOG (NB only or NB + custom runs or NB + Wicket per MCC Law 21.18)
    if (showNoBallDialog) {
        var customRunsText by remember { mutableStateOf("") }
        var isOffBat by remember { mutableStateOf(true) }
        var isNbWicketMode by remember { mutableStateOf(false) }
        var selectedNbDismissal by remember { mutableStateOf(DismissalType.RUN_OUT) }
        var nbDismissedBatterId by remember { mutableStateOf(match.currentStrikerId ?: "") }
        var nbDismissedBatterName by remember { mutableStateOf(match.currentStrikerName ?: "") }
        var nbIncomingBatterId by remember { mutableStateOf("") }
        var nbIncomingBatterName by remember { mutableStateOf("") }
        var nbFielderName by remember { mutableStateOf("") }

        val maxWkts = match.getMaxWicketsForInnings(match.currentInningsNumber)
        val currWkts = activeInnings?.wickets ?: 0
        val isAllOutDelivery = (currWkts + 1) >= maxWkts

        val eligibleNewBatters = battingSquad.filter { p ->
            p.id != match.currentStrikerId &&
                    p.id != match.currentNonStrikerId &&
                    !match.dismissedPlayerIds.contains(p.id)
        }

        AlertDialog(
            onDismissRequest = { showNoBallDialog = false },
            title = {
                Text(
                    text = "No Ball Options (নো বল অপশন)",
                    color = GoldYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    if (!isNbWicketMode) {
                        item {
                            Text(
                                text = "প্রতিটি নো বলে ১ অতিরিক্ত রান দলীয় ও বোলার স্কোরে যোগ হবে। ব্যাটার যদি রান নেয়, তবে নিচের অপশন থেকে নির্বাচন করুন:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "দ্রুত নির্বাচন (Quick Options):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        val nbQuickOptions = listOf(
                            Triple("শুধুমাত্র নো বল / NB only (1 Run)", 0, 1),
                            Triple("নো বল + ১ রান / NB + 1 Run (Total 2)", 1, 1),
                            Triple("নো বল + ২ রান / NB + 2 Runs (Total 3)", 2, 1),
                            Triple("নো বল + ৩ রান / NB + 3 Runs (Total 4)", 3, 1),
                            Triple("নো বল + ৪ রান / NB + 4 (Four - Total 5)", 4, 1),
                            Triple("নো বল + ৬ রান / NB + 6 (Six - Total 7)", 6, 1)
                        )

                        items(nbQuickOptions) { (label, runsBat, extraPenalty) ->
                            Button(
                                onClick = {
                                    scope.launch {
                                        container.scoringRepository.recordBallEvent(
                                            match = match,
                                            runsOffBat = runsBat,
                                            extraType = ExtraType.NO_BALL,
                                            extraRuns = extraPenalty,
                                            isWicket = false,
                                            dismissalType = null,
                                            dismissedPlayerId = null,
                                            dismissedPlayerName = null,
                                            incomingBatterId = null,
                                            incomingBatterName = null,
                                            assistingPlayerName = null,
                                            scorerId = currentUserId
                                        )
                                        showNoBallDialog = false
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (runsBat == 0) MaterialTheme.colorScheme.surfaceVariant 
                                    else if (runsBat >= 4) BoundaryBlue 
                                    else MaterialTheme.colorScheme.surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldYellow.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (runsBat >= 4) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { isNbWicketMode = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = WicketRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("নো বল + আউট (No Ball + Wicket)", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "কাস্টম রান যোগ করুন (Custom Runs):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldYellow
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = customRunsText,
                                onValueChange = { customRunsText = it },
                                label = { Text("Runs (রানের সংখ্যা, যেমন: 5, 7)") },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldGreen),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { isOffBat = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isOffBat) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isOffBat) Color.White else MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("ব্যাট থেকে (Off Bat)", fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { isOffBat = false },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!isOffBat) GoldYellow else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (!isOffBat) Color.Black else MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("বাই রান (Byes)", fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        // MCC Law 21.18 dismissals
                        item {
                            Text(
                                text = "⚖️ MCC Law 21.18: নো বলে Bowled, Caught, LBW, Stumped, Hit Wicket আউট হওয়া অসম্ভব। কেবলমাত্র রান আউট, অবস্ট্রাক্টিং ফিল্ড এবং হিট দ্য বল টুয়াইস আউট হতে পারে।",
                                fontSize = 12.sp,
                                color = GoldYellow,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("আউটের ধরন নির্বাচন করুন:", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        val legalNbDismissals = listOf(DismissalType.RUN_OUT, DismissalType.OBSTRUCTING_FIELD, DismissalType.HIT_BALL_TWICE)
                        items(legalNbDismissals) { type ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedNbDismissal = type }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, if (selectedNbDismissal == type) WicketRed else MaterialTheme.colorScheme.outline, CircleShape)
                                        .background(if (selectedNbDismissal == type) WicketRed else Color.Transparent)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${type.icon} ${type.displayName}", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("আউট ব্যাটার:", color = GoldYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = {
                                        nbDismissedBatterId = match.currentStrikerId ?: ""
                                        nbDismissedBatterName = match.currentStrikerName ?: ""
                                    }
                                ) {
                                    Text(
                                        "Striker (${match.currentStrikerName})",
                                        color = if (nbDismissedBatterId == match.currentStrikerId) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        nbDismissedBatterId = match.currentNonStrikerId ?: ""
                                        nbDismissedBatterName = match.currentNonStrikerName ?: ""
                                    }
                                ) {
                                    Text(
                                        "Non-Striker (${match.currentNonStrikerName})",
                                        color = if (nbDismissedBatterId == match.currentNonStrikerId) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (selectedNbDismissal == DismissalType.RUN_OUT) {
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = nbFielderName,
                                    onValueChange = { nbFielderName = it },
                                    label = { Text("Fielder Name") },
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldGreen),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (isAllOutDelivery) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    colors = CardDefaults.cardColors(containerColor = WicketRed.copy(alpha = 0.15f))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("🚨 শেষ উইকেট! ইনিংস সমাপ্ত হবে।", color = WicketRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("পরবর্তী ব্যাটার (${eligibleNewBatters.size} জন বাকি):", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!isAllOutDelivery) {
                            items(eligibleNewBatters) { p ->
                                val isSelected = nbIncomingBatterId == p.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            nbIncomingBatterId = p.id
                                            nbIncomingBatterName = p.name
                                        }
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CricUpAvatar(
                                        model = p.photoUrl,
                                        size = 22.dp,
                                        fallbackText = p.name.take(2),
                                        fallbackIcon = Icons.Default.Person
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${PlayerRoles.getRoleIcon(p.role)} ${p.name}",
                                        color = if (isSelected) EmeraldGreen else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (isNbWicketMode) {
                    Button(
                        onClick = {
                            if (!isAllOutDelivery && nbIncomingBatterId.isBlank() && eligibleNewBatters.isNotEmpty()) {
                                Toast.makeText(context, "পরবর্তী ব্যাটার নির্বাচন করুন", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val incomingId = if (!isAllOutDelivery && nbIncomingBatterId.isNotBlank()) nbIncomingBatterId else null
                            val incomingName = if (!isAllOutDelivery && nbIncomingBatterName.isNotBlank()) nbIncomingBatterName else null

                            scope.launch {
                                try {
                                    container.scoringRepository.recordBallEvent(
                                        match = match,
                                        runsOffBat = 0,
                                        extraType = ExtraType.NO_BALL,
                                        extraRuns = 1,
                                        isWicket = true,
                                        dismissalType = selectedNbDismissal,
                                        dismissedPlayerId = nbDismissedBatterId,
                                        dismissedPlayerName = nbDismissedBatterName,
                                        incomingBatterId = incomingId,
                                        incomingBatterName = incomingName,
                                        assistingPlayerName = nbFielderName.ifBlank { null },
                                        scorerId = currentUserId
                                    )
                                    showNoBallDialog = false
                                } catch (e: Exception) {
                                    Toast.makeText(context, "ত্রুটি: ${e.localizedMessage ?: "সমস্যা হয়েছে"}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WicketRed)
                    ) {
                        Text(if (isAllOutDelivery) "আউট ও ইনিংস সমাপ্ত 🛑" else "Confirm Wicket", color = Color.White)
                    }
                } else {
                    Button(
                        onClick = {
                            val customRuns = customRunsText.toIntOrNull() ?: 0
                            scope.launch {
                                try {
                                    val batRuns = if (isOffBat) customRuns else 0
                                    val penaltyAndExtra = 1 + (if (!isOffBat) customRuns else 0)
                                    container.scoringRepository.recordBallEvent(
                                        match = match,
                                        runsOffBat = batRuns,
                                        extraType = ExtraType.NO_BALL,
                                        extraRuns = penaltyAndExtra,
                                        isWicket = false,
                                        dismissalType = null,
                                        dismissedPlayerId = null,
                                        dismissedPlayerName = null,
                                        incomingBatterId = null,
                                        incomingBatterName = null,
                                        assistingPlayerName = null,
                                        scorerId = currentUserId
                                    )
                                    showNoBallDialog = false
                                } catch (e: Exception) {
                                    Toast.makeText(context, "ত্রুটি: ${e.localizedMessage ?: "সমস্যা হয়েছে"}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) {
                        Text("কাস্টম নো বল যোগ করুন", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (isNbWicketMode) isNbWicketMode = false else showNoBallDialog = false
                    }
                ) {
                    Text(if (isNbWicketMode) "⬅️ Back" else "বাতিল (Cancel)")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // MCC WIDE CUSTOMIZATION DIALOG (Wide only, Wide + runs, or Wide + Wicket per MCC Law 22.17)
    if (showWideDialog) {
        var isWideWicketMode by remember { mutableStateOf(false) }
        var selectedWideDismissal by remember { mutableStateOf(DismissalType.STUMPED) }
        var wideDismissedBatterId by remember { mutableStateOf(match.currentStrikerId ?: "") }
        var wideDismissedBatterName by remember { mutableStateOf(match.currentStrikerName ?: "") }
        var wideIncomingBatterId by remember { mutableStateOf("") }
        var wideIncomingBatterName by remember { mutableStateOf("") }
        var wideFielderName by remember { mutableStateOf("") }
        var wideCompletedByeRuns by remember { mutableIntStateOf(0) }

        val maxWkts = match.getMaxWicketsForInnings(match.currentInningsNumber)
        val currWkts = activeInnings?.wickets ?: 0
        val isAllOutDelivery = (currWkts + 1) >= maxWkts

        val eligibleNewBatters = battingSquad.filter { p ->
            p.id != match.currentStrikerId &&
                    p.id != match.currentNonStrikerId &&
                    !match.dismissedPlayerIds.contains(p.id)
        }

        AlertDialog(
            onDismissRequest = { showWideDialog = false },
            title = {
                Text(
                    text = "Wide Ball Options (ওয়াইড বল অপশন)",
                    color = GoldYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    if (!isWideWicketMode) {
                        item {
                            Text(
                                text = "MCC Law 22: ওয়াইডে স্বয়ংক্রিয়ভাবে ১ রান অতিরিক্ত ও বোলারের বিপক্ষে যোগ হবে। ওয়াইড বলে অতিরিক্ত দৌড় বা বাউন্ডারি হলে নির্বাচন করুন:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        val wideQuickOptions = listOf(
                            Pair("শুধুমাত্র ওয়াইড / Wide only (1 Extra)", 1),
                            Pair("ওয়াইড + ১ বাই রান দৌড় / Wide + 1 Bye (Total 2)", 2),
                            Pair("ওয়াইড + ২ বাই রান / Wide + 2 Byes (Total 3)", 3),
                            Pair("ওয়াইড + ৩ বাই রান / Wide + 3 Byes (Total 4)", 4),
                            Pair("ওয়াইড + ৪ বাই বাউন্ডারি / Wide + 4 Byes Boundary (Total 5)", 5)
                        )

                        items(wideQuickOptions) { (label, extraRunsTotal) ->
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            container.scoringRepository.recordBallEvent(
                                                match = match,
                                                runsOffBat = 0,
                                                extraType = ExtraType.WIDE,
                                                extraRuns = extraRunsTotal,
                                                isWicket = false,
                                                dismissalType = null,
                                                dismissedPlayerId = null,
                                                dismissedPlayerName = null,
                                                incomingBatterId = null,
                                                incomingBatterName = null,
                                                assistingPlayerName = null,
                                                scorerId = currentUserId
                                            )
                                            showWideDialog = false
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "ত্রুটি: ${e.localizedMessage ?: "সমস্যা হয়েছে"}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (extraRunsTotal == 1) MaterialTheme.colorScheme.surfaceVariant
                                    else if (extraRunsTotal >= 5) BoundaryBlue
                                    else MaterialTheme.colorScheme.surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldYellow.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (extraRunsTotal >= 5) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { isWideWicketMode = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = WicketRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("ওয়াইড + রান আউট / স্টাম্পড (Wide + Run Out)", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Wicket mode: MCC Law 22.17 restricts dismissals on a Wide to Stumped, Run Out, Hit Wicket
                        item {
                            Text(
                                text = "⚖️ MCC Law 22.17: ওয়াইড বলে বোল্ড বা ক্যাচ আউট হয় না। শুধুমাত্র রান আউট বা স্টাম্পড বৈধ।",
                                fontSize = 12.sp,
                                color = GoldYellow,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("আউটের ধরন নির্বাচন করুন:", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        val legalWideDismissals = listOf(DismissalType.RUN_OUT, DismissalType.STUMPED, DismissalType.HIT_WICKET)
                        items(legalWideDismissals) { type ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedWideDismissal = type }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, if (selectedWideDismissal == type) WicketRed else MaterialTheme.colorScheme.outline, CircleShape)
                                        .background(if (selectedWideDismissal == type) WicketRed else Color.Transparent)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${type.icon} ${type.displayName}", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("আউট ব্যাটার:", color = GoldYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = {
                                        wideDismissedBatterId = match.currentStrikerId ?: ""
                                        wideDismissedBatterName = match.currentStrikerName ?: ""
                                    }
                                ) {
                                    Text(
                                        "Striker (${match.currentStrikerName})",
                                        color = if (wideDismissedBatterId == match.currentStrikerId) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        wideDismissedBatterId = match.currentNonStrikerId ?: ""
                                        wideDismissedBatterName = match.currentNonStrikerName ?: ""
                                    }
                                ) {
                                    Text(
                                        "Non-Striker (${match.currentNonStrikerName})",
                                        color = if (wideDismissedBatterId == match.currentNonStrikerId) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (selectedWideDismissal == DismissalType.STUMPED || selectedWideDismissal == DismissalType.RUN_OUT) {
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = wideFielderName,
                                    onValueChange = { wideFielderName = it },
                                    label = { Text("Keeper / Fielder Name") },
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldGreen),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (selectedWideDismissal == DismissalType.RUN_OUT) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "রান আউটের আগে সফলভাবে সম্পন্ন হওয়া বাই রান (Completed Bye runs):",
                                    color = EmeraldGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "১ ওয়াইড অতিরিক্ত রান + সফল বাই রান মিলে মোট রান স্কোরে যোগ হবে",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(listOf(0, 1), listOf(2, 3)).forEach { pair ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            pair.forEach { r ->
                                                val isChosen = wideCompletedByeRuns == r
                                                Surface(
                                                    color = if (isChosen) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isChosen) EmeraldGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { wideCompletedByeRuns = r }
                                                ) {
                                                    Text(
                                                        text = if (r == 0) "০ রান (১ ওয়াইড)" else "$r বাই (মোট ${1 + r})",
                                                        fontSize = 11.5.sp,
                                                        color = if (isChosen) Color.White else MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = if (isChosen) FontWeight.Bold else FontWeight.Normal,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 8.dp, vertical = 10.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (isAllOutDelivery) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    colors = CardDefaults.cardColors(containerColor = WicketRed.copy(alpha = 0.15f))
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("🚨 শেষ উইকেট! ইনিংস সমাপ্ত হবে।", color = WicketRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("পরবর্তী ব্যাটার (${eligibleNewBatters.size} জন বাকি):", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!isAllOutDelivery) {
                            items(eligibleNewBatters) { p ->
                                val isSelected = wideIncomingBatterId == p.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            wideIncomingBatterId = p.id
                                            wideIncomingBatterName = p.name
                                        }
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CricUpAvatar(
                                        model = p.photoUrl,
                                        size = 22.dp,
                                        fallbackText = p.name.take(2),
                                        fallbackIcon = Icons.Default.Person
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${PlayerRoles.getRoleIcon(p.role)} ${p.name}",
                                        color = if (isSelected) EmeraldGreen else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (isWideWicketMode) {
                    Button(
                        onClick = {
                            if (!isAllOutDelivery && wideIncomingBatterId.isBlank() && eligibleNewBatters.isNotEmpty()) {
                                Toast.makeText(context, "পরবর্তী ব্যাটার নির্বাচন করুন", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val incomingId = if (!isAllOutDelivery && wideIncomingBatterId.isNotBlank()) wideIncomingBatterId else null
                            val incomingName = if (!isAllOutDelivery && wideIncomingBatterName.isNotBlank()) wideIncomingBatterName else null

                            scope.launch {
                                try {
                                    val totalWideRuns = 1 + (if (selectedWideDismissal == DismissalType.RUN_OUT) wideCompletedByeRuns else 0)
                                    container.scoringRepository.recordBallEvent(
                                        match = match,
                                        runsOffBat = 0,
                                        extraType = ExtraType.WIDE,
                                        extraRuns = totalWideRuns,
                                        isWicket = true,
                                        dismissalType = selectedWideDismissal,
                                        dismissedPlayerId = wideDismissedBatterId,
                                        dismissedPlayerName = wideDismissedBatterName,
                                        incomingBatterId = incomingId,
                                        incomingBatterName = incomingName,
                                        assistingPlayerName = wideFielderName.ifBlank { null },
                                        scorerId = currentUserId
                                    )
                                    showWideDialog = false
                                } catch (e: Exception) {
                                    Toast.makeText(context, "ত্রুটি: ${e.localizedMessage ?: "সমস্যা হয়েছে"}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WicketRed)
                    ) {
                        Text(if (isAllOutDelivery) "আউট ও ইনিংস সমাপ্ত 🛑" else "Confirm Wicket", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (isWideWicketMode) isWideWicketMode = false else showWideDialog = false
                    }
                ) {
                    Text(if (isWideWicketMode) "⬅️ Back" else "Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // BYES & LEG BYES DIALOG (Separate accounting, not charged to bowler)
    if (showByesLegByesDialog) {
        var customByeRunsInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showByesLegByesDialog = false },
            title = {
                Text(
                    text = "${if (selectedByeType == ExtraType.BYE) "Byes (বাই রান)" else "Leg Byes (লেগ বাই রান)"}",
                    color = GoldYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { selectedByeType = ExtraType.BYE },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedByeType == ExtraType.BYE) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Byes (বাই)", color = if (selectedByeType == ExtraType.BYE) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                        Button(
                            onClick = { selectedByeType = ExtraType.LEG_BYE },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedByeType == ExtraType.LEG_BYE) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Leg Byes (লেগ বাই)", color = if (selectedByeType == ExtraType.LEG_BYE) Color.White else MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "⚖️ MCC Law: বাই ও লেগ বাই রান বোলারের বিপক্ষে চার্জ করা হয় না (Bowler runs conceded unaffected)। বিজোড় রানে (১, ৩) স্ট্রাইক পরিবর্তিত হবে।",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val byeOptions = listOf(
                        Pair("১ রান / 1 Run (Strike rotates)", 1),
                        Pair("২ রান / 2 Runs", 2),
                        Pair("৩ রান / 3 Runs (Strike rotates)", 3),
                        Pair("৪ বাউন্ডারি / 4 Runs (Boundary Byes)", 4),
                        Pair("৫ ওভারথ্রো / 5 Runs (Overthrow Byes)", 5)
                    )

                    byeOptions.forEach { (label, r) ->
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        container.scoringRepository.recordBallEvent(
                                            match = match,
                                            runsOffBat = 0,
                                            extraType = selectedByeType,
                                            extraRuns = r,
                                            isWicket = false,
                                            dismissalType = null,
                                            dismissedPlayerId = null,
                                            dismissedPlayerName = null,
                                            incomingBatterId = null,
                                            incomingBatterName = null,
                                            assistingPlayerName = null,
                                            scorerId = currentUserId
                                        )
                                        showByesLegByesDialog = false
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "ত্রুটি: ${e.localizedMessage ?: "সমস্যা হয়েছে"}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (r >= 4) BoundaryBlue else MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldYellow.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (r >= 4) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "অথবা যেকোন সংখ্যা লিখে যোগ করুন (Custom Runs):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldYellow
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customByeRunsInput,
                            onValueChange = { if (it.all { ch -> ch.isDigit() }) customByeRunsInput = it },
                            placeholder = { Text("যেমন: 1, 2, 3, 4, 6, 7 ইত্যাদি", fontSize = 12.sp) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldGreen),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                val parsed = customByeRunsInput.toIntOrNull()
                                if (parsed != null && parsed > 0) {
                                    scope.launch {
                                        try {
                                            container.scoringRepository.recordBallEvent(
                                                match = match,
                                                runsOffBat = 0,
                                                extraType = selectedByeType,
                                                extraRuns = parsed,
                                                isWicket = false,
                                                dismissalType = null,
                                                dismissedPlayerId = null,
                                                dismissedPlayerName = null,
                                                incomingBatterId = null,
                                                incomingBatterName = null,
                                                assistingPlayerName = null,
                                                scorerId = currentUserId
                                            )
                                            showByesLegByesDialog = false
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "ত্রুটি: ${e.localizedMessage ?: "সমস্যা হয়েছে"}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "সঠিক রান সংখ্যা লিখুন", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("যোগ করুন", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showByesLegByesDialog = false }) {
                    Text("বাতিল (Cancel)")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // PENALTY RUNS DIALOG (Req 18: MCC Law 41/42 - 5 Penalty Runs awarded by Umpire)
    if (showPenaltyDialog) {
        var customPenaltyInput by remember { mutableStateOf("5") }

        AlertDialog(
            onDismissRequest = { showPenaltyDialog = false },
            title = {
                Text(
                    text = "⚖️ MCC Penalty Runs (পেনাল্টি রান)",
                    color = GoldYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "MCC Law 41/42 অনুযায়ী আম্পায়ার অপরাধের জন্য ব্যাটিং দলকে ৫ পেনাল্টি রান দিতে পারেন। এই রান বোলারের বিপক্ষে চার্জ হয় না এবং কোনো বল গণনা হয় না।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    container.scoringRepository.recordBallEvent(
                                        match = match,
                                        runsOffBat = 0,
                                        extraType = ExtraType.PENALTY,
                                        extraRuns = 5,
                                        isWicket = false,
                                        dismissalType = null,
                                        dismissedPlayerId = null,
                                        dismissedPlayerName = null,
                                        incomingBatterId = null,
                                        incomingBatterName = null,
                                        assistingPlayerName = null,
                                        scorerId = currentUserId
                                    )
                                    showPenaltyDialog = false
                                    Toast.makeText(context, "৫ পেনাল্টি রান যোগ করা হয়েছে", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "ত্রুটি: ${e.localizedMessage ?: "সমস্যা হয়েছে"}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+৫ পেনাল্টি রান যোগ করুন (Standard 5 Runs)", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("অথবা কাস্টম পেনাল্টি রান লিখুন:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = customPenaltyInput,
                        onValueChange = { customPenaltyInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("পেনাল্টি রান সংখ্যা") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pRuns = customPenaltyInput.toIntOrNull() ?: 5
                        if (pRuns <= 0) return@Button
                        scope.launch {
                            try {
                                container.scoringRepository.recordBallEvent(
                                    match = match,
                                    runsOffBat = 0,
                                    extraType = ExtraType.PENALTY,
                                    extraRuns = pRuns,
                                    isWicket = false,
                                    dismissalType = null,
                                    dismissedPlayerId = null,
                                    dismissedPlayerName = null,
                                    incomingBatterId = null,
                                    incomingBatterName = null,
                                    assistingPlayerName = null,
                                    scorerId = currentUserId
                                )
                                showPenaltyDialog = false
                                Toast.makeText(context, "$pRuns পেনাল্টি রান যোগ করা হয়েছে", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "ত্রুটি: ${e.localizedMessage ?: "সমস্যা হয়েছে"}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldYellow),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("কাস্টম পেনাল্টি কনফার্ম", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPenaltyDialog = false }) {
                    Text("বাতিল")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // MANUAL OVER COMPLETION DIALOG (MCC Law Umpire Regulation)
    if (showManualOverDialog) {
        var manualReason by remember { mutableStateOf("") }
        val currBalls = activeInnings?.legalBalls ?: 0
        val ballsInOver = currBalls % 6

        AlertDialog(
            onDismissRequest = { showManualOverDialog = false },
            title = {
                Text(
                    text = "অ্যাম্পায়ার ওভার সমাপ্তি (Manual Over End)",
                    color = GoldYellow,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "বর্তমান ওভারের বল সংখ্যা: $ballsInOver/6 বল। MCC ক্রিকেট আইন মোতাবেক অ্যাম্পায়ার কর্তৃক যেকোনো বৈধ কারণে (যেমন বল গণনা ভুল বা জরুরি পরিস্থিতি) ওভার সমাপ্ত ঘোষণা করতে পারেন। এটি অডিট লগে সংরক্ষিত থাকবে।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = manualReason,
                        onValueChange = { manualReason = it },
                        label = { Text("সমাপ্তির কারণ (Audit Reason)") },
                        placeholder = { Text("যেমন: অ্যাম্পায়ার ৫ বলে ওভার ডেকেছেন / বোলার ইনজুরি") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldGreen),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val reasonText = manualReason.ifBlank { "অ্যাম্পায়ার কর্তৃক ম্যানুয়াল ওভার সমাপ্তি" }
                        scope.launch {
                            val res = container.scoringRepository.manualCompleteOver(match, reasonText, currentUserId)
                            if (res.isSuccess) {
                                Toast.makeText(context, "ওভার সমাপ্ত ও নতুন বোলার সিলেকশন প্রস্তুত", Toast.LENGTH_SHORT).show()
                                showManualOverDialog = false
                                showBowlerDialog = true
                            } else {
                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("ওভার সমাপ্ত করুন", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualOverDialog = false }) {
                    Text("বাতিল")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // MCC SCOREBOOK RECONCILIATION DIALOG
    if (showReconciliationDialog) {
        val inn = activeInnings
        val batRuns = inn?.totalBatsmanRuns ?: 0
        val extras = inn?.totalExtras ?: 0
        val calcTeamRuns = batRuns + extras
        val actualTeamRuns = inn?.totalRuns ?: 0
        val bowlerRuns = inn?.totalBowlerRunsConceded ?: 0
        val byes = inn?.byes ?: 0
        val legByes = inn?.legByes ?: 0
        val penalties = inn?.penalties ?: 0
        val calcBowlerRuns = actualTeamRuns - byes - legByes - penalties
        val isReconciled = inn?.isScoreReconciled == true

        AlertDialog(
            onDismissRequest = { showReconciliationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MCC স্কোরবুক রিকনসিলিয়েশন",
                        color = GoldYellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                Column {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isReconciled) EmeraldGreen.copy(alpha = 0.12f) else GoldYellow.copy(alpha = 0.15f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isReconciled) EmeraldGreen else GoldYellow)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (isReconciled) "✅ স্কোরবুক সম্পূর্ণ ব্যালেন্সড ও পরীক্ষিত" else "⚠️ রিকনসিলিয়েশন অডিট পর্যালোচনা",
                                color = if (isReconciled) EmeraldGreen else GoldYellow,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "MCC Laws 16 & 21 অনুযায়ী অফিসিয়াল স্কোরবুকের ৩টি অংশ (ব্যাটিং বিশ্লেষণ, বোলিং বিশ্লেষণ এবং অতিরিক্ত রান) প্রতি বল শেষে স্বয়ংক্রিয় ক্রস-ভেরিফাই হয়।",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("১. টিম স্কোর সমীকরণ (MCC Law 16):", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("মোট রান = ব্যাটার রান ($batRuns) + অতিরিক্ত ($extras) = $calcTeamRuns", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("রেকর্ডকৃত টিম রান = $actualTeamRuns ${if (calcTeamRuns == actualTeamRuns) "✓ মিল রয়েছে" else "✗ গরমিল"}", fontSize = 11.sp, color = if (calcTeamRuns == actualTeamRuns) EmeraldGreen else WicketRed)

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("২. অতিরিক্ত রানের বিভাজন:", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("ওয়াইড: ${inn?.wides ?: 0} | নো বল: ${inn?.noBalls ?: 0} | বাই: $byes | লেগ বাই: $legByes | পেনাল্টি: $penalties", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("৩. বোলিং ক্রস-চেক সমীকরণ:", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("বোলার রান = টিম রান ($actualTeamRuns) - বাই ($byes) - লেগ বাই ($legByes) = $calcBowlerRuns", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("রেকর্ডকৃত বোলার মোট রান = $bowlerRuns ${if (calcBowlerRuns == bowlerRuns) "✓ মিল রয়েছে" else "✗ গরমিল"}", fontSize = 11.sp, color = if (calcBowlerRuns == bowlerRuns) EmeraldGreen else WicketRed)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showReconciliationDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("ঠিক আছে (Close)", color = Color.White)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // END INNINGS DIALOG (Decides winner with maximum runs)
    if (showEndInningsDialog) {
        AlertDialog(
            onDismissRequest = { showEndInningsDialog = false },
            title = {
                Text(
                    text = if (match.currentInningsNumber == 1) "End 1st Innings?" else "Complete Match? (ম্যাচ সমাপ্ত করুন)",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (match.currentInningsNumber == 1)
                        "First innings total is ${activeInnings?.totalRuns} runs. Target for 2nd innings will be set to ${(activeInnings?.totalRuns ?: 0) + 1} runs."
                    else
                        "Complete and finalize the match. The team with maximum runs will be declared the winner.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            container.scoringRepository.endInningsOrMatch(match.id)
                            showEndInningsDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("Confirm", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndInningsDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // AUTOMATIC INNINGS BREAK MODAL (When 1st Innings Ends via All-Out or Max Overs)
    if (match.status == MatchStatus.INNINGS_BREAK) {
        val inn1 = match.firstInnings
        val target = match.targetRuns ?: ((inn1?.totalRuns ?: 0) + 1)
        val maxW1 = match.getMaxWicketsForInnings(1)
        val isAllOut = (inn1?.wickets ?: 0) >= maxW1
        val isOversDone = (inn1?.legalBalls ?: 0) / 6 >= match.totalOvers

        AlertDialog(
            onDismissRequest = { /* Must proceed to 2nd innings or view scorecard */ },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏏 ১ম ইনিংস সমাপ্ত! (Innings Break)", color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            },
            text = {
                Column {
                    Text(
                        text = if (isAllOut) "দলের নির্ধারিত সর্বোচ্চ ${maxW1} উইকেটের সবকটি পতন হওয়ায় দল অল-আউট হয়েছে এবং ১ম ইনিংস সমাপ্ত হয়েছে।"
                        else if (isOversDone) "নির্ধারিত ${match.totalOvers} ওভার সমাপ্ত হওয়ায় ১ম ইনিংস শেষ হয়েছে।"
                        else "১ম ইনিংস সমাপ্ত হয়েছে।",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "১ম ইনিংস স্কোরকার্ড:",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${inn1?.battingTeamName}: ${inn1?.totalRuns ?: 0}/${inn1?.wickets ?: 0} (${inn1?.oversFormatted ?: "0.0"} Ov)",
                                color = EmeraldGreen,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "২য় ইনিংসে জয়ের টার্গেট: $target রান",
                                color = GoldYellow,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            container.scoringRepository.startSecondInnings(match.id)
                            showOpeningSelectionDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("২য় ইনিংস শুরু করুন ➔", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onBack) {
                    Text("স্কোরকার্ড দেখুন (View Scorecard)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // AUTOMATIC MATCH COMPLETED MODAL (When 2nd Innings Ends via Target, All-Out, or Max Overs)
    if (match.status == MatchStatus.COMPLETED) {
        val inn1 = match.firstInnings
        val inn2 = match.secondInnings

        AlertDialog(
            onDismissRequest = { /* View match results */ },
            title = {
                Text("🏆 ম্যাচ সমাপ্ত! (MATCH COMPLETED)", color = GoldYellow, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Column {
                    Text(
                        text = match.resultDescription.ifBlank { "ম্যাচ সফলভাবে সম্পন্ন হয়েছে।" },
                        color = EmeraldGreen,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "১ম ইনিংস: ${inn1?.battingTeamName ?: "Team 1"}: ${inn1?.totalRuns ?: 0}/${inn1?.wickets ?: 0} (${inn1?.oversFormatted ?: "0.0"} Ov)",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "২য় ইনিংস: ${inn2?.battingTeamName ?: "Team 2"}: ${inn2?.totalRuns ?: 0}/${inn2?.wickets ?: 0} (${inn2?.oversFormatted ?: "0.0"} Ov)",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("সম্পূর্ণ বিবরণ ও স্কোরকার্ড 📊", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
    }
}
