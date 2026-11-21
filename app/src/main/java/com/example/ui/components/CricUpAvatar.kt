package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.CardBorder
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.SlateCardBg
import com.example.ui.theme.TextWhite
import com.example.util.ImageModelResolver

@Composable
fun CricUpAvatar(
    model: Any?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = CircleShape,
    fallbackText: String? = null,
    fallbackIcon: ImageVector = Icons.Default.Person,
    borderColor: Color = CardBorder,
    borderWidth: Dp = 1.5.dp,
    contentDescription: String? = null
) {
    val resolvedModel = remember(model) { ImageModelResolver.resolveForCoil(model) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageRequest = remember(resolvedModel) {
        resolvedModel?.let {
            coil.request.ImageRequest.Builder(context)
                .data(it)
                .crossfade(false)
                .build()
        }
    }

    val emeraldTint = com.example.ui.theme.adaptiveEmeraldColor()
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
            .border(borderWidth, borderColor, shape),
        contentAlignment = Alignment.Center
    ) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
                contentScale = ContentScale.Crop
            )
        } else if (!fallbackText.isNullOrBlank()) {
            Text(
                text = fallbackText.take(3).uppercase(),
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                fontSize = (size.value * 0.36f).sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDescription,
                tint = emeraldTint,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}
