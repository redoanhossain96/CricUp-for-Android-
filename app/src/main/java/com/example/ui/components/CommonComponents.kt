package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BoundaryBlue
import com.example.ui.theme.LiveRed
import com.example.ui.theme.SixerPurple
import com.example.ui.theme.TextWhite
import com.example.ui.theme.WicketRed
import com.example.ui.theme.adaptiveEmeraldColor
import com.example.ui.theme.adaptiveGoldColor

@Composable
fun LivePill(
    modifier: Modifier = Modifier,
    text: String = "LIVE"
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = modifier
            .testTag("live_indicator")
            .clip(RoundedCornerShape(12.dp))
            .background(LiveRed.copy(alpha = 0.2f))
            .border(1.dp, LiveRed.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .graphicsLayer { this.alpha = alpha }
                .background(LiveRed, CircleShape)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            color = LiveRed,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun OfflinePill(
    modifier: Modifier = Modifier,
    text: String = "OFFLINE"
) {
    val goldAccent = adaptiveGoldColor()
    Row(
        modifier = modifier
            .testTag("offline_indicator")
            .clip(RoundedCornerShape(12.dp))
            .background(goldAccent.copy(alpha = 0.16f))
            .border(1.dp, goldAccent.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(goldAccent, CircleShape)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            color = goldAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun BallBadge(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    onClick: (() -> Unit)? = null
) {
    val goldColor = adaptiveGoldColor()
    val emeraldColor = adaptiveEmeraldColor()
    val isWicket = text.equals("W", ignoreCase = true) ||
        (text.contains("W") && !text.contains("Wd", ignoreCase = true) && !text.contains("WD")) ||
        text.contains("OUT", ignoreCase = true) ||
        text.contains("Wkt", ignoreCase = true)

    val displayText = when {
        text.startsWith("W+") || text.contains("W+") -> text
        text.equals("RH", ignoreCase = true) -> "RH"
        isWicket -> "W"
        else -> text
    }

    val (bgColor, textColor, borderColor) = when {
        displayText == "RH" -> Triple(goldColor.copy(alpha = 0.22f), goldColor, goldColor)
        isWicket -> Triple(WicketRed, TextWhite, WicketRed)
        text == "6" -> Triple(SixerPurple, TextWhite, SixerPurple)
        text == "4" -> Triple(BoundaryBlue, TextWhite, BoundaryBlue)
        text.contains("Wd", ignoreCase = true) || text.contains("Nb", ignoreCase = true) ->
            Triple(goldColor.copy(alpha = 0.20f), goldColor, goldColor)
        text == "•" || text == "0" -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.outline
        )
        else -> Triple(emeraldColor.copy(alpha = 0.16f), emeraldColor, emeraldColor)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, borderColor, CircleShape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val calcFontSize = when {
            displayText.length >= 3 -> (size.value * 0.34f).sp
            size > 36.dp -> 16.5.sp
            else -> 13.5.sp
        }
        Text(
            text = displayText,
            color = textColor,
            fontSize = calcFontSize,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun SyncStatusChip(
    isOffline: Boolean,
    pendingCount: Int,
    onManualSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val goldColor = adaptiveGoldColor()
    val emeraldColor = adaptiveEmeraldColor()
    val (color, text) = when {
        isOffline && pendingCount > 0 -> LiveRed to "OFFLINE ($pendingCount pending)"
        pendingCount > 0 -> goldColor to "SYNCING ($pendingCount)"
        else -> emeraldColor to "ONLINE (Synced)"
    }

    Row(
        modifier = modifier
            .testTag("sync_status_chip")
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable { onManualSync() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = color,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
