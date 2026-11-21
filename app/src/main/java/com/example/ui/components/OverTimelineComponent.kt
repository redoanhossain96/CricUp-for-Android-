package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BallEvent
import com.example.data.model.DismissalType
import com.example.data.model.ExtraType

data class BowlerSpellInOver(
    val bowlerName: String,
    val deliveries: List<BallEvent>,
    val legalBalls: Int,
    val runs: Int,
    val wickets: Int
)

data class OverGroup(
    val overNumber: Int, // 1-indexed (Over 1, Over 2, ...)
    val bowlerName: String,
    val bowlerSpells: List<BowlerSpellInOver>,
    val strikerName: String,
    val nonStrikerName: String,
    val deliveries: List<BallEvent>,
    val totalRuns: Int,
    val wickets: Int
) {
    val isSplitOver: Boolean
        get() = bowlerSpells.size > 1
}

fun groupBallEventsByOver(ballEvents: List<BallEvent>): List<OverGroup> {
    val sorted = ballEvents.sortedBy { it.ballSeq }
    if (sorted.isEmpty()) return emptyList()

    val list = mutableListOf<OverGroup>()
    val grouped = sorted.groupBy { it.overNumber }
    for ((ovNum, balls) in grouped) {
        val totalRuns = balls.sumOf { it.totalRunsOnBall }
        val wkts = balls.count { it.isWicket && it.dismissalType != DismissalType.RETIRED_HURT }
        val firstBall = balls.first()
        val lastBall = balls.last()

        val spells = mutableListOf<BowlerSpellInOver>()
        var currentBowler = ""
        var currentSpellBalls = mutableListOf<BallEvent>()

        for (b in balls) {
            val bName = b.bowlerName.ifEmpty { "Bowler" }
            if (currentBowler.isEmpty()) {
                currentBowler = bName
                currentSpellBalls.add(b)
            } else if (currentBowler == bName) {
                currentSpellBalls.add(b)
            } else {
                spells.add(
                    BowlerSpellInOver(
                        bowlerName = currentBowler,
                        deliveries = currentSpellBalls.toList(),
                        legalBalls = currentSpellBalls.count { it.isLegalDelivery && it.dismissalType != DismissalType.RETIRED_HURT && it.dismissalType != DismissalType.RETIRED_OUT },
                        runs = currentSpellBalls.sumOf { it.totalRunsOnBall },
                        wickets = currentSpellBalls.count { it.isWicket && it.dismissalType != DismissalType.RETIRED_HURT }
                    )
                )
                currentBowler = bName
                currentSpellBalls = mutableListOf(b)
            }
        }
        if (currentSpellBalls.isNotEmpty()) {
            spells.add(
                BowlerSpellInOver(
                    bowlerName = currentBowler,
                    deliveries = currentSpellBalls.toList(),
                    legalBalls = currentSpellBalls.count { it.isLegalDelivery && it.dismissalType != DismissalType.RETIRED_HURT && it.dismissalType != DismissalType.RETIRED_OUT },
                    runs = currentSpellBalls.sumOf { it.totalRunsOnBall },
                    wickets = currentSpellBalls.count { it.isWicket && it.dismissalType != DismissalType.RETIRED_HURT }
                )
            )
        }

        list.add(
            OverGroup(
                overNumber = ovNum + 1,
                bowlerName = if (spells.size > 1) spells.joinToString(" + ") { it.bowlerName }
                else lastBall.bowlerName.ifEmpty { firstBall.bowlerName }.ifEmpty { "Bowler" },
                bowlerSpells = spells,
                strikerName = lastBall.strikerName,
                nonStrikerName = lastBall.nonStrikerName,
                deliveries = balls,
                totalRuns = totalRuns,
                wickets = wkts
            )
        )
    }
    return list.sortedByDescending { it.overNumber }
}

/**
 * Standalone scrollable over timeline for dedicated tabs/dialogs with bounded height.
 * NEVER place inside another vertical LazyColumn or verticalScroll container.
 */
@Composable
fun OverTimelineList(
    ballEvents: List<BallEvent>,
    modifier: Modifier = Modifier
) {
    val overs = remember(ballEvents) { groupBallEventsByOver(ballEvents) }

    if (overs.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No overs bowled yet in this innings.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(overs, key = { it.overNumber }) { over ->
                OverCardRow(over = over)
            }
        }
    }
}

/**
 * Non-lazy Column version of over timeline, safe to embed inside any parent LazyColumn
 * or verticalScroll container without triggering infinite-height measurement crashes.
 */
@Composable
fun OverTimelineColumn(
    ballEvents: List<BallEvent>,
    maxOversToShow: Int? = null,
    modifier: Modifier = Modifier
) {
    val overs = remember(ballEvents, maxOversToShow) {
        val grouped = groupBallEventsByOver(ballEvents)
        if (maxOversToShow != null && maxOversToShow > 0) grouped.take(maxOversToShow) else grouped
    }

    if (overs.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No overs bowled yet in this innings.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            overs.forEach { over ->
                OverCardRow(over = over)
            }
        }
    }
}

@Composable
fun OverCardRow(
    over: OverGroup,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("over_row_${over.overNumber}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Over Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (over.isSplitOver) "OVER ${over.overNumber} (উভয় বোলার মিলে ওভার)" else "OVER ${over.overNumber}",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    if (!over.isSplitOver) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = over.bowlerName,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Over Runs & Wickets Summary
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${over.totalRuns} runs",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (over.wickets > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFE53935))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${over.wickets} WKT",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (over.isSplitOver) {
                // Separate spell per bowler in this over
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    over.bowlerSpells.forEach { spell ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🎯 ${spell.bowlerName}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${spell.legalBalls} বল • ${spell.runs} রান${if (spell.wickets > 0) " • ${spell.wickets} W" else ""}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                spell.deliveries.forEach { ball ->
                                    TimelineBallBadge(ball = ball)
                                }
                            }
                        }
                    }
                }
            } else {
                // Chronological Ball Delivery Indicators for single bowler
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    over.deliveries.forEach { ball ->
                        TimelineBallBadge(ball = ball)
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineBallBadge(
    ball: BallEvent,
    modifier: Modifier = Modifier
) {
    // Delivery styling:
    // Wicket: Red highlight
    // 6: Green highlight
    // 4: Orange highlight
    // Extras: Light badge with small subtext
    // Dots: Neutral circle with dot
    val (bgColor, textColor, borderColor) = when {
        ball.isWicket -> Triple(Color(0xFFD32F2F), Color.White, Color(0xFFFF5252))
        ball.runsOffBat == 6 -> Triple(Color(0xFF2E7D32), Color.White, Color(0xFF66BB6A))
        ball.runsOffBat == 4 -> Triple(Color(0xFFE65100), Color.White, Color(0xFFFF9800))
        ball.extraType == ExtraType.WIDE || ball.extraType == ExtraType.NO_BALL -> Triple(
            Color(0xFF1565C0),
            Color.White,
            Color(0xFF42A5F5)
        )
        ball.extraType == ExtraType.BYE || ball.extraType == ExtraType.LEG_BYE -> Triple(
            Color(0xFF5E35B1),
            Color.White,
            Color(0xFF7E57C2)
        )
        ball.runsOffBat == 0 -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    }

    val label = when {
        ball.dismissalType?.name == "RETIRED_HURT" -> "RH"
        ball.dismissalType?.name == "RETIRED_OUT" -> "RO"
        ball.isWicket -> if (ball.totalRunsOnBall > 0) "W+${ball.totalRunsOnBall}" else "W"
        ball.extraType == ExtraType.WIDE -> if (ball.extraRuns > 1) "${ball.extraRuns}WD" else "WD"
        ball.extraType == ExtraType.NO_BALL -> if (ball.runsOffBat > 0) "${ball.runsOffBat + ball.extraRuns}NB" else "NB"
        ball.extraType == ExtraType.BYE -> "${ball.extraRuns}B"
        ball.extraType == ExtraType.LEG_BYE -> "${ball.extraRuns}LB"
        ball.runsOffBat == 0 -> "0"
        else -> "${ball.runsOffBat}"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(bgColor)
                .border(1.5.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = if (label.length > 2) 10.sp else 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        if (ball.dismissalType == DismissalType.RETIRED_HURT || ball.dismissalType == DismissalType.RETIRED_OUT) {
            Text(
                text = "RET",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        } else if (!ball.isLegalDelivery) {
            Text(
                text = "EXTRA",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
