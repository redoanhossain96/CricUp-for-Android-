package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BallEvent
import com.example.data.model.CrowdVoteStats
import com.example.data.model.Match
import com.example.data.model.MatchPrediction
import com.example.data.model.PredictionCategory
import com.example.data.model.PredictionOptionSpec
import com.example.data.model.PredictionStatus
import com.example.ui.theme.CricUpIcons
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.WicketRed
import com.example.ui.theme.adaptiveEmeraldColor
import com.example.ui.theme.adaptiveGoldColor
import com.example.util.PredictionEvaluator
import kotlin.math.roundToInt

@Composable
fun GoldenCoinIcon(
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)

        // Outer metallic gold coin face
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF176),
                    Color(0xFFFFCA28),
                    Color(0xFFF59E0B)
                ),
                center = Offset(center.x - radius * 0.25f, center.y - radius * 0.25f),
                radius = radius * 1.4f
            ),
            radius = radius,
            center = center
        )
        // Outer amber rim
        drawCircle(
            color = Color(0xFFB45309),
            radius = radius - 1.dp.toPx(),
            center = center,
            style = Stroke(width = 1.4.dp.toPx())
        )
        // Inner embossed ring
        drawCircle(
            color = Color(0xFFFFFDE7).copy(alpha = 0.72f),
            radius = radius * 0.68f,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
        // Center mint star/pillar emblem
        drawLine(
            color = Color(0xFF92400E),
            start = Offset(center.x, center.y - radius * 0.34f),
            end = Offset(center.x, center.y + radius * 0.34f),
            strokeWidth = (radius * 0.24f).coerceAtLeast(1.6.dp.toPx()),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color(0xFFFFFDE7).copy(alpha = 0.8f),
            radius = radius * 0.14f,
            center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f)
        )
    }
}

@Composable
fun SpectatorPredictionTriggerButton(
    userPredictionPoints: Int,
    userPredictionsWon: Int,
    userPredictionsTotal: Int,
    isScorerOrSubScorer: Boolean,
    crowdTotalVotes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emerald = adaptiveEmeraldColor()
    val gold = adaptiveGoldColor()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("open_prediction_panel_btn"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.3.dp,
            brush = Brush.horizontalGradient(listOf(emerald, gold))
        ),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(emerald.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎯", fontSize = 18.sp)
                }
                Column {
                    Text(
                        text = "লাইভ প্রেডিকশন ও ফ্যান ভোট",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            isScorerOrSubScorer -> "স্কোরার মোড • দর্শক মতামত দেখতে ট্যাপ করুন"
                            crowdTotalVotes > 0 -> "নেক্সট বল, ওভার ও উইনিং ভোট ($crowdTotalVotes ভোট) • ট্যাপ করুন"
                            else -> "বল, ওভার ও উইনিং ভোট দিয়ে কয়েন জিতুন • ট্যাপ করুন"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                color = gold.copy(alpha = 0.16f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, gold.copy(alpha = 0.65f)),
                modifier = Modifier.testTag("prediction_trigger_coin_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    GoldenCoinIcon(size = 18.dp)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$userPredictionPoints",
                            color = gold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (userPredictionsTotal > 0) {
                            Text(
                                text = "$userPredictionsWon/$userPredictionsTotal",
                                color = emerald,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "›",
                        color = gold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpectatorPredictionPanel(
    match: Match,
    ballEvents1: List<BallEvent>,
    ballEvents2: List<BallEvent>,
    currentUserId: String,
    isGuest: Boolean,
    isScorerOrSubScorer: Boolean,
    userPredictionPoints: Int,
    userPredictionsWon: Int,
    userPredictionsTotal: Int,
    allMatchPredictions: List<MatchPrediction>,
    crowdStats: CrowdVoteStats,
    onSubmitOptionPrediction: (
        category: PredictionCategory,
        targetKey: String,
        targetLabel: String,
        optionKey: String,
        optionLabel: String,
        rewardPoints: Int,
        targetInnings: Int,
        baselineBallCount: Int,
        targetOverNumber: Int,
        percentTeamA: Int
    ) -> Unit,
    onRequireAuth: () -> Unit,
    showHeader: Boolean = true,
    modifier: Modifier = Modifier
) {
    val emerald = adaptiveEmeraldColor()
    val gold = adaptiveGoldColor()
    var selectedCategory by remember { mutableStateOf(PredictionCategory.NEXT_BALL) }
    val userPredictions = remember(allMatchPredictions, currentUserId) {
        allMatchPredictions.filter { it.userId == currentUserId }
    }
    val nextBallTarget = remember(match, ballEvents1, ballEvents2) {
        PredictionEvaluator.computeNextBallTarget(match, ballEvents1, ballEvents2)
    }
    val nextOverTarget = remember(match, ballEvents1, ballEvents2) {
        PredictionEvaluator.computeNextOverTarget(match, ballEvents1, ballEvents2)
    }
    val activeInnings = PredictionEvaluator.activeInningsForPrediction(match)
    val activeBallsCount = remember(activeInnings, ballEvents1, ballEvents2) {
        PredictionEvaluator.sortedInningsBalls(if (activeInnings == 1) ballEvents1 else ballEvents2).size
    }
    val acceptingPredictions = PredictionEvaluator.isMatchAcceptingPredictions(match)

    val panelContainerModifier = if (showHeader) {
        modifier
            .fillMaxWidth()
            .testTag("spectator_prediction_panel")
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.3.dp, emerald.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    } else {
        modifier
            .fillMaxWidth()
            .testTag("spectator_prediction_panel")
    }

    Column(
        modifier = panelContainerModifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (showHeader) {
            // Header + User Profile Points Pill (shown when embedded inline; omitted inside modal which already has its own top header)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🎯 লাইভ প্রেডিকশন ও ফ্যান ভোট",
                        color = gold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (isScorerOrSubScorer) {
                            "ফেয়ার প্লে মোড • স্কোরার প্রেডিকশনের বাইরে"
                        } else {
                            "সঠিক অনুমান বা ভোটে কয়েন জিতে প্রোফাইলে যোগ করুন!"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    color = gold.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, gold.copy(alpha = 0.55f)),
                    modifier = Modifier.testTag("prediction_user_points_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GoldenCoinIcon(size = 18.dp)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$userPredictionPoints কয়েন",
                                color = gold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (userPredictionsTotal > 0) {
                                Text(
                                    text = "জয়: $userPredictionsWon/$userPredictionsTotal",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Strict Scorer / Sub-Scorer Exclusion Banner
        if (isScorerOrSubScorer) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("prediction_scorer_blocked_banner"),
                color = WicketRed.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, WicketRed.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Scorer Restricted",
                        tint = WicketRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "স্কোরার / সাব-স্কোরার প্রেডিকশন বন্ধ",
                            color = WicketRed,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "আপনি এই ম্যাচের Head Scorer বা Sub-Scorer হওয়ায় ফেয়ার প্লে নিয়ম অনুযায়ী প্রেডিকশন ও ভোটে অংশ নিতে পারবেন না।",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Read-only Crowd Poll Bar so scorers can still observe fan sentiment
            if (crowdStats.totalTeamVotes > 0 || crowdStats.totalPercentPollVotes > 0) {
                CrowdConsensusBar(match = match, crowdStats = crowdStats)
            }
            return@Column
        }

        // All 5 Spectator Category Tabs rendered cleanly in full view (no clipping or hidden scroll)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                PredictionCategoryTabButton(
                    category = PredictionCategory.NEXT_BALL,
                    subtitle = "+15–50 কয়েন",
                    selected = selectedCategory == PredictionCategory.NEXT_BALL,
                    onClick = { selectedCategory = PredictionCategory.NEXT_BALL },
                    modifier = Modifier.weight(1f)
                )
                PredictionCategoryTabButton(
                    category = PredictionCategory.NEXT_OVER,
                    subtitle = "+15–35 কয়েন",
                    selected = selectedCategory == PredictionCategory.NEXT_OVER,
                    onClick = { selectedCategory = PredictionCategory.NEXT_OVER },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                PredictionCategoryTabButton(
                    category = PredictionCategory.ACTION_CHALLENGE,
                    subtitle = "+25–50 কয়েন",
                    selected = selectedCategory == PredictionCategory.ACTION_CHALLENGE,
                    onClick = { selectedCategory = PredictionCategory.ACTION_CHALLENGE },
                    modifier = Modifier.weight(1f)
                )
                PredictionCategoryTabButton(
                    category = PredictionCategory.WINNING_TEAM,
                    subtitle = "ম্যাচ জয় +50 কয়েন",
                    selected = selectedCategory == PredictionCategory.WINNING_TEAM,
                    onClick = { selectedCategory = PredictionCategory.WINNING_TEAM },
                    modifier = Modifier.weight(1f)
                )
            }
            PredictionCategoryTabButton(
                category = PredictionCategory.WINNING_PERCENT,
                subtitle = "লাইভ উইনিং % ফ্যান পোল • +30–45 কয়েন",
                selected = selectedCategory == PredictionCategory.WINNING_PERCENT,
                onClick = { selectedCategory = PredictionCategory.WINNING_PERCENT },
                modifier = Modifier.fillMaxWidth()
            )
        }

            if (!acceptingPredictions) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ম্যাচ সমাপ্ত হওয়ায় নতুন প্রেডিকশন বন্ধ রয়েছে। নিচে আপনার ফলাফল ও দর্শক ভোট দেখুন।",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Category Body
            if (acceptingPredictions) {
                when (selectedCategory) {
                    PredictionCategory.NEXT_BALL -> {
                        val existing = userPredictions.firstOrNull {
                            it.resolvedCategory() == PredictionCategory.NEXT_BALL &&
                                it.targetKey == nextBallTarget.targetKey
                        }
                        TargetBanner(
                            title = "পরবর্তী ডেলিভারি: ${nextBallTarget.targetLabel}",
                            lockedLabel = existing?.selectedLabel
                        )
                        OptionGrid(
                            options = PredictionEvaluator.nextBallOptions,
                            selectedOptionKey = existing?.selectedOption,
                            enabled = existing == null,
                            optionCounts = crowdStats.categoryOptionCounts,
                            countPrefix = "${PredictionCategory.NEXT_BALL.name}:${nextBallTarget.targetKey}:",
                            onSelect = { spec ->
                                if (isGuest) {
                                    onRequireAuth()
                                } else {
                                    onSubmitOptionPrediction(
                                        PredictionCategory.NEXT_BALL,
                                        nextBallTarget.targetKey,
                                        nextBallTarget.targetLabel,
                                        spec.key,
                                        spec.label,
                                        spec.rewardPoints,
                                        nextBallTarget.inningsNumber,
                                        nextBallTarget.baselineBallCount,
                                        0,
                                        50
                                    )
                                }
                            }
                        )
                    }

                    PredictionCategory.NEXT_OVER -> {
                        val existing = userPredictions.firstOrNull {
                            it.resolvedCategory() == PredictionCategory.NEXT_OVER &&
                                it.targetKey == nextOverTarget.targetKey
                        }
                        TargetBanner(
                            title = "টার্গেট ওভার: ${nextOverTarget.targetLabel}",
                            lockedLabel = existing?.selectedLabel
                        )
                        OptionGrid(
                            options = PredictionEvaluator.nextOverOptions,
                            selectedOptionKey = existing?.selectedOption,
                            enabled = existing == null,
                            optionCounts = crowdStats.categoryOptionCounts,
                            countPrefix = "${PredictionCategory.NEXT_OVER.name}:${nextOverTarget.targetKey}:",
                            onSelect = { spec ->
                                if (isGuest) {
                                    onRequireAuth()
                                } else {
                                    onSubmitOptionPrediction(
                                        PredictionCategory.NEXT_OVER,
                                        nextOverTarget.targetKey,
                                        nextOverTarget.targetLabel,
                                        spec.key,
                                        spec.label,
                                        spec.rewardPoints,
                                        nextOverTarget.inningsNumber,
                                        activeBallsCount,
                                        nextOverTarget.targetOverZeroBased,
                                        50
                                    )
                                }
                            }
                        )
                    }

                    PredictionCategory.ACTION_CHALLENGE -> {
                        val pendingAction = userPredictions.firstOrNull {
                            it.resolvedCategory() == PredictionCategory.ACTION_CHALLENGE &&
                                it.resolvedStatus() == PredictionStatus.PENDING
                        }
                        val targetKey = "inn${activeInnings}_act_from_${activeBallsCount}"
                        val targetLabel = "ইনিংস $activeInnings • বল #$activeBallsCount থেকে অ্যাকশন"
                        TargetBanner(
                            title = "লাইভ অ্যাকশন চ্যালেঞ্জ (ইনিংস $activeInnings)",
                            lockedLabel = pendingAction?.selectedLabel
                        )
                        OptionGrid(
                            options = PredictionEvaluator.actionChallengeOptions,
                            selectedOptionKey = pendingAction?.selectedOption,
                            enabled = pendingAction == null,
                            optionCounts = crowdStats.categoryOptionCounts,
                            countPrefix = "${PredictionCategory.ACTION_CHALLENGE.name}:$targetKey:",
                            onSelect = { spec ->
                                if (isGuest) {
                                    onRequireAuth()
                                } else {
                                    onSubmitOptionPrediction(
                                        PredictionCategory.ACTION_CHALLENGE,
                                        targetKey,
                                        targetLabel,
                                        spec.key,
                                        spec.label,
                                        spec.rewardPoints,
                                        activeInnings,
                                        activeBallsCount,
                                        0,
                                        50
                                    )
                                }
                            }
                        )
                    }

                    PredictionCategory.WINNING_TEAM -> {
                        val existingVote = userPredictions.firstOrNull {
                            it.resolvedCategory() == PredictionCategory.WINNING_TEAM &&
                                it.targetKey == "match_winner"
                        }
                        Text(
                            text = "ম্যাচে কোন দল জিতবে? ভোট দিন (+50 পয়েন্ট)",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TeamVoteButton(
                                teamName = match.teamA.name,
                                shortName = match.teamA.shortName,
                                logoUrl = match.teamA.logoUrl,
                                votePercent = crowdStats.teamAVotePercent,
                                voteCount = crowdStats.teamAVotes,
                                isSelected = existingVote?.selectedOption == match.teamA.id,
                                accentColor = emerald,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("vote_winning_team_a_btn"),
                                onClick = {
                                    if (isGuest) {
                                        onRequireAuth()
                                    } else {
                                        onSubmitOptionPrediction(
                                            PredictionCategory.WINNING_TEAM,
                                            "match_winner",
                                            "ম্যাচ জয়ী দল ভোট",
                                            match.teamA.id,
                                            "${match.teamA.name} জিতবে",
                                            50,
                                            activeInnings,
                                            activeBallsCount,
                                            0,
                                            100
                                        )
                                    }
                                }
                            )
                            TeamVoteButton(
                                teamName = match.teamB.name,
                                shortName = match.teamB.shortName,
                                logoUrl = match.teamB.logoUrl,
                                votePercent = crowdStats.teamBVotePercent,
                                voteCount = crowdStats.teamBVotes,
                                isSelected = existingVote?.selectedOption == match.teamB.id,
                                accentColor = gold,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("vote_winning_team_b_btn"),
                                onClick = {
                                    if (isGuest) {
                                        onRequireAuth()
                                    } else {
                                        onSubmitOptionPrediction(
                                            PredictionCategory.WINNING_TEAM,
                                            "match_winner",
                                            "ম্যাচ জয়ী দল ভোট",
                                            match.teamB.id,
                                            "${match.teamB.name} জিতবে",
                                            50,
                                            activeInnings,
                                            activeBallsCount,
                                            0,
                                            0
                                        )
                                    }
                                }
                            )
                        }
                    }

                    PredictionCategory.WINNING_PERCENT -> {
                        val existingPct = userPredictions.firstOrNull {
                            it.resolvedCategory() == PredictionCategory.WINNING_PERCENT &&
                                it.targetKey == "win_percent_poll"
                        }
                        var sliderTeamAPct by remember(existingPct?.selectedPercentTeamA) {
                            mutableFloatStateOf((existingPct?.selectedPercentTeamA ?: 55).toFloat())
                        }
                        val teamAPctInt = ((sliderTeamAPct / 5f).roundToInt() * 5).coerceIn(10, 90)
                        val teamBPctInt = 100 - teamAPctInt
                        val quickPresets = listOf(80, 65, 50, 35, 20)

                        Text(
                            text = "ম্যাচ উইনিং % ভোট দিন (সঠিক হলে +30 থেকে +45 পয়েন্ট)",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${match.teamA.shortName.ifBlank { match.teamA.name }}: $teamAPctInt%",
                                color = emerald,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${match.teamB.shortName.ifBlank { match.teamB.name }}: $teamBPctInt%",
                                color = gold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Slider(
                            value = sliderTeamAPct,
                            onValueChange = { sliderTeamAPct = it },
                            valueRange = 10f..90f,
                            steps = 15,
                            colors = SliderDefaults.colors(
                                thumbColor = emerald,
                                activeTrackColor = emerald,
                                inactiveTrackColor = gold.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.testTag("winning_percent_slider")
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            quickPresets.forEach { presetA ->
                                val label = when {
                                    presetA == 50 -> "৫০-৫০ থ্রিলার"
                                    presetA > 50 -> "${match.teamA.shortName.ifBlank { "A" }} $presetA%"
                                    else -> "${match.teamB.shortName.ifBlank { "B" }} ${100 - presetA}%"
                                }
                                FilterChip(
                                    selected = teamAPctInt == presetA,
                                    onClick = { sliderTeamAPct = presetA.toFloat() },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.heightIn(min = 38.dp)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (isGuest) {
                                    onRequireAuth()
                                } else {
                                    val favoredName = when {
                                        teamAPctInt > 50 -> "${match.teamA.name} ($teamAPctInt%)"
                                        teamAPctInt < 50 -> "${match.teamB.name} ($teamBPctInt%)"
                                        else -> "৫০-৫০ থ্রিলার ম্যাচ"
                                    }
                                    val maxConf = maxOf(teamAPctInt, teamBPctInt)
                                    val reward = if (maxConf >= 70) 45 else if (teamAPctInt == 50) 40 else 30
                                    onSubmitOptionPrediction(
                                        PredictionCategory.WINNING_PERCENT,
                                        "win_percent_poll",
                                        "উইনিং পার্সেন্টেজ ভোট",
                                        "PCT_A_$teamAPctInt",
                                        favoredName,
                                        reward,
                                        activeInnings,
                                        activeBallsCount,
                                        0,
                                        teamAPctInt
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("submit_winning_percent_vote_btn")
                        ) {
                            val btnText = if (existingPct != null) {
                                "উইনিং % ভোট আপডেট করুন ($teamAPctInt% vs $teamBPctInt%)"
                            } else {
                                "উইনিং % ভোট কনফার্ম করুন ($teamAPctInt% vs $teamBPctInt%)"
                            }
                            Text(btnText, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Crowd Poll Live Percentage Bar
            if (crowdStats.totalTeamVotes > 0 || crowdStats.totalPercentPollVotes > 0) {
                CrowdConsensusBar(match = match, crowdStats = crowdStats)
            }

            // User's Recent / Active Predictions
            if (userPredictions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "আপনার সাম্প্রতিক প্রেডিকশন (${userPredictions.size})",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(userPredictions.take(8), key = { it.id }) { pred ->
                            val st = pred.resolvedStatus()
                            val chipBg = when (st) {
                                PredictionStatus.WON -> emerald.copy(alpha = 0.16f)
                                PredictionStatus.LOST -> WicketRed.copy(alpha = 0.13f)
                                PredictionStatus.EXPIRED -> MaterialTheme.colorScheme.surfaceVariant
                                PredictionStatus.PENDING -> gold.copy(alpha = 0.14f)
                            }
                            val chipColor = when (st) {
                                PredictionStatus.WON -> emerald
                                PredictionStatus.LOST -> WicketRed
                                PredictionStatus.EXPIRED -> MaterialTheme.colorScheme.onSurfaceVariant
                                PredictionStatus.PENDING -> gold
                            }
                            val statusIcon = when (st) {
                                PredictionStatus.WON -> "✅ +${pred.awardedPoints}pts"
                                PredictionStatus.LOST -> "❌ মিস"
                                PredictionStatus.EXPIRED -> "⏹️ বাতিল"
                                PredictionStatus.PENDING -> "⏳ অপেক্ষায়"
                            }
                            Surface(
                                color = chipBg,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, chipColor.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text(
                                        text = "${pred.resolvedCategory().badgeEmoji} ${pred.selectedLabel}",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${pred.targetLabel} • $statusIcon",
                                        color = chipColor,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
private fun PredictionCategoryTabButton(
    category: PredictionCategory,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeBorder = Color(0xFF047857)
    val activeTitleColor = Color(0xFF052E16)
    val activeSubtitleColor = Color(0xFF064E3B)

    Surface(
        modifier = modifier
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("prediction_mode_${category.name}"),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) EmeraldGreen else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 1.8.dp else 1.2.dp,
            color = if (selected) activeBorder else MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)
        ),
        shadowElevation = if (selected) 3.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${category.badgeEmoji} ${category.displayTitle}",
                    color = if (selected) activeTitleColor else MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.5.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (selected) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = activeBorder,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = "✓",
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = subtitle,
                color = if (selected) activeSubtitleColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TargetBanner(title: String, lockedLabel: String?) {
    val emerald = adaptiveEmeraldColor()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (!lockedLabel.isNullOrBlank()) {
            Text(
                text = "✓ লকড: $lockedLabel",
                color = emerald,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionGrid(
    options: List<PredictionOptionSpec>,
    selectedOptionKey: String?,
    enabled: Boolean,
    optionCounts: Map<String, Int>,
    countPrefix: String,
    onSelect: (PredictionOptionSpec) -> Unit
) {
    val emerald = adaptiveEmeraldColor()
    val gold = adaptiveGoldColor()
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2
    ) {
        options.forEach { spec ->
            val isSelected = selectedOptionKey == spec.key
            val votes = optionCounts["$countPrefix${spec.key}"] ?: 0
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = enabled) { onSelect(spec) }
                    .testTag("pred_opt_${spec.key}"),
                shape = RoundedCornerShape(10.dp),
                color = when {
                    isSelected -> emerald.copy(alpha = 0.18f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
                border = BorderStroke(
                    width = if (isSelected) 1.6.dp else 1.dp,
                    color = if (isSelected) emerald else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = spec.label,
                            color = if (isSelected) emerald else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "+${spec.rewardPoints}",
                            color = gold,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = spec.subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (votes > 0) {
                            Text(
                                text = "$votes ভোট",
                                color = emerald,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamVoteButton(
    teamName: String,
    shortName: String,
    logoUrl: String?,
    votePercent: Int,
    voteCount: Int,
    isSelected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(if (isSelected) 1.8.dp else 1.dp, if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CricUpAvatar(
                model = logoUrl,
                size = 36.dp,
                fallbackText = shortName.ifBlank { teamName }.take(3),
                fallbackIcon = CricUpIcons.SportsCricket,
                borderColor = accentColor
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = teamName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isSelected) "✓ আপনার ভোট ($votePercent%)" else "$votePercent% • $voteCount ভোট",
                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CrowdConsensusBar(match: Match, crowdStats: CrowdVoteStats) {
    val emerald = adaptiveEmeraldColor()
    val gold = adaptiveGoldColor()
    val leftPct = if (crowdStats.totalTeamVotes > 0) crowdStats.teamAVotePercent else crowdStats.avgTeamAWinPercent
    val rightPct = (100 - leftPct).coerceIn(0, 100)
    val leftFrac = (leftPct / 100f).coerceIn(0.08f, 0.92f)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "দর্শক মতামত: ${match.teamA.shortName.ifBlank { match.teamA.name }} $leftPct%",
                color = emerald,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${match.teamB.shortName.ifBlank { match.teamB.name }} $rightPct%",
                color = gold,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(gold.copy(alpha = 0.75f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(leftFrac)
                    .fillMaxHeight()
                    .background(emerald)
            )
        }
    }
}
