package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Match
import com.example.data.model.MatchStatus
import com.example.ui.theme.CardBorder
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldLight
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.GoldLight
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.SlateCardBg
import com.example.ui.theme.StadiumCyanLight
import com.example.ui.theme.SurfaceDarkNavy
import com.example.ui.theme.TextLightGrey
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

@Composable
fun MatchCard(
    match: Match,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null
) {
    val isLive = match.status == MatchStatus.LIVE || match.status == MatchStatus.INNINGS_BREAK
    val isOfflineMatch = match.isOfflineOnlyMatch()
    val isDark = com.example.ui.theme.LocalThemeIsDark.current.value
    val emeraldAccent = com.example.ui.theme.adaptiveEmeraldColor()
    val goldAccent = com.example.ui.theme.adaptiveGoldColor()
    val primaryText = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val mutedText = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant

    val outlineColor = androidx.compose.material3.MaterialTheme.colorScheme.outline
    val cardBg = remember(isDark, isLive) {
        if (isDark) {
            if (isLive) {
                Brush.verticalGradient(colors = listOf(Color(0xFF0E2B1E), Color(0xFF071911)))
            } else {
                Brush.verticalGradient(colors = listOf(SurfaceDarkNavy, Color(0xFF0A2418)))
            }
        } else {
            if (isLive) {
                Brush.verticalGradient(colors = listOf(Color(0xFFFFFFFF), Color(0xFFF0FAF4)))
            } else {
                Brush.verticalGradient(colors = listOf(Color(0xFFFFFFFF), Color(0xFFF6FBF8)))
            }
        }
    }

    val cardBorderBrush = remember(isLive, emeraldAccent, goldAccent, outlineColor) {
        if (isLive) {
            Brush.horizontalGradient(listOf(emeraldAccent, StadiumCyanLight, goldAccent))
        } else {
            Brush.linearGradient(listOf(outlineColor, outlineColor))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("match_card_${match.id}")
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(
                if (isLive) 1.5.dp else 1.dp,
                cardBorderBrush,
                RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column {
            // Header: Category, Title & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${match.category} • ${match.format} (${match.totalOvers} Ov)",
                        color = goldAccent,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = match.title,
                        color = primaryText,
                        fontSize = 17.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    if (!isOfflineMatch && match.matchCode.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Surface(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, goldAccent.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = "CODE: ${match.matchCode}",
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLive) {
                        if (isOfflineMatch) {
                            OfflinePill()
                        } else {
                            LivePill()
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
                                .border(
                                    1.dp,
                                    androidx.compose.material3.MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = match.status.name,
                                color = mutedText,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (onDelete != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        androidx.compose.material3.IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(28.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Delete,
                                contentDescription = "Delete Match",
                                tint = Color(0xFFFF5252).copy(alpha = 0.85f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Scoreboard Teams Row
            val activeInnings = match.activeInnings
            val firstInnings = match.firstInnings
            val secondInnings = match.secondInnings

            // Team A
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreen.copy(alpha = 0.25f))
                            .border(1.5.dp, EmeraldGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!match.teamA.logoUrl.isNullOrEmpty()) {
                            val resolvedLogoA = remember(match.teamA.logoUrl) {
                                com.example.util.ImageModelResolver.resolveForCoil(match.teamA.logoUrl)
                            }
                            AsyncImage(
                                model = resolvedLogoA,
                                contentDescription = match.teamA.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = match.teamA.shortName.take(3),
                                color = emeraldAccent,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = match.teamA.name,
                        color = primaryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val scoreA = when {
                    match.battingFirstTeamId == match.teamA.id -> firstInnings
                    secondInnings?.battingTeamId == match.teamA.id -> secondInnings
                    else -> null
                }
                if (scoreA != null) {
                    Text(
                        text = "${scoreA.totalRuns}/${scoreA.wickets} (${scoreA.oversFormatted})",
                        color = primaryText,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                } else {
                    Text(
                        text = "Yet to bat",
                        color = mutedText,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Team B
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(goldAccent.copy(alpha = 0.2f))
                            .border(1.5.dp, goldAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!match.teamB.logoUrl.isNullOrEmpty()) {
                            val resolvedLogoB = remember(match.teamB.logoUrl) {
                                com.example.util.ImageModelResolver.resolveForCoil(match.teamB.logoUrl)
                            }
                            AsyncImage(
                                model = resolvedLogoB,
                                contentDescription = match.teamB.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = match.teamB.shortName.take(3),
                                color = goldAccent,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = match.teamB.name,
                        color = primaryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                val scoreB = when {
                    match.battingFirstTeamId == match.teamB.id -> firstInnings
                    secondInnings?.battingTeamId == match.teamB.id -> secondInnings
                    else -> null
                }
                if (scoreB != null) {
                    Text(
                        text = "${scoreB.totalRuns}/${scoreB.wickets} (${scoreB.oversFormatted})",
                        color = primaryText,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                } else {
                    Text(
                        text = "Yet to bat",
                        color = mutedText,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Target / Result Banner
            if (match.resultDescription.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer)
                        .border(1.dp, emeraldAccent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "🏆 ${match.resultDescription}",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (match.targetRuns != null && isLive) {
                val currentRuns = activeInnings?.totalRuns ?: 0
                val runsNeeded = match.targetRuns - currentRuns
                val ballsLeft = (match.totalOvers * 6) - (activeInnings?.legalBalls ?: 0)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer)
                        .border(1.dp, goldAccent.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Target: ${match.targetRuns} | Need $runsNeeded off $ballsLeft balls",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Recent Balls Ribbon
            if (isLive && match.recentBallsSummary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Recent: ",
                        color = mutedText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        match.recentBallsSummary.takeLast(8).forEach { ballText ->
                            BallBadge(text = ballText, size = 22.dp)
                        }
                    }
                }
            }
        }
    }
}
