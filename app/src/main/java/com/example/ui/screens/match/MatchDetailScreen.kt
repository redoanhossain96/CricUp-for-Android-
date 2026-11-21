package com.example.ui.screens.match

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import com.example.ui.theme.CricUpIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.BallEvent
import com.example.data.model.InningsScorecard
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.data.model.Umpire
import com.example.di.AppContainer
import com.example.ui.components.BallBadge
import com.example.ui.components.CricUpAvatar
import com.example.ui.components.LivePill
import com.example.ui.components.OfflinePill
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.WicketRed
import com.example.ui.theme.adaptiveEmeraldColor
import com.example.ui.theme.adaptiveGoldColor
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDetailScreen(
    matchId: String,
    container: AppContainer,
    onBack: () -> Unit,
    onNavigateToScoring: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val matchFlow = remember(matchId) { container.matchRepository.getMatchFlowById(matchId) }
    val matchState by matchFlow.collectAsState(initial = null)
    val currentUser by remember(container) { container.authRepository.getCurrentUserFlow() }.collectAsState()
    val currentUserId = currentUser?.uid?.takeIf { it.isNotBlank() }
        ?: remember(container) { container.authRepository.getCurrentUserId() }
    val allUmpires by remember(currentUserId) {
        container.umpireRepository.getMyUmpiresFlow(currentUserId)
    }.collectAsState(initial = emptyList())
    val isGuest = container.authRepository.isGuestMode.collectAsState().value

    val allMatchPredictionsFlow = remember(matchId) {
        container.predictionRepository.observeMatchPredictions(matchId)
    }
    val allMatchPredictions by allMatchPredictionsFlow.collectAsState()

    androidx.compose.runtime.DisposableEffect(matchId) {
        onDispose {
            container.matchRepository.unsubscribeMatchDocument(matchId)
            container.predictionRepository.stopObservingMatch(matchId)
        }
    }

    val match = matchState

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("SCOREBOARD", "OVERS", "COMMENTARY", "PLAYING XI", "MATCH INFO")

    var showScorerManagementDialog by remember { mutableStateOf(false) }
    var showEditMatchDetailsDialog by remember { mutableStateOf(false) }
    var showMoreScoreDialog by remember { mutableStateOf(false) }
    var showPredictionPanelDialog by remember { mutableStateOf(false) }
    var showMatchDetailGuestDialog by remember { mutableStateOf(false) }
    var guestDialogAction by remember { mutableStateOf("লাইভ স্কোরিং পরিচালনা করতে") }

    val sharedPrefs = remember { context.getSharedPreferences("cricup_prefs", android.content.Context.MODE_PRIVATE) }
    var isCommentaryEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("pref_commentary_enabled", true))
    }
    var commentaryLang by remember {
        mutableStateOf(sharedPrefs.getString("pref_commentary_lang", "bn") ?: "bn")
    }

    var showCommentaryPopup by remember { mutableStateOf(false) }
    var activeCommentaryBall by remember { mutableStateOf<BallEvent?>(null) }

    val adaptiveEmerald = adaptiveEmeraldColor()
    val adaptiveGold = adaptiveGoldColor()

    if (match == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading Match Details...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val isHeadScorer = !isGuest && match.isHeadScorer(currentUserId)
    val isSubScorer = !isGuest && !isHeadScorer && match.canUserScore(currentUserId)
    val canScore = !isGuest && (isHeadScorer || isSubScorer)
    val canManage = isHeadScorer

    val ballEvents1Flow = remember(matchId) { container.scoringRepository.getBallEventsFlow(matchId, 1) }
    val ballEvents2Flow = remember(matchId) { container.scoringRepository.getBallEventsFlow(matchId, 2) }
    val ballEvents1 by ballEvents1Flow.collectAsState(initial = emptyList())
    val ballEvents2 by ballEvents2Flow.collectAsState(initial = emptyList())
    val allDbPlayers by remember(currentUserId) {
        container.playerRepository.getMyPlayersFlow(currentUserId)
    }.collectAsState(initial = emptyList())
    val dbPlayerPhotoById = remember(allDbPlayers) {
        allDbPlayers.mapNotNull { p -> p.photoUrl?.takeIf { it.isNotBlank() }?.let { p.id to it } }.toMap()
    }
    val dbPlayerPhotoByName = remember(allDbPlayers) {
        allDbPlayers.mapNotNull { p -> p.photoUrl?.takeIf { it.isNotBlank() }?.let { p.fullName.trim().lowercase() to it } }.toMap()
    }
    val allBallEvents = remember(ballEvents1, ballEvents2) {
        (ballEvents1 + ballEvents2).sortedByDescending { it.timestamp }
    }
    val latestBall = allBallEvents.firstOrNull()
    var lastObservedBallId by remember { mutableStateOf<String?>(null) }
    var activeAnimationEvent by remember { mutableStateOf<com.example.ui.components.ScoreAnimationEvent?>(null) }

    LaunchedEffect(latestBall?.id, isCommentaryEnabled) {
        val ball = latestBall
        if (ball != null) {
            val isNewBall = lastObservedBallId != null && lastObservedBallId != ball.id
            val isRecentInitialBall = lastObservedBallId == null && (System.currentTimeMillis() - ball.timestamp) < 60_000L

            if (isNewBall || isRecentInitialBall) {
                // Trigger 4, 6, or Wicket animation for spectators (skip Retired Hurt)
                if (ball.isWicket && ball.dismissalType != com.example.data.model.DismissalType.RETIRED_HURT) {
                    val dismissed = ball.dismissedPlayerName ?: ball.strikerName
                    val sub = if (ball.dismissalType == com.example.data.model.DismissalType.RUN_OUT) {
                        "$dismissed run out${if (!ball.assistingPlayerName.isNullOrBlank()) " (${ball.assistingPlayerName})" else ""}"
                    } else {
                        "$dismissed (${ball.dismissalType?.displayName ?: "Out"}) • বোলার: ${ball.bowlerName}"
                    }
                    activeAnimationEvent = com.example.ui.components.ScoreAnimationEvent(
                        type = com.example.ui.components.ScoreEventType.WICKET,
                        title = "WICKET! ☝️ OUT",
                        subtitle = sub
                    )
                } else if (ball.runsOffBat == 6) {
                    activeAnimationEvent = com.example.ui.components.ScoreAnimationEvent(
                        type = com.example.ui.components.ScoreEventType.SIX,
                        title = "SIX! 🚀 MAXIMUM 6 RUNS",
                        subtitle = "Massive hit by ${ball.strikerName}"
                    )
                } else if (ball.runsOffBat == 4) {
                    activeAnimationEvent = com.example.ui.components.ScoreAnimationEvent(
                        type = com.example.ui.components.ScoreEventType.FOUR,
                        title = "FOUR! 🏏 4 RUNS",
                        subtitle = "Boundary struck by ${ball.strikerName}"
                    )
                }

                if (isCommentaryEnabled) {
                    activeCommentaryBall = ball
                    showCommentaryPopup = true
                    kotlinx.coroutines.delay(5000)
                    showCommentaryPopup = false
                }
            }
            lastObservedBallId = ball.id
        } else if (!isCommentaryEnabled) {
            showCommentaryPopup = false
        }
    }

    val isScorerOrSubScorer = remember(match, currentUserId) {
        com.example.util.PredictionEvaluator.isScorerOrSubScorer(match, currentUserId)
    }
    val crowdStats = remember(match, allMatchPredictions) {
        container.predictionRepository.computeCrowdStats(match, allMatchPredictions)
    }
    val celebrationQueue = remember { mutableStateListOf<com.example.data.model.PredictionWinCelebration>() }

    LaunchedEffect(
        match.status,
        match.currentInningsNumber,
        match.winnerTeamId,
        ballEvents1.size,
        ballEvents2.size,
        currentUserId,
        isScorerOrSubScorer,
        isGuest
    ) {
        if (!isGuest && !isScorerOrSubScorer && currentUserId.isNotBlank()) {
            val newWins = container.predictionRepository.evaluateAndSettleForUser(
                match = match,
                userId = currentUserId,
                ballEvents1 = ballEvents1,
                ballEvents2 = ballEvents2
            )
            newWins.forEach { cel ->
                if (celebrationQueue.none { it.predictionId == cel.predictionId }) {
                    celebrationQueue.add(cel)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            // Unified Compact Top Panel Bar (Title + Live View/Scorer + Venue + Match Code + Commentary)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("unified_match_top_panel")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Row 1: Navigation + Club League Title + Role Badge + Share/Manage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = match.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                val isOfflineMatch = match.isOfflineOnlyMatch()
                                Surface(
                                    color = if (canScore) adaptiveEmerald.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (canScore) adaptiveEmerald else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                                    )
                                ) {
                                    Text(
                                        text = when {
                                            isOfflineMatch && canScore -> "📱 OFFLINE"
                                            isHeadScorer -> "✏️ Head Scorer"
                                            isSubScorer -> "✏️ Sub Scorer"
                                            isOfflineMatch -> "📱 OFFLINE"
                                            else -> "👁 LIVE VIEW"
                                        },
                                        color = if (canScore) adaptiveEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${match.category} • ${match.venue}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (isGuest) {
                                Surface(
                                    color = EmeraldGreen,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            guestDialogAction = "লাইভ স্কোরিং ও প্রেডিকশনে অংশ নিতে"
                                            showMatchDetailGuestDialog = true
                                        }
                                        .testTag("match_detail_viewer_login_btn")
                                ) {
                                    Text(
                                        text = "লগইন",
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            if (canManage) {
                                IconButton(
                                    onClick = { showEditMatchDetailsDialog = true },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Match Info", tint = adaptiveEmerald, modifier = Modifier.size(19.dp))
                                }
                                if (!match.isOfflineOnlyMatch()) {
                                    IconButton(
                                        onClick = { showScorerManagementDialog = true },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .testTag("manage_sub_scorers_btn")
                                    ) {
                                        Icon(
                                            imageVector = CricUpIcons.GroupAdd,
                                            contentDescription = "Manage Sub Scorers",
                                            tint = adaptiveGold,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { com.example.util.MatchShareHelper.shareMatch(context, match) },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = "Share Match", tint = adaptiveEmerald, modifier = Modifier.size(19.dp))
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    // Row 2: Compact Match Code Copy Pill + Commentary Toggle & Language in ONE bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Match Code Compact Pill (hidden on Offline matches; shows Offline badge instead)
                        val isOfflineMatch = match.isOfflineOnlyMatch()
                        if (isOfflineMatch) {
                            OfflinePill()
                        } else if (match.matchCode.isNotBlank()) {
                            Surface(
                                color = adaptiveGold.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, adaptiveGold.copy(alpha = 0.65f)),
                                modifier = Modifier
                                    .heightIn(min = 34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        com.example.util.MatchShareHelper.copyMatchCode(context, match.matchCode)
                                    }
                                    .testTag("top_bar_match_code_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = "কোড:",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = match.matchCode,
                                        color = adaptiveGold,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(adaptiveEmerald)
                                    )
                                    Icon(
                                        imageVector = CricUpIcons.ContentCopy,
                                        contentDescription = "Copy Match Code",
                                        tint = adaptiveGold,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = match.status.name,
                                    color = adaptiveEmerald,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Compact Commentary Controls (Toggle + বাংলা/EN)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = if (isCommentaryEnabled) adaptiveEmerald.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isCommentaryEnabled) adaptiveEmerald.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .heightIn(min = 34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val next = !isCommentaryEnabled
                                        isCommentaryEnabled = next
                                        sharedPrefs.edit().putBoolean("pref_commentary_enabled", next).apply()
                                    }
                                    .testTag("top_bar_commentary_toggle")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = CricUpIcons.Mic,
                                        contentDescription = "Toggle Commentary",
                                        tint = if (isCommentaryEnabled) adaptiveEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isCommentaryEnabled) "ধারাভাষ্য চালু" else "ধারাভাষ্য বন্ধ",
                                        color = if (isCommentaryEnabled) adaptiveEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .heightIn(min = 34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val nextLang = if (commentaryLang == "bn") "en" else "bn"
                                        commentaryLang = nextLang
                                        sharedPrefs.edit().putString("pref_commentary_lang", nextLang).apply()
                                    }
                                    .testTag("top_bar_commentary_lang")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "বাং",
                                        color = if (commentaryLang == "bn") adaptiveEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (commentaryLang == "bn") FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                    Text("|", color = MaterialTheme.colorScheme.outline, fontSize = 10.sp)
                                    Text(
                                        text = "EN",
                                        color = if (commentaryLang == "en") adaptiveEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (commentaryLang == "en") FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                }
            }
        },
        bottomBar = {
            // Only Head Scorer / Sub-Scorer sees bottom action bar; Spectators have NO bottom Live View Read-Only bar
            if (canScore && (match.status == MatchStatus.LIVE || match.status == MatchStatus.UPCOMING || match.status == MatchStatus.INNINGS_BREAK)) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Button(
                            onClick = { onNavigateToScoring(match.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("resume_scoring_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            val roleLabel = if (isHeadScorer) "HEAD SCORER" else "SUB SCORER"
                            Text(
                                text = if (match.status == MatchStatus.UPCOMING) "START SCORING ($roleLabel)" else "RESUME SCORING ($roleLabel)",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
                // Crisp Bordered Pill Tabs (full unclipped titles for all 5 match detail tabs)
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 8.dp,
                    divider = {},
                    indicator = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTabIndex == index
                        Tab(
                            selected = isSelected,
                            onClick = { selectedTabIndex = index },
                            modifier = Modifier
                                .padding(horizontal = 3.dp, vertical = 6.dp)
                                .testTag("match_detail_tab_$index")
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
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
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Tab Content + Floating Overlay Popups (no empty vertical gap above Scoreboard)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTabIndex) {
                        0 -> ScoreboardTab(
                            match = match,
                            allUmpires = allUmpires,
                            ballEvents1 = ballEvents1,
                            ballEvents2 = ballEvents2,
                            isScorerOrSubScorer = isScorerOrSubScorer,
                            userPredictionPoints = currentUser?.predictionPoints ?: 0,
                            userPredictionsWon = currentUser?.predictionsWon ?: 0,
                            userPredictionsTotal = currentUser?.predictionsTotal ?: 0,
                            crowdStats = crowdStats,
                            dbPlayerPhotoById = dbPlayerPhotoById,
                            dbPlayerPhotoByName = dbPlayerPhotoByName,
                            onOpenPredictionPanel = { showPredictionPanelDialog = true },
                            onOpenMoreScore = { showMoreScoreDialog = true },
                            onOpenOversTab = { selectedTabIndex = 1 }
                        )
                        1 -> OversTab(ballEvents1, ballEvents2)
                        2 -> CommentaryTab(allBallEvents, commentaryLang)
                        3 -> PlayingXITab(match, dbPlayerPhotoById, dbPlayerPhotoByName)
                        4 -> MatchInfoTab(match, allUmpires)
                    }

                    // Overlay 4/6/Wicket & Live Commentary Banner cleanly without shifting content
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .zIndex(10f)
                    ) {
                        com.example.ui.components.ScoreEventAnimationBanner(
                            event = activeAnimationEvent,
                            onDismiss = { activeAnimationEvent = null },
                            modifier = Modifier.fillMaxWidth()
                        )
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showCommentaryPopup && activeCommentaryBall != null,
                            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .fillMaxWidth()
                        ) {
                            activeCommentaryBall?.let { ball ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.5.dp,
                                        if (ball.isWicket) WicketRed else adaptiveGold
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = if (commentaryLang == "en") "🎙️ Live Commentary" else "🎙️ বল-বাই-বল ধারাভাষ্য",
                                                    color = if (ball.isWicket) WicketRed else adaptiveGold,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                BallBadge(
                                                    text = ball.displayBadgeText,
                                                    size = 26.dp
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Ov ${ball.overNumber}.${ball.ballInOver}",
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            IconButton(
                                                onClick = { showCommentaryPopup = false },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Close Commentary",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = com.example.util.CommentaryHelper.formatCommentary(ball, commentaryLang),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 17.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

        com.example.ui.components.PredictionCelebrationOverlay(
            celebration = celebrationQueue.firstOrNull(),
            onDismiss = {
                if (celebrationQueue.isNotEmpty()) {
                    celebrationQueue.removeAt(0)
                }
            },
            modifier = Modifier.zIndex(20f)
        )
    }

    if (showPredictionPanelDialog) {
        Dialog(
            onDismissRequest = { showPredictionPanelDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxWidth(0.95f)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 680.dp)
                        .testTag("spectator_prediction_modal"),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.background,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, adaptiveEmerald.copy(alpha = 0.6f)),
                    tonalElevation = 8.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("prediction_user_points_badge")
                            ) {
                                com.example.ui.components.GoldenCoinIcon(size = 22.dp)
                                Column {
                                    Text(
                                        text = "লাইভ প্রেডিকশন ও ফ্যান ভোট",
                                        color = adaptiveGold,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    val totalPreds = currentUser?.predictionsTotal ?: 0
                                    val wonPreds = currentUser?.predictionsWon ?: 0
                                    val pointsSummary = if (totalPreds > 0) {
                                        "মোট কয়েন: ${currentUser?.predictionPoints ?: 0} • জয়: $wonPreds/$totalPreds"
                                    } else {
                                        "মোট কয়েন: ${currentUser?.predictionPoints ?: 0}"
                                    }
                                    Text(
                                        text = pointsSummary,
                                        color = adaptiveEmerald,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showPredictionPanelDialog = false },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("close_prediction_modal_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Prediction Panel",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            com.example.ui.components.SpectatorPredictionPanel(
                                match = match,
                                ballEvents1 = ballEvents1,
                                ballEvents2 = ballEvents2,
                                currentUserId = currentUserId,
                                isGuest = isGuest,
                                isScorerOrSubScorer = isScorerOrSubScorer,
                                userPredictionPoints = currentUser?.predictionPoints ?: 0,
                                userPredictionsWon = currentUser?.predictionsWon ?: 0,
                                userPredictionsTotal = currentUser?.predictionsTotal ?: 0,
                                allMatchPredictions = allMatchPredictions,
                                crowdStats = crowdStats,
                                showHeader = false,
                                onSubmitOptionPrediction = { category, targetKey, targetLabel, optionKey, optionLabel, rewardPoints, targetInnings, baselineBallCount, targetOverNumber, percentTeamA ->
                                    scope.launch {
                                        val userName = currentUser?.displayName?.ifBlank { "Spectator" } ?: "Spectator"
                                        val result = container.predictionRepository.submitPrediction(
                                            match = match,
                                            userId = currentUserId,
                                            userName = userName,
                                            category = category,
                                            targetKey = targetKey,
                                            targetLabel = targetLabel,
                                            selectedOption = optionKey,
                                            selectedLabel = optionLabel,
                                            rewardPoints = rewardPoints,
                                            targetInnings = targetInnings,
                                            baselineBallCount = baselineBallCount,
                                            targetOverNumber = targetOverNumber,
                                            selectedPercentTeamA = percentTeamA
                                        )
                                        result.onSuccess {
                                            Toast.makeText(
                                                context,
                                                "প্রেডিকশন সেভ হয়েছে: $optionLabel ✓",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }.onFailure { err ->
                                            Toast.makeText(
                                                context,
                                                err.message ?: "প্রেডিকশন জমা দেওয়া যায়নি",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                onRequireAuth = {
                                    showPredictionPanelDialog = false
                                    guestDialogAction = "লাইভ প্রেডিকশন ও ভোটে অংশ নিতে"
                                    showMatchDetailGuestDialog = true
                                }
                            )
                        }
                    }
                }
                com.example.ui.components.PredictionCelebrationOverlay(
                    celebration = celebrationQueue.firstOrNull(),
                    onDismiss = {
                        if (celebrationQueue.isNotEmpty()) {
                            celebrationQueue.removeAt(0)
                        }
                    },
                    modifier = Modifier.zIndex(25f)
                )
            }
        }
    }

    if (showMoreScoreDialog) {
        MoreScoreFullDialog(
            match = match,
            ballEvents1 = ballEvents1,
            ballEvents2 = ballEvents2,
            onDismiss = { showMoreScoreDialog = false }
        )
    }

    // HEAD SCORER & SUB-SCORERS MANAGEMENT MODAL
    if (showMatchDetailGuestDialog) {
        com.example.ui.components.GuestRestrictedDialog(
            actionName = guestDialogAction,
            container = container,
            onDismiss = { showMatchDetailGuestDialog = false }
        )
    }

    if (showScorerManagementDialog && canManage) {
        com.example.ui.components.SubScorerManagementDialog(
            match = match,
            container = container,
            onDismiss = { showScorerManagementDialog = false }
        )
    }

    // EDIT MATCH DETAILS DIALOG (Allows changing match title, venue, teams, logos, umpires anytime from History)
    if (showEditMatchDetailsDialog) {
        var editTitle by remember { mutableStateOf(match.title) }
        var editVenue by remember { mutableStateOf(match.venue) }
        var editTeamAName by remember { mutableStateOf(match.teamA.name) }
        var editTeamALogo by remember { mutableStateOf(match.teamA.logoUrl) }
        var editTeamBName by remember { mutableStateOf(match.teamB.name) }
        var editTeamBLogo by remember { mutableStateOf(match.teamB.logoUrl) }
        var editUmpire1 by remember { mutableStateOf(match.umpire1) }
        var editUmpire2 by remember { mutableStateOf(match.umpire2) }
        var editUmpire3 by remember { mutableStateOf(match.umpire3) }

        val logoPickerA = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val encoded = com.example.util.ImagePickerHelper.compressUriToBase64Async(context, uri)
                    if (encoded != null) {
                        editTeamALogo = encoded
                    } else {
                        Toast.makeText(context, "ছবি প্রসেস করা যায়নি, অন্য ছবি চেষ্টা করুন", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val logoPickerB = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            if (uri != null) {
                scope.launch {
                    val encoded = com.example.util.ImagePickerHelper.compressUriToBase64Async(context, uri)
                    if (encoded != null) {
                        editTeamBLogo = encoded
                    } else {
                        Toast.makeText(context, "ছবি প্রসেস করা যায়নি, অন্য ছবি চেষ্টা করুন", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showEditMatchDetailsDialog = false },
            title = {
                Text("ম্যাচের তথ্য, দল ও আম্পায়ার সম্পাদনা", color = adaptiveGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("Match Title (ম্যাচের নাম)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = adaptiveEmerald
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editVenue,
                            onValueChange = { editVenue = it },
                            label = { Text("Venue (ভেন্যু / খেলার মাঠ)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = adaptiveEmerald
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Team A
                        Text("দল ১ / Team A:", color = adaptiveEmerald, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, adaptiveEmerald, CircleShape)
                                    .clickable {
                                        logoPickerA.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (!editTeamALogo.isNullOrEmpty()) {
                                    val resolvedLogoA = remember(editTeamALogo) { com.example.util.ImageModelResolver.resolveForCoil(editTeamALogo) }
                                    AsyncImage(
                                        model = resolvedLogoA,
                                        contentDescription = "Team A Logo",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text("+লোগো", fontSize = 9.sp, color = adaptiveEmerald)
                                }
                            }
                            OutlinedTextField(
                                value = editTeamAName,
                                onValueChange = { editTeamAName = it },
                                label = { Text("Team A Name") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedBorderColor = adaptiveEmerald
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Team B
                        Text("দল ২ / Team B:", color = adaptiveGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, adaptiveGold, CircleShape)
                                    .clickable {
                                        logoPickerB.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (!editTeamBLogo.isNullOrEmpty()) {
                                    val resolvedLogoB = remember(editTeamBLogo) { com.example.util.ImageModelResolver.resolveForCoil(editTeamBLogo) }
                                    AsyncImage(
                                        model = resolvedLogoB,
                                        contentDescription = "Team B Logo",
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text("+লোগো", fontSize = 9.sp, color = adaptiveGold)
                                }
                            }
                            OutlinedTextField(
                                value = editTeamBName,
                                onValueChange = { editTeamBName = it },
                                label = { Text("Team B Name") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedBorderColor = adaptiveGold
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("আম্পায়ার / ম্যাচ পরিচালক:", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = editUmpire1,
                            onValueChange = { editUmpire1 = it },
                            label = { Text("১ম আম্পায়ার (Umpire 1)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = adaptiveEmerald
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = editUmpire2,
                            onValueChange = { editUmpire2 = it },
                            label = { Text("২য় আম্পায়ার (Umpire 2)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = adaptiveEmerald
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = editUmpire3,
                            onValueChange = { editUmpire3 = it },
                            label = { Text("৩য় আম্পায়ার / রেফারি (Umpire 3 - ঐচ্ছিক)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = adaptiveEmerald
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = match.copy(
                            title = editTitle.trim(),
                            venue = editVenue.trim(),
                            teamA = match.teamA.copy(name = editTeamAName.trim(), logoUrl = editTeamALogo),
                            teamB = match.teamB.copy(name = editTeamBName.trim(), logoUrl = editTeamBLogo),
                            umpire1 = editUmpire1.trim(),
                            umpire2 = editUmpire2.trim(),
                            umpire3 = editUmpire3.trim()
                        )
                        scope.launch {
                            container.matchRepository.updateMatch(updated)
                            showEditMatchDetailsDialog = false
                            Toast.makeText(context, "ম্যাচের তথ্য ও লোগো সফলভাবে সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("সংরক্ষণ করুন", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditMatchDetailsDialog = false }) {
                    Text("বাতিল", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
private fun ScoreboardTab(
    match: Match,
    allUmpires: List<Umpire>,
    ballEvents1: List<BallEvent>,
    ballEvents2: List<BallEvent>,
    isScorerOrSubScorer: Boolean,
    userPredictionPoints: Int,
    userPredictionsWon: Int,
    userPredictionsTotal: Int,
    crowdStats: com.example.data.model.CrowdVoteStats,
    dbPlayerPhotoById: Map<String, String> = emptyMap(),
    dbPlayerPhotoByName: Map<String, String> = emptyMap(),
    onOpenPredictionPanel: () -> Unit,
    onOpenMoreScore: () -> Unit,
    onOpenOversTab: () -> Unit
) {
    val adaptiveEmerald = adaptiveEmeraldColor()
    val adaptiveGold = adaptiveGoldColor()
    var scTabSelectedInnings by remember(match.currentInningsNumber) {
        mutableIntStateOf(if (match.currentInningsNumber == 2 && ballEvents2.isNotEmpty()) 2 else 1)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(match.category, color = adaptiveGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isOfflineMatch = match.isOfflineOnlyMatch()
                            if (isOfflineMatch) {
                                Text("অফলাইন ম্যাচ • লোকাল", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            } else {
                                val secondsAgo = maxOf(0L, (System.currentTimeMillis() - match.lastSyncTimestamp) / 1000)
                                val syncText = when {
                                    secondsAgo < 5 -> "Synced (Just now)"
                                    secondsAgo < 60 -> "Synced (${secondsAgo}s ago)"
                                    else -> "Updated ${secondsAgo / 60}m ago"
                                }
                                Text(syncText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            }
                            if (match.status == MatchStatus.LIVE || match.status == MatchStatus.INNINGS_BREAK) {
                                if (isOfflineMatch) {
                                    OfflinePill()
                                } else {
                                    LivePill()
                                }
                            } else {
                                Text(match.status.name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Team A
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CricUpAvatar(
                                model = match.teamA.logoUrl,
                                size = 44.dp,
                                fallbackText = match.teamA.shortName,
                                fallbackIcon = CricUpIcons.SportsCricket,
                                borderColor = adaptiveEmerald
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(match.teamA.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        val scA = if (match.battingFirstTeamId == match.teamA.id) match.firstInnings else match.secondInnings
                        Text(
                            text = if (scA != null) "${scA.totalRuns}/${scA.wickets} (${scA.oversFormatted})" else "Yet to bat",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Team B
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CricUpAvatar(
                                model = match.teamB.logoUrl,
                                size = 44.dp,
                                fallbackText = match.teamB.shortName,
                                fallbackIcon = CricUpIcons.SportsCricket,
                                borderColor = adaptiveGold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(match.teamB.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        val scB = if (match.battingFirstTeamId == match.teamB.id) match.firstInnings else match.secondInnings
                        Text(
                            text = if (scB != null) "${scB.totalRuns}/${scB.wickets} (${scB.oversFormatted})" else "Yet to bat",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (match.resultDescription.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(10.dp)
                        ) {
                            Text(
                                match.resultDescription,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Officials / Umpires strip with photo avatars
                    if (match.umpire1.isNotBlank() || match.umpire2.isNotBlank() || match.umpire3.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Officials:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            if (match.umpire1.isNotBlank()) {
                                val u1Photo = allUmpires.find { it.fullName.equals(match.umpire1, ignoreCase = true) }?.photoUrl
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    CricUpAvatar(
                                        model = u1Photo,
                                        size = 28.dp,
                                        fallbackText = match.umpire1.take(2),
                                        fallbackIcon = Icons.Default.Person
                                    )
                                    Text(match.umpire1, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            if (match.umpire2.isNotBlank()) {
                                val u2Photo = allUmpires.find { it.fullName.equals(match.umpire2, ignoreCase = true) }?.photoUrl
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                                    CricUpAvatar(
                                        model = u2Photo,
                                        size = 28.dp,
                                        fallbackText = match.umpire2.take(2),
                                        fallbackIcon = Icons.Default.Person
                                    )
                                    Text(match.umpire2, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            if (match.umpire3.isNotBlank()) {
                                val u3Photo = allUmpires.find { it.fullName.equals(match.umpire3, ignoreCase = true) }?.photoUrl
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
                                    CricUpAvatar(
                                        model = u3Photo,
                                        size = 28.dp,
                                        fallbackText = match.umpire3.take(2),
                                        fallbackIcon = Icons.Default.Person
                                    )
                                    Text("${match.umpire3} (3rd)", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    // Quick Zero-Scroll Actions: More Score & All Overs
                    if (match.firstInnings != null || match.secondInnings != null || ballEvents1.isNotEmpty() || ballEvents2.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onOpenMoreScore,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldGreen,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("more_score_btn")
                            ) {
                                Icon(CricUpIcons.Leaderboard, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("মোর স্কোর (More Score)", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = onOpenOversTab,
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, adaptiveGold),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = adaptiveGold),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("quick_all_overs_btn")
                            ) {
                                Text("সকল ওভার 📋", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        item(key = "spectator_prediction_trigger_button") {
            com.example.ui.components.SpectatorPredictionTriggerButton(
                userPredictionPoints = userPredictionPoints,
                userPredictionsWon = userPredictionsWon,
                userPredictionsTotal = userPredictionsTotal,
                isScorerOrSubScorer = isScorerOrSubScorer,
                crowdTotalVotes = crowdStats.totalTeamVotes + crowdStats.totalPercentPollVotes,
                onClick = onOpenPredictionPanel
            )
        }

        if (match.firstInnings != null) {
            item(key = "innings_1_card") {
                InningsScorecardView("1st Innings: ${match.firstInnings.battingTeamName}", match.firstInnings, match, dbPlayerPhotoById, dbPlayerPhotoByName)
            }
        }
        if (match.secondInnings != null) {
            item(key = "innings_2_card") {
                InningsScorecardView("2nd Innings: ${match.secondInnings.battingTeamName}", match.secondInnings, match, dbPlayerPhotoById, dbPlayerPhotoByName)
            }
        }

        val hasBalls = ballEvents1.isNotEmpty() || ballEvents2.isNotEmpty()
        if (hasBalls) {
            item(key = "scoreboard_overs_preview") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "সাম্প্রতিক ওভার বিবরণী (RECENT OVERS)",
                                color = adaptiveGold,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onOpenOversTab,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("সব ওভার দেখুন →", color = adaptiveEmerald, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (ballEvents2.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { scTabSelectedInnings = 1 },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (scTabSelectedInnings == 1) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (scTabSelectedInnings == 1) Color.Black else MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("1st Inn Overs", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                }
                                Button(
                                    onClick = { scTabSelectedInnings = 2 },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (scTabSelectedInnings == 2) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (scTabSelectedInnings == 2) Color.Black else MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("2nd Inn Overs", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                }
                            }
                        }
                        val displayedBalls = if (ballEvents2.isNotEmpty() && scTabSelectedInnings == 2) ballEvents2 else ballEvents1
                        com.example.ui.components.OverTimelineColumn(
                            ballEvents = displayedBalls,
                            maxOversToShow = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenOversTab,
                            border = androidx.compose.foundation.BorderStroke(1.dp, adaptiveEmerald),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = adaptiveEmerald),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("view_all_overs_bottom_btn")
                        ) {
                            Text("সম্পূর্ণ ওভার টাইমলাইন দেখুন (${displayedBalls.size} বল) 📋", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    }
                }
            }
        }

        if (match.firstInnings == null && match.secondInnings == null) {
            item(key = "empty_scorecard") {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Scorecard will be generated once match scoring begins.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun OversTab(
    ballEvents1: List<BallEvent>,
    ballEvents2: List<BallEvent>
) {
    var selectedInnings by remember { mutableIntStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        if (ballEvents2.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { selectedInnings = 1 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedInnings == 1) EmeraldGreen else MaterialTheme.colorScheme.surface,
                        contentColor = if (selectedInnings == 1) Color.Black else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("1st Innings Overs", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Button(
                    onClick = { selectedInnings = 2 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedInnings == 2) EmeraldGreen else MaterialTheme.colorScheme.surface,
                        contentColor = if (selectedInnings == 2) Color.Black else MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("2nd Innings Overs", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        val activeBalls = if (selectedInnings == 1) ballEvents1 else ballEvents2
        com.example.ui.components.OverTimelineList(ballEvents = activeBalls, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun InningsScorecardView(
    title: String,
    innings: InningsScorecard,
    match: Match? = null,
    dbPlayerPhotoById: Map<String, String> = emptyMap(),
    dbPlayerPhotoByName: Map<String, String> = emptyMap()
) {
    val adaptiveEmerald = adaptiveEmeraldColor()
    val adaptiveGold = adaptiveGoldColor()
    val photoLookup = remember(match, dbPlayerPhotoById, dbPlayerPhotoByName) {
        val players = match?.let { m ->
            (m.playingXI_A + m.playingXI_B + m.fullSquad_A + m.fullSquad_B)
        } ?: emptyList()
        val byId = HashMap<String, String?>(dbPlayerPhotoById)
        val byName = HashMap<String, String?>(dbPlayerPhotoByName)
        players.forEach { p ->
            if (!p.photoUrl.isNullOrBlank()) {
                byId[p.id] = p.photoUrl
                byName[p.name.trim().lowercase()] = p.photoUrl
            }
        }
        Pair(byId, byName)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = adaptiveGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (innings.isScoreReconciled) {
                    Surface(color = adaptiveEmerald.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                        Text(" MCC Reconciled ✓ ", color = adaptiveEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Batting Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Pos Batter", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(2.2f))
                Text("R", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                Text("B", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                Text("4s", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(0.6f))
                Text("6s", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(0.6f))
                Text("SR", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(1f))
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
            Spacer(modifier = Modifier.height(4.dp))

            innings.batters.forEach { b ->
                val pPhoto = photoLookup.first[b.playerId] ?: photoLookup.second[b.playerName.trim().lowercase()]
                Column(modifier = Modifier.padding(vertical = 3.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.weight(2.2f), verticalAlignment = Alignment.CenterVertically) {
                            Text("#${b.battingPosition} ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            CricUpAvatar(
                                model = pPhoto,
                                size = 28.dp,
                                fallbackText = b.playerName.take(2),
                                fallbackIcon = Icons.Default.Person
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = b.playerName,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (b.isCaptain) {
                                Text(" (C)", color = adaptiveGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            if (b.isWicketkeeper) {
                                Text(" (†)", color = adaptiveEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text("${b.runs}", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                        Text("${b.balls}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(0.7f))
                        Text("${b.fours}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(0.6f))
                        Text("${b.sixes}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(0.6f))
                        Text(String.format(Locale.US, "%.1f", b.strikeRate), color = adaptiveEmerald, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(start = 28.dp)) {
                        Text(b.dismissalText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Extras: ${innings.totalExtras} (wd ${innings.wides}, nb ${innings.noBalls}, b ${innings.byes}, lb ${innings.legByes}, p ${innings.penalties})",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Text(
                "Total: ${innings.totalRuns}/${innings.wickets} in ${innings.oversFormatted} overs (RR: ${String.format(Locale.US, "%.2f", innings.runRate)})",
                color = adaptiveEmerald,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            // MCC Reconciliation summary strip
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (innings.isScoreReconciled) adaptiveEmerald.copy(alpha = 0.08f) else adaptiveGold.copy(alpha = 0.1f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    Text(
                        text = "Score Audit: Batting (${innings.totalBatsmanRuns}) + Extras (${innings.totalExtras}) = ${innings.totalRuns} | Bowlers Conceded = ${innings.totalBowlerRunsConceded}",
                        color = if (innings.isScoreReconciled) adaptiveEmerald else adaptiveGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            // Bowlers Header
            Text("Bowling Analysis", color = adaptiveGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Bowler", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(2f))
                Text("O", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(0.6f))
                Text("M(WM)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(0.9f))
                Text("R", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(0.6f))
                Text("W", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(0.6f))
                Text("ECON", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(0.9f))
                Text("AVG", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(0.8f))
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))

            innings.bowlers.forEach { bw ->
                val bwPhoto = photoLookup.first[bw.playerId] ?: photoLookup.second[bw.playerName.trim().lowercase()]
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(modifier = Modifier.weight(2f), verticalAlignment = Alignment.CenterVertically) {
                        CricUpAvatar(
                            model = bwPhoto,
                            size = 28.dp,
                            fallbackText = bw.playerName.take(2),
                            fallbackIcon = CricUpIcons.SportsCricket
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = bw.playerName,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Text(bw.oversString, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(0.6f))
                    Text("${bw.maidens}(${bw.wicketMaidens})", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(0.9f))
                    Text("${bw.runsConceded}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(0.6f))
                    Text("${bw.wickets}", color = WicketRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
                    Text(String.format(Locale.US, "%.1f", bw.economy), color = adaptiveGold, fontSize = 11.sp, modifier = Modifier.weight(0.9f))
                    val avgStr = if (bw.wickets > 0) String.format(Locale.US, "%.1f", bw.average) else "-"
                    Text(avgStr, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.weight(0.8f))
                }
            }
        }
    }
}

@Composable
private fun BallByBallTab(events: List<BallEvent>) {
    if (events.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No ball-by-ball deliveries recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        itemsIndexed(events, key = { idx, ball -> "${idx}_${ball.id}" }) { _, ball ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BallBadge(text = ball.displayBadgeText, size = 32.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Over ${ball.overNumber}.${ball.ballInOver} • ${ball.bowlerName} to ${ball.strikerName}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = ball.commentary.ifBlank { com.example.util.CommentaryHelper.formatCommentary(ball) },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentaryTab(events: List<BallEvent>, lang: String = "bn") {
    val adaptiveGold = adaptiveGoldColor()
    if (events.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Live match commentary will stream here ball-by-ball.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        itemsIndexed(events, key = { idx, ball -> "${idx}_${ball.id}" }) { _, ball ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${ball.overNumber}.${ball.ballInOver}",
                    color = adaptiveGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(42.dp)
                )
                BallBadge(
                    text = ball.displayBadgeText,
                    size = 26.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = com.example.util.CommentaryHelper.formatCommentary(ball, lang),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
    }
}

@Composable
private fun PlayingXITab(
    match: Match,
    dbPlayerPhotoById: Map<String, String> = emptyMap(),
    dbPlayerPhotoByName: Map<String, String> = emptyMap()
) {
    val adaptiveEmerald = adaptiveEmeraldColor()
    val adaptiveGold = adaptiveGoldColor()
    val squadA = match.playingXI_A.distinctBy { it.id }.distinctBy { it.name.trim().lowercase() }
    val squadB = match.playingXI_B.distinctBy { it.id }.distinctBy { it.name.trim().lowercase() }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        item {
            Text("${match.teamA.name} Playing Squad (${squadA.size} Players / খেলোয়াড়)", color = adaptiveEmerald, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
        }
        items(squadA, key = { "A_${it.id}_${it.name}" }) { p ->
            val isCapt = p.isCaptain || p.id == match.team1CaptainId || p.id == match.team2CaptainId
            val resolvedPhoto = p.photoUrl?.takeIf { it.isNotBlank() }
                ?: dbPlayerPhotoById[p.id]
                ?: dbPlayerPhotoByName[p.name.trim().lowercase()]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 3.dp)
            ) {
                CricUpAvatar(
                    model = resolvedPhoto,
                    size = 34.dp,
                    fallbackText = p.name.take(2),
                    fallbackIcon = Icons.Default.Person
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${p.name} (${p.role})${if (isCapt) " [C]" else ""}${if (p.isWicketkeeper) " [WK]" else ""}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text("${match.teamB.name} Playing Squad (${squadB.size} Players / খেলোয়াড়)", color = adaptiveGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
        }
        items(squadB, key = { "B_${it.id}_${it.name}" }) { p ->
            val isCapt = p.isCaptain || p.id == match.team1CaptainId || p.id == match.team2CaptainId
            val resolvedPhoto = p.photoUrl?.takeIf { it.isNotBlank() }
                ?: dbPlayerPhotoById[p.id]
                ?: dbPlayerPhotoByName[p.name.trim().lowercase()]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 3.dp)
            ) {
                CricUpAvatar(
                    model = resolvedPhoto,
                    size = 34.dp,
                    fallbackText = p.name.take(2),
                    fallbackIcon = Icons.Default.Person
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${p.name} (${p.role})${if (isCapt) " [C]" else ""}${if (p.isWicketkeeper) " [WK]" else ""}",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun MatchInfoTab(match: Match, allUmpires: List<Umpire>) {
    val adaptiveGold = adaptiveGoldColor()
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("MATCH INFORMATION", color = adaptiveGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    InfoRow("Title", match.title)
                    if (match.isOfflineOnlyMatch()) {
                        InfoRow("Match Mode", "Offline (Local Device Only)")
                    } else {
                        InfoRow("Unique Match Code", if (match.matchCode.isNotBlank()) match.matchCode else "Not Assigned")
                    }
                    InfoRow("Category", match.category)
                    InfoRow("Format", "${match.format} (${match.totalOvers} Overs per side)")
                    InfoRow("Venue", match.venue)
                    val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(match.matchDate))
                    InfoRow("Date & Time", dateStr)
                    InfoRow("Toss", "${if (match.tossWinnerTeamId == match.teamA.id) match.teamA.name else match.teamB.name} opted to ${match.tossDecision.lowercase()}")
                    if (match.umpire1.isNotBlank()) {
                        val u1Photo = allUmpires.find { it.fullName.equals(match.umpire1, ignoreCase = true) }?.photoUrl
                        InfoRowWithPhoto("Umpire 1 (১ম আম্পায়ার)", match.umpire1, u1Photo)
                    }
                    if (match.umpire2.isNotBlank()) {
                        val u2Photo = allUmpires.find { it.fullName.equals(match.umpire2, ignoreCase = true) }?.photoUrl
                        InfoRowWithPhoto("Umpire 2 (২য় আম্পায়ার)", match.umpire2, u2Photo)
                    }
                    if (match.umpire3.isNotBlank()) {
                        val u3Photo = allUmpires.find { it.fullName.equals(match.umpire3, ignoreCase = true) }?.photoUrl
                        InfoRowWithPhoto("Umpire 3 (৩য় আম্পায়ার / রেফারি)", match.umpire3, u3Photo)
                    }
                    InfoRow("Match Owner", match.ownerName)
                    InfoRow("Match ID", match.id)
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoRowWithPhoto(label: String, value: String, photoUrl: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CricUpAvatar(
                model = photoUrl,
                size = 30.dp,
                fallbackText = value.take(2),
                fallbackIcon = Icons.Default.Person
            )
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Full-screen "More Score" explorer modal allowing users to inspect Batting, Bowling,
 * Extras, Match Summary, and Over Timeline across both innings via instant section tabs
 * with minimal or zero scrolling.
 */
@Composable
private fun MoreScoreFullDialog(
    match: Match,
    ballEvents1: List<BallEvent>,
    ballEvents2: List<BallEvent>,
    onDismiss: () -> Unit
) {
    val adaptiveEmerald = adaptiveEmeraldColor()
    val adaptiveGold = adaptiveGoldColor()
    var selectedInnings by remember(match.currentInningsNumber) {
        mutableIntStateOf(if (match.currentInningsNumber == 2 && match.secondInnings != null) 2 else 1)
    }
    var selectedSection by remember { mutableIntStateOf(0) } // 0: Batting, 1: Bowling, 2: Overs

    val activeInnings = if (selectedInnings == 2 && match.secondInnings != null) match.secondInnings else match.firstInnings
    val activeBalls = if (selectedInnings == 2 && ballEvents2.isNotEmpty()) ballEvents2 else ballEvents1

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, adaptiveEmerald.copy(alpha = 0.7f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "মোর স্কোর / সম্পূর্ণ স্কোরবোর্ড",
                            color = adaptiveGold,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = match.title,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            maxLines = 1
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close More Score", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Compact Score Summary Strip (Always Visible)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sc1 = match.firstInnings
                    val sc2 = match.secondInnings
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sc1?.battingTeamName ?: match.teamA.name,
                            color = adaptiveEmerald,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = if (sc1 != null) "${sc1.totalRuns}/${sc1.wickets} (${sc1.oversFormatted})" else "Yet to bat",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(
                            text = sc2?.battingTeamName ?: match.teamB.name,
                            color = adaptiveGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = if (sc2 != null) "${sc2.totalRuns}/${sc2.wickets} (${sc2.oversFormatted})" else "Yet to bat",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                // Innings Switcher (when 2nd innings exists)
                if (match.secondInnings != null || ballEvents2.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { selectedInnings = 1 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedInnings == 1) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedInnings == 1) Color.Black else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("1st Innings", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { selectedInnings = 2 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedInnings == 2) GoldYellow else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (selectedInnings == 2) Color.Black else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("2nd Innings", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }

                // Section Switcher: Batting / Bowling / Overs (Eliminates vertical scrolling)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        0 to "🏏 ব্যাটিং (${activeInnings?.batters?.size ?: 0})",
                        1 to "🎯 বোলিং (${activeInnings?.bowlers?.size ?: 0})",
                        2 to "📋 ওভার (${activeBalls.size})"
                    ).forEach { (idx, label) ->
                        val active = selectedSection == idx
                        Button(
                            onClick = { selectedSection = idx },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (active) EmeraldGreen else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (active) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                // Dedicated Section Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    when (selectedSection) {
                        0 -> {
                            val batters = activeInnings?.batters ?: emptyList()
                            if (batters.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("এই ইনিংসে এখনো ব্যাটিং রেকর্ড নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    item(key = "bat_hdr") {
                                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                                            Text("Batter", color = adaptiveGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2.2f))
                                            Text("R", color = adaptiveGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
                                            Text("B", color = adaptiveGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
                                            Text("4s", color = adaptiveGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
                                            Text("6s", color = adaptiveGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
                                            Text("SR", color = adaptiveGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                    }
                                    itemsIndexed(batters, key = { idx, b -> "${idx}_${b.playerId}_${b.battingPosition}" }) { _, b ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                .padding(horizontal = 10.dp, vertical = 7.dp)
                                        ) {
                                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "#${b.battingPosition} ${b.playerName}",
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(2.2f),
                                                    maxLines = 1
                                                )
                                                Text("${b.runs}", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(0.6f))
                                                Text("${b.balls}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(0.6f))
                                                Text("${b.fours}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(0.6f))
                                                Text("${b.sixes}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(0.6f))
                                                Text(String.format(Locale.US, "%.1f", b.strikeRate), color = adaptiveEmerald, fontSize = 11.sp, modifier = Modifier.weight(0.9f))
                                            }
                                            Text(b.dismissalText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                        }
                                    }
                                    item(key = "bat_extras") {
                                        activeInnings?.let { inn ->
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                "Extras: ${inn.totalExtras} (wd ${inn.wides}, nb ${inn.noBalls}, b ${inn.byes}, lb ${inn.legByes})",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.5.sp
                                            )
                                            Text(
                                                "Total: ${inn.totalRuns}/${inn.wickets} (${inn.oversFormatted} Ov • RR: ${String.format(Locale.US, "%.2f", inn.runRate)})",
                                                color = adaptiveEmerald,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            val bowlers = activeInnings?.bowlers ?: emptyList()
                            if (bowlers.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("এই ইনিংসে এখনো বোলিং রেকর্ড নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    item(key = "bowl_hdr") {
                                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                                            Text("Bowler", color = adaptiveGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                                            Text("O", color = adaptiveGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                                            Text("M", color = adaptiveGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
                                            Text("R", color = adaptiveGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.7f))
                                            Text("W", color = adaptiveGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.6f))
                                            Text("ECON", color = adaptiveGold, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                    }
                                    itemsIndexed(bowlers, key = { idx, bw -> "${idx}_${bw.playerId}_${bw.playerName}" }) { _, bw ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                .padding(horizontal = 10.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(bw.playerName, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f), maxLines = 1)
                                            Text(bw.oversString, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(0.7f))
                                            Text("${bw.maidens}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(0.6f))
                                            Text("${bw.runsConceded}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(0.7f))
                                            Text("${bw.wickets}", color = WicketRed, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(0.6f))
                                            Text(String.format(Locale.US, "%.1f", bw.economy), color = adaptiveGold, fontSize = 11.5.sp, modifier = Modifier.weight(0.9f))
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            com.example.ui.components.OverTimelineList(
                                ballEvents = activeBalls,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

