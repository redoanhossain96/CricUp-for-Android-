package com.example.ui.screens.home

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import com.example.ui.theme.CricUpIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.CommunityPost
import com.example.data.model.CommunityPostFeelings
import com.example.data.model.PostMediaType
import com.example.di.AppContainer
import com.example.ui.components.CricUpAvatar
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.LightGoldBorder
import com.example.ui.theme.LightGoldenGreenCardGradient
import com.example.ui.theme.LocalThemeIsDark
import com.example.ui.theme.WicketRed
import com.example.ui.theme.adaptiveEmeraldColor
import com.example.ui.theme.adaptiveGoldColor
import com.example.util.ImageModelResolver
import com.example.util.MediaHelper
import com.example.util.PreparedPostVideo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

@Composable
fun GoldenGreenThemeTogglePill(modifier: Modifier = Modifier) {
    val isDarkState = LocalThemeIsDark.current
    val isDark = isDarkState.value
    val goldColor = adaptiveGoldColor()
    val emeraldColor = adaptiveEmeraldColor()

    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(24.dp))
            .clickable { isDarkState.value = !isDark }
            .testTag("home_theme_toggle_btn"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.2.dp,
            Brush.horizontalGradient(listOf(emeraldColor, goldColor))
        ),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isDark) CricUpIcons.LightMode else CricUpIcons.DarkMode,
                contentDescription = if (isDark) "Switch to Golden-Green Light Mode" else "Switch to Dark Mode",
                tint = goldColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (isDark) "লাইট" else "ডার্ক",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CommunityPostComposerCard(
    container: AppContainer,
    currentUserId: String,
    memberName: String,
    memberAvatarUrl: String?,
    onMemberProfileUpdated: (name: String, avatarUrl: String?) -> Unit,
    onPostPublished: () -> Unit = {},
    isGuest: Boolean = false,
    onRequireAuth: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = LocalThemeIsDark.current.value
    val goldColor = adaptiveGoldColor()
    val emeraldColor = adaptiveEmeraldColor()
    var isSigningInGoogle by remember { mutableStateOf(false) }
    var googleAuthError by remember { mutableStateOf<String?>(null) }

    if (isGuest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_community_composer_locked_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.2.dp, goldColor.copy(alpha = 0.6f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(goldColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔒", fontSize = 18.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ফিডে পোস্ট করতে Gmail লগইন প্রয়োজন",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "রেজিস্ট্রেশন ছাড়া শুধু ফিড পড়া এবং ম্যাচের স্কোর ও কোড সার্চ করে দেখা যাবে।",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
                com.example.ui.components.GoogleSignInActionButton(
                    text = "Continue with Google (Gmail লগইন)",
                    isLoading = isSigningInGoogle,
                    onClick = {
                        googleAuthError = null
                        isSigningInGoogle = true
                        com.example.util.GoogleSignInHelper.signInWithGoogle(
                            context = context,
                            scope = scope,
                            authRepository = container.authRepository,
                            onSuccess = { linkedUser ->
                                isSigningInGoogle = false
                                googleAuthError = null
                                val label = linkedUser.email.takeIf { it.isNotBlank() } ?: linkedUser.displayName
                                Toast.makeText(
                                    context,
                                    "Gmail ($label) লগইন সফল! এখন ফিডে পোস্ট করতে পারবেন।",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            onError = { err ->
                                isSigningInGoogle = false
                                googleAuthError = err
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            },
                            onCancelled = {
                                isSigningInGoogle = false
                            }
                        )
                    },
                    testTag = "feed_locked_google_sign_in_btn"
                )
                com.example.ui.components.GoogleBrowserSignInButton(
                    text = "ব্রাউজার দিয়ে Gmail লগইন (বিকল্প পদ্ধতি)",
                    isLoading = isSigningInGoogle,
                    onClick = {
                        googleAuthError = null
                        isSigningInGoogle = true
                        com.example.util.GoogleSignInHelper.signInWithGoogleViaBrowser(
                            context = context,
                            scope = scope,
                            authRepository = container.authRepository,
                            onSuccess = { linkedUser ->
                                isSigningInGoogle = false
                                googleAuthError = null
                                val label = linkedUser.email.takeIf { it.isNotBlank() } ?: linkedUser.displayName
                                Toast.makeText(
                                    context,
                                    "Gmail ($label) লগইন সফল! এখন ফিডে পোস্ট করতে পারবেন।",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            onError = { err ->
                                isSigningInGoogle = false
                                googleAuthError = err
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    testTag = "feed_locked_google_browser_btn"
                )
                googleAuthError?.let { errMsg ->
                    Text(
                        text = errMsg,
                        color = WicketRed,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        return
    }

    var postText by rememberSaveable { mutableStateOf("") }
    var selectedFeeling by rememberSaveable { mutableStateOf("") }
    var selectedImageBase64 by remember { mutableStateOf<String?>(null) }
    var preparedVideo by remember { mutableStateOf<PreparedPostVideo?>(null) }
    var pendingVideoPostId by remember { mutableStateOf<String?>(null) }
    var isProcessingMedia by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingMedia = true
            scope.launch {
                val compressed = MediaHelper.compressPostImageToBase64(context, uri)
                isProcessingMedia = false
                if (compressed != null) {
                    preparedVideo = null
                    pendingVideoPostId = null
                    selectedImageBase64 = compressed
                } else {
                    Toast.makeText(context, "ছবি প্রসেস করা যায়নি, অন্য ছবি চেষ্টা করুন", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingMedia = true
            val draftPostId = "post_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
            scope.launch {
                val res = MediaHelper.preparePostVideo(context, uri, draftPostId)
                isProcessingMedia = false
                res.onSuccess { video ->
                    selectedImageBase64 = null
                    pendingVideoPostId = draftPostId
                    preparedVideo = video
                    if (video.exceedsCloudClipLimit) {
                        Toast.makeText(
                            context,
                            "ভিডিওটি বড় হওয়ায় থাম্বনেইল ক্লাউডে ও সম্পূর্ণ ভিডিও ডিভাইসে সংরক্ষিত হবে",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }.onFailure { err ->
                    Toast.makeText(context, err.message ?: "ভিডিও নেওয়া যায়নি", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val canSubmit = !isProcessingMedia && !isPublishing &&
        (postText.isNotBlank() || selectedImageBase64 != null || preparedVideo != null)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_community_composer_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.4.dp,
            Brush.horizontalGradient(
                listOf(
                    emeraldColor.copy(alpha = 0.75f),
                    if (isDark) GoldYellow.copy(alpha = 0.7f) else LightGoldBorder.copy(alpha = 0.85f)
                )
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 2.dp else 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (!isDark) Modifier.background(LightGoldenGreenCardGradient) else Modifier
                )
                .padding(14.dp)
        ) {
            // Author & Name Editor Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showEditProfileDialog = true }
                        .padding(vertical = 2.dp)
                        .testTag("home_edit_profile_row")
                ) {
                    CricUpAvatar(
                        model = memberAvatarUrl,
                        size = 42.dp,
                        fallbackText = memberName.take(2),
                        borderColor = emeraldColor
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = memberName,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit profile name and photo",
                                tint = goldColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = "নাম ও ছবি বদলাতে ট্যাপ করুন • মুহূর্ত ও ভাবনা লিখুন",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Feeling Selector Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Feeling:",
                    color = goldColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.width(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(CommunityPostFeelings.all, key = { it.key }) { item ->
                        val isSelected = selectedFeeling.equals(item.key, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedFeeling = if (isSelected) "" else item.key
                            },
                            label = {
                                Text(
                                    text = "${item.emoji} ${item.key}",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                selectedBorderColor = goldColor
                            ),
                            modifier = Modifier.testTag("feeling_chip_${item.key.lowercase()}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Text Input
            OutlinedTextField(
                value = postText,
                onValueChange = { postText = it },
                placeholder = {
                    Text(
                        text = "আপনার মুহূর্ত, চিন্তা বা ভাবনা লিখুন...",
                        fontSize = 13.5.sp
                    )
                },
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = emeraldColor,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_post_input")
            )

            if (isProcessingMedia) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = emeraldColor
                    )
                    Text(
                        text = "মিডিয়া প্রস্তুত হচ্ছে...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            // Selected Image Preview (natural posted proportions, uncropped)
            selectedImageBase64?.let { imgData ->
                Spacer(modifier = Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    NaturalFeedPostImage(
                        imageSource = imgData,
                        contentDescription = "Selected photo preview",
                        borderColor = MaterialTheme.colorScheme.outline,
                        maxFallbackHeight = 460.dp
                    )
                    IconButton(
                        onClick = { selectedImageBase64 = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove photo",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Selected Video Preview (proportional to video frame)
            preparedVideo?.let { vid ->
                Spacer(modifier = Modifier.height(10.dp))
                val thumbModel = remember(vid.thumbnailBase64) {
                    ImageModelResolver.resolveForCoil(vid.thumbnailBase64)
                }
                val vidPreviewAspect by produceState<Float?>(
                    initialValue = ImageModelResolver.getCachedAspectRatio(vid.thumbnailBase64),
                    vid.thumbnailBase64
                ) {
                    value = ImageModelResolver.resolveAspectRatio(vid.thumbnailBase64)
                }
                val safePreviewAspect = (vidPreviewAspect ?: (16f / 9f)).coerceIn(0.6f, 2.2f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(safePreviewAspect)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0A2418))
                        .border(1.dp, goldColor.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                ) {
                    if (thumbModel != null) {
                        AsyncImage(
                            model = thumbModel,
                            contentDescription = "Video thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = GoldYellow,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "ভিডিও (${MediaHelper.formatDuration(vid.durationMs)})",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            try {
                                File(vid.localFilePath).delete()
                            } catch (_: Exception) {
                            }
                            preparedVideo = null
                            pendingVideoPostId = null
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.65f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove video",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row (Photo, Video, Publish)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isProcessingMedia && !isPublishing,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("home_pick_image_btn")
                ) {
                    Icon(
                        imageVector = CricUpIcons.Image,
                        contentDescription = "Pick photo",
                        tint = emeraldColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ছবি",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    enabled = !isProcessingMedia && !isPublishing,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, goldColor.copy(alpha = 0.7f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("home_pick_video_btn")
                ) {
                    Icon(
                        imageVector = CricUpIcons.Videocam,
                        contentDescription = "Pick video",
                        tint = goldColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ভিডিও",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        isPublishing = true
                        scope.launch {
                            val currentVid = preparedVideo
                            val currentImg = selectedImageBase64
                            val mediaType = when {
                                currentVid != null -> PostMediaType.VIDEO
                                currentImg != null -> PostMediaType.IMAGE
                                else -> PostMediaType.NONE
                            }
                            val result = container.postRepository.createPost(
                                postId = pendingVideoPostId
                                    ?: "post_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
                                authorId = currentUserId,
                                authorName = memberName,
                                authorAvatarUrl = memberAvatarUrl,
                                categoryTag = "",
                                feeling = selectedFeeling,
                                contentText = postText,
                                mediaType = mediaType,
                                imageUrl = currentImg,
                                videoThumbnailUrl = currentVid?.thumbnailBase64,
                                videoBase64 = currentVid?.inlineVideoBase64,
                                videoChunks = currentVid?.chunkedBase64Parts.orEmpty(),
                                localVideoPath = currentVid?.localFilePath,
                                videoDurationMs = currentVid?.durationMs ?: 0L
                            )
                            isPublishing = false
                            result.onSuccess {
                                postText = ""
                                selectedFeeling = ""
                                selectedImageBase64 = null
                                preparedVideo = null
                                pendingVideoPostId = null
                                onPostPublished()
                                Toast.makeText(context, "আপনার পোস্ট শেয়ার করা হয়েছে!", Toast.LENGTH_SHORT).show()
                            }.onFailure { err ->
                                Toast.makeText(context, err.message ?: "পোস্ট করা যায়নি", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = canSubmit,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("home_submit_post_btn")
                ) {
                    if (isPublishing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("পোস্ট করুন", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }

    if (showEditProfileDialog) {
        EditMemberProfileDialog(
            initialName = memberName,
            initialPhotoUrl = memberAvatarUrl,
            onDismiss = { showEditProfileDialog = false },
            onSave = { newName, newPhotoUrl, cleared ->
                scope.launch {
                    container.postRepository.saveMemberProfile(
                        name = newName,
                        photoUrl = newPhotoUrl,
                        userId = currentUserId,
                        clearPhoto = cleared
                    )
                    container.authRepository.updateUserProfile(
                        uid = currentUserId,
                        displayName = newName,
                        photoUrl = newPhotoUrl,
                        clearPhoto = cleared
                    )
                    onMemberProfileUpdated(newName, if (cleared) null else newPhotoUrl)
                    showEditProfileDialog = false
                    Toast.makeText(context, "প্রোফাইল আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun EditMemberProfileDialog(
    initialName: String,
    initialPhotoUrl: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, photoUrl: String?, clearPhoto: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val emeraldColor = adaptiveEmeraldColor()
    val goldColor = adaptiveGoldColor()

    var nameDraft by remember { mutableStateOf(initialName) }
    var photoDraft by remember { mutableStateOf(initialPhotoUrl) }
    var photoCleared by remember { mutableStateOf(false) }
    var hasEditedName by remember { mutableStateOf(false) }
    var hasEditedPhoto by remember { mutableStateOf(false) }
    var isProcessingPhoto by remember { mutableStateOf(false) }

    // Safe one-way upgrade if real cloud profile finishes arriving while dialog is open,
    // without ever overwriting user edits or reverting a real name back to a placeholder.
    LaunchedEffect(initialName, initialPhotoUrl) {
        if (!hasEditedName &&
            com.example.data.repository.AuthRepository.isPlaceholderProfileName(nameDraft) &&
            !com.example.data.repository.AuthRepository.isPlaceholderProfileName(initialName)
        ) {
            nameDraft = initialName
        }
        if (!hasEditedPhoto && !photoCleared && photoDraft.isNullOrBlank() && !initialPhotoUrl.isNullOrBlank()) {
            photoDraft = initialPhotoUrl
        }
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingPhoto = true
            scope.launch {
                val compressed = MediaHelper.compressAvatarToBase64(context, uri)
                isProcessingPhoto = false
                if (compressed != null) {
                    photoDraft = compressed
                    photoCleared = false
                    hasEditedPhoto = true
                } else {
                    Toast.makeText(context, "ছবি প্রসেস করা যায়নি, অন্য ছবি চেষ্টা করুন", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "প্রোফাইল এডিট (নাম ও ছবি)",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    CricUpAvatar(
                        model = photoDraft,
                        size = 84.dp,
                        fallbackText = nameDraft.ifBlank { "U" }.take(2),
                        borderColor = emeraldColor,
                        borderWidth = 2.dp
                    )
                    if (isProcessingPhoto) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(28.dp),
                            color = goldColor,
                            strokeWidth = 2.5.dp
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            avatarPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isProcessingPhoto,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, emeraldColor.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("profile_pick_photo_btn")
                    ) {
                        Icon(
                            imageVector = CricUpIcons.Image,
                            contentDescription = "Choose photo",
                            tint = emeraldColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (photoDraft.isNullOrBlank()) "ছবি যোগ করুন" else "ছবি পরিবর্তন",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!photoDraft.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                photoDraft = null
                                photoCleared = true
                                hasEditedPhoto = true
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("মুছুন", color = WicketRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = {
                        nameDraft = it
                        hasEditedName = true
                    },
                    label = { Text("ব্যবহারকারীর নাম (User Name)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = nameDraft.trim()
                    if (trimmed.isNotBlank()) {
                        onSave(trimmed, photoDraft, photoCleared)
                    }
                },
                enabled = nameDraft.isNotBlank() && !isProcessingPhoto,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.height(48.dp).testTag("profile_save_btn")
            ) {
                Text("সংরক্ষণ করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("বাতিল")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun CommunityPostCard(
    post: CommunityPost,
    currentUserId: String,
    currentUserName: String,
    currentUserAvatarUrl: String? = null,
    container: AppContainer,
    onImageClick: (String) -> Unit,
    onVideoClick: (CommunityPost) -> Unit,
    onAuthorClick: ((authorId: String) -> Unit)? = null,
    isGuest: Boolean = false,
    onRequireAuth: (String) -> Unit = {},
    isVideoAutoplayActive: Boolean = false,
    isVideoMuted: Boolean = true,
    onToggleVideoMute: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val goldColor = adaptiveGoldColor()
    val emeraldColor = adaptiveEmeraldColor()
    val isLiked = remember(post.likedByIds, currentUserId) { post.likedByIds.contains(currentUserId) }
    val isOwner = !isGuest && post.authorId == currentUserId
    val resolvedAuthorName = remember(isOwner, post.authorName, currentUserName) {
        if (isOwner && !com.example.data.repository.AuthRepository.isPlaceholderProfileName(currentUserName)) {
            currentUserName
        } else {
            post.authorName.ifBlank { "Member" }
        }
    }
    val resolvedAuthorAvatar = remember(isOwner, post.authorAvatarUrl, currentUserAvatarUrl) {
        if (isOwner) {
            currentUserAvatarUrl?.takeIf { it.isNotBlank() }
                ?: post.authorAvatarUrl?.takeIf { it.isNotBlank() }
        } else {
            post.authorAvatarUrl?.takeIf { it.isNotBlank() }
        }
    }

    var showCommentsDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("community_post_card_${post.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Post Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = onAuthorClick != null && post.authorId.isNotBlank()) {
                            onAuthorClick?.invoke(post.authorId)
                        }
                        .padding(vertical = 2.dp)
                        .testTag("post_author_header_${post.id}")
                ) {
                    CricUpAvatar(
                        model = resolvedAuthorAvatar,
                        size = 42.dp,
                        fallbackText = resolvedAuthorName.take(2),
                        borderColor = emeraldColor
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = resolvedAuthorName,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = formatRelativePostTime(post.createdAt),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                            if (post.feeling.isNotBlank()) {
                                Surface(
                                    color = goldColor.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "feeling ${CommunityPostFeelings.formatLabel(post.feeling)}",
                                        color = goldColor,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (isOwner) {
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_post_${post.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete post",
                            tint = WicketRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            if (post.contentText.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = post.contentText,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp
                )
            }

            // Image Media (preserves natural posted size/aspect ratio without cropping)
            if (post.resolvedMediaType() == PostMediaType.IMAGE && !post.imageUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                NaturalFeedPostImage(
                    imageSource = post.imageUrl,
                    contentDescription = "Post photo",
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    onClick = { onImageClick(post.imageUrl) }
                )
            }

            // Video Media (smooth inline autoplay when visible in feed)
            if (post.resolvedMediaType() == PostMediaType.VIDEO) {
                Spacer(modifier = Modifier.height(10.dp))
                AutoplayFeedVideoPlayer(
                    post = post,
                    container = container,
                    isAutoplayActive = isVideoAutoplayActive,
                    isMuted = isVideoMuted,
                    onToggleMute = onToggleVideoMute,
                    onOpenFullScreen = { onVideoClick(post) },
                    goldColor = goldColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Spacer(modifier = Modifier.height(4.dp))

            // Footer: Like & Comment
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            if (isGuest) {
                                onRequireAuth("পোস্টে লাইক দিতে")
                            } else {
                                scope.launch { container.postRepository.toggleLike(post.id, currentUserId) }
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("like_post_${post.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like post",
                        tint = if (isLiked) WicketRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (post.likedByIds.isEmpty()) "পছন্দ" else "${post.likedByIds.size} পছন্দ",
                        color = if (isLiked) WicketRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        fontWeight = if (isLiked) FontWeight.Bold else FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showCommentsDialog = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("comment_post_${post.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = CricUpIcons.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = emeraldColor,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (post.comments.isEmpty()) "মন্তব্য" else "${post.comments.size} মন্তব্য",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("পোস্ট মুছে ফেলবেন?", fontWeight = FontWeight.Bold) },
            text = { Text("এই মুহূর্ত বা পোস্টটি স্থায়ীভাবে মুছে ফেলা হবে।") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            container.postRepository.deletePost(post.id)
                            showDeleteConfirm = false
                            Toast.makeText(context, "পোস্ট মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WicketRed)
                ) {
                    Text("মুছে ফেলুন", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("বাতিল") }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showCommentsDialog) {
        var commentInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCommentsDialog = false },
            title = { Text("মন্তব্যসমূহ (${post.comments.size})", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (post.comments.isEmpty()) {
                        Text(
                            text = "এখনো কোনো মন্তব্য নেই। প্রথম মন্তব্যটি লিখুন!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.5.sp
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(post.comments, key = { it.id }) { cmt ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = cmt.authorName,
                                                color = emeraldColor,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = formatRelativePostTime(cmt.createdAt),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = cmt.text,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (!isGuest) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = commentInput,
                            onValueChange = { commentInput = it },
                            placeholder = { Text("মন্তব্য লিখুন...", fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "মন্তব্য লিখতে Gmail লগইন প্রয়োজন",
                            color = goldColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                if (isGuest) {
                    Button(
                        onClick = {
                            showCommentsDialog = false
                            onRequireAuth("পোস্টে মন্তব্য করতে")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) {
                        Text("Gmail লগইন", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            val txt = commentInput.trim()
                            if (txt.isNotBlank()) {
                                scope.launch {
                                    container.postRepository.addComment(
                                        postId = post.id,
                                        authorId = currentUserId,
                                        authorName = currentUserName,
                                        commentText = txt
                                    )
                                    commentInput = ""
                                }
                            }
                        },
                        enabled = commentInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("পাঠান")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommentsDialog = false }) { Text("বন্ধ করুন") }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun FullScreenPostImageDialog(
    imageSource: String,
    onDismiss: () -> Unit
) {
    val resolved = remember(imageSource) { ImageModelResolver.resolveForCoil(imageSource) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = resolved,
                contentDescription = "Full screen image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

@Composable
fun PostVideoPlayerDialog(
    post: CommunityPost,
    container: AppContainer,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var videoFile by remember(post.id) { mutableStateOf<File?>(null) }
    var isLoading by remember(post.id) { mutableStateOf(true) }
    var loadError by remember(post.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(post.id) {
        isLoading = true
        loadError = null
        val file = MediaHelper.materializeVideoFile(
            context = context,
            postId = post.id,
            localPath = post.localVideoPath,
            inlineVideoBase64 = post.videoBase64,
            chunkFetcher = { id -> container.postRepository.fetchVideoChunks(id) }
        )
        videoFile = file
        isLoading = false
        if (file == null) {
            loadError = "এই ভিডিও ক্লিপটি অনলাইনে উপলব্ধ নয় বা সাইজ সীমার বাইরে রয়েছে।"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = GoldYellow)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("ভিডিও লোড হচ্ছে...", color = Color.White, fontSize = 13.sp)
                    }
                }
                loadError != null -> {
                    Text(
                        text = loadError.orEmpty(),
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                videoFile != null -> {
                    val readyFile = videoFile
                    if (readyFile != null) {
                        var activeVideoView by remember { mutableStateOf<VideoView?>(null) }
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    val controller = MediaController(ctx)
                                    controller.setAnchorView(this)
                                    setMediaController(controller)
                                    setOnErrorListener { _, _, _ ->
                                        loadError = "দুঃখিত, এই ভিডিও ফরম্যাটটি প্লে করা যায়নি।"
                                        true
                                    }
                                    setVideoPath(readyFile.absolutePath)
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = false
                                        start()
                                    }
                                    activeVideoView = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        DisposableEffect(readyFile.absolutePath) {
                            onDispose {
                                try {
                                    activeVideoView?.stopPlayback()
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.68f))
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close video", tint = Color.White)
            }
        }
    }
}

@Composable
fun NaturalFeedPostImage(
    imageSource: String,
    contentDescription: String,
    borderColor: Color,
    modifier: Modifier = Modifier,
    maxFallbackHeight: Dp = 620.dp,
    onClick: (() -> Unit)? = null
) {
    val resolvedModel = remember(imageSource) { ImageModelResolver.resolveForCoil(imageSource) }
    val precomputedRatio by produceState<Float?>(
        initialValue = ImageModelResolver.getCachedAspectRatio(imageSource),
        imageSource
    ) {
        value = ImageModelResolver.resolveAspectRatio(imageSource)
    }
    var painterRatio by remember(imageSource) { mutableStateOf<Float?>(null) }
    val effectiveRatio = precomputedRatio ?: painterRatio

    val sizeModifier = when {
        effectiveRatio != null && effectiveRatio >= 0.45f -> Modifier
            .fillMaxWidth()
            .aspectRatio(effectiveRatio)
        effectiveRatio != null -> Modifier
            .fillMaxWidth()
            .heightIn(max = maxFallbackHeight)
        else -> Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp, max = maxFallbackHeight)
    }

    AsyncImage(
        model = resolvedModel,
        contentDescription = contentDescription,
        onSuccess = { state ->
            val sz = state.painter.intrinsicSize
            if (sz.width > 0f && sz.height > 0f && sz.width.isFinite() && sz.height.isFinite()) {
                val ratio = sz.width / sz.height
                painterRatio = ratio
                ImageModelResolver.cacheAspectRatio(imageSource, ratio)
            }
        },
        modifier = modifier
            .then(sizeModifier)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .let { base ->
                if (onClick != null) base.clickable(onClick = onClick) else base
            },
        contentScale = ContentScale.Fit
    )
}

@Composable
fun AutoplayFeedVideoPlayer(
    post: CommunityPost,
    container: AppContainer,
    isAutoplayActive: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onOpenFullScreen: () -> Unit,
    goldColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val thumbResolved = remember(post.videoThumbnailUrl) {
        ImageModelResolver.resolveForCoil(post.videoThumbnailUrl)
    }
    val thumbAspect by produceState<Float?>(
        initialValue = ImageModelResolver.getCachedAspectRatio(post.videoThumbnailUrl),
        post.videoThumbnailUrl
    ) {
        value = ImageModelResolver.resolveAspectRatio(post.videoThumbnailUrl)
    }
    var videoStreamAspect by remember(post.id) { mutableStateOf<Float?>(null) }
    val displayAspect = ((videoStreamAspect ?: thumbAspect) ?: (16f / 9f)).coerceIn(0.56f, 2.2f)

    var materializedFile by remember(post.id) { mutableStateOf<File?>(null) }
    var isMaterializing by remember(post.id) { mutableStateOf(false) }
    var hasPlaybackError by remember(post.id) { mutableStateOf(false) }
    var isRenderingVideo by remember(post.id) { mutableStateOf(false) }

    var mediaPlayerRef by remember(post.id) { mutableStateOf<MediaPlayer?>(null) }
    var activeSurfaceRef by remember(post.id) { mutableStateOf<Surface?>(null) }
    val latestIsAutoplay by rememberUpdatedState(isAutoplayActive)
    val latestIsMuted by rememberUpdatedState(isMuted)

    LaunchedEffect(post.id, isAutoplayActive) {
        if (!isAutoplayActive || materializedFile != null || hasPlaybackError) return@LaunchedEffect
        delay(120)
        isMaterializing = true
        val file = MediaHelper.materializeVideoFile(
            context = context,
            postId = post.id,
            localPath = post.localVideoPath,
            inlineVideoBase64 = post.videoBase64,
            chunkFetcher = { id -> container.postRepository.fetchVideoChunks(id) }
        )
        materializedFile = file
        isMaterializing = false
        if (file == null) {
            hasPlaybackError = true
        }
    }

    LaunchedEffect(isAutoplayActive, isMuted, mediaPlayerRef) {
        val mp = mediaPlayerRef ?: return@LaunchedEffect
        val vol = if (isMuted) 0f else 1f
        try {
            mp.setVolume(vol, vol)
            if (isAutoplayActive) {
                if (!mp.isPlaying) mp.start()
            } else {
                if (mp.isPlaying) mp.pause()
            }
        } catch (_: Exception) {
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(displayAspect)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0A2418))
            .border(1.2.dp, goldColor.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
            .clickable { onOpenFullScreen() }
            .testTag("play_video_${post.id}"),
        contentAlignment = Alignment.Center
    ) {
        val readyFile = materializedFile
        if (readyFile != null && isAutoplayActive && !hasPlaybackError) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        val textureView = this
                        isOpaque = false
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            private fun applyFitCenterMatrix(viewW: Int, viewH: Int, vidW: Int, vidH: Int) {
                                if (viewW <= 0 || viewH <= 0 || vidW <= 0 || vidH <= 0) return
                                val viewRatio = viewW.toFloat() / viewH.toFloat()
                                val vidRatio = vidW.toFloat() / vidH.toFloat()
                                val sx: Float
                                val sy: Float
                                if (vidRatio > viewRatio) {
                                    sx = 1f
                                    sy = viewRatio / vidRatio
                                } else {
                                    sx = vidRatio / viewRatio
                                    sy = 1f
                                }
                                textureView.setTransform(Matrix().apply { setScale(sx, sy, viewW / 2f, viewH / 2f) })
                            }

                            override fun onSurfaceTextureAvailable(
                                surfaceTexture: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                val surface = Surface(surfaceTexture)
                                activeSurfaceRef = surface
                                try {
                                    val mp = MediaPlayer().apply {
                                        setDataSource(readyFile.absolutePath)
                                        setSurface(surface)
                                        isLooping = true
                                        val vol = if (latestIsMuted) 0f else 1f
                                        setVolume(vol, vol)
                                        setOnVideoSizeChangedListener { _, w, h ->
                                            if (w > 0 && h > 0) {
                                                videoStreamAspect = w.toFloat() / h.toFloat()
                                                applyFitCenterMatrix(textureView.width, textureView.height, w, h)
                                            }
                                        }
                                        setOnInfoListener { _, what, _ ->
                                            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                                                isRenderingVideo = true
                                            }
                                            false
                                        }
                                        setOnPreparedListener { preparedMp ->
                                            if (mediaPlayerRef === preparedMp && latestIsAutoplay) {
                                                preparedMp.start()
                                                isRenderingVideo = true
                                            }
                                        }
                                        setOnErrorListener { _, _, _ ->
                                            hasPlaybackError = true
                                            isRenderingVideo = false
                                            true
                                        }
                                        prepareAsync()
                                    }
                                    mediaPlayerRef = mp
                                } catch (_: Exception) {
                                    hasPlaybackError = true
                                    isRenderingVideo = false
                                    surface.release()
                                }
                            }

                            override fun onSurfaceTextureSizeChanged(
                                surface: SurfaceTexture,
                                width: Int,
                                height: Int
                            ) {
                                val mp = mediaPlayerRef ?: return
                                try {
                                    applyFitCenterMatrix(width, height, mp.videoWidth, mp.videoHeight)
                                } catch (_: Exception) {
                                }
                            }

                            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                                isRenderingVideo = false
                                try {
                                    mediaPlayerRef?.setSurface(null)
                                    mediaPlayerRef?.release()
                                } catch (_: Exception) {
                                }
                                mediaPlayerRef = null
                                try {
                                    activeSurfaceRef?.release()
                                } catch (_: Exception) {
                                }
                                activeSurfaceRef = null
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            DisposableEffect(readyFile.absolutePath, isAutoplayActive) {
                onDispose {
                    isRenderingVideo = false
                    try {
                        mediaPlayerRef?.setSurface(null)
                        mediaPlayerRef?.release()
                    } catch (_: Exception) {
                    }
                    mediaPlayerRef = null
                    try {
                        activeSurfaceRef?.release()
                    } catch (_: Exception) {
                    }
                    activeSurfaceRef = null
                }
            }
        }

        // Keep crisp thumbnail visible until video first frame renders
        if (!isRenderingVideo) {
            if (thumbResolved != null) {
                AsyncImage(
                    model = thumbResolved,
                    contentDescription = "Video thumbnail",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f))
            )
            if (isMaterializing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(34.dp),
                    color = GoldYellow,
                    strokeWidth = 2.5.dp
                )
            } else if (!isAutoplayActive || hasPlaybackError) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.68f))
                        .border(2.dp, GoldYellow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play video",
                        tint = GoldYellow,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // Top-right Mute / Unmute toggle button
        IconButton(
            onClick = onToggleMute,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .minimumInteractiveComponentSize()
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .testTag("mute_video_${post.id}")
        ) {
            Icon(
                imageVector = if (isMuted) CricUpIcons.VolumeOff else CricUpIcons.VolumeUp,
                contentDescription = if (isMuted) "Unmute video" else "Mute video",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        // Bottom-left Duration / Live Autoplay Pill
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            color = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = if (isRenderingVideo && isAutoplayActive) {
                    "▶ ${MediaHelper.formatDuration(post.videoDurationMs)}"
                } else {
                    "🎬 ${MediaHelper.formatDuration(post.videoDurationMs)}"
                },
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Bottom-right Fullscreen expand affordance
        IconButton(
            onClick = onOpenFullScreen,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .minimumInteractiveComponentSize()
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.65f))
                .testTag("fullscreen_video_${post.id}")
        ) {
            Icon(
                imageVector = CricUpIcons.Fullscreen,
                contentDescription = "Full screen video",
                tint = GoldYellow,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

fun selectCenteredAutoplayVideoPostId(
    visibleItems: List<Triple<String, Int, Int>>,
    videoPostIds: Set<String>,
    viewportStart: Int,
    viewportEnd: Int
): String? {
    if (videoPostIds.isEmpty() || visibleItems.isEmpty() || viewportEnd <= viewportStart) return null
    val viewportCenter = (viewportStart + viewportEnd) / 2f
    var bestPostId: String? = null
    var bestDistance = Float.MAX_VALUE

    for ((key, offset, size) in visibleItems) {
        if (!key.startsWith("community_post_")) continue
        val postId = key.removePrefix("community_post_")
        if (postId !in videoPostIds) continue
        val itemStart = offset
        val itemEnd = offset + size
        val visiblePx = minOf(itemEnd, viewportEnd) - maxOf(itemStart, viewportStart)
        if (visiblePx <= 0) continue
        val visibleFraction = visiblePx.toFloat() / size.coerceAtLeast(1)
        if (visibleFraction < 0.35f && visiblePx < 220) continue
        val itemCenter = (itemStart + itemEnd) / 2f
        val distance = abs(itemCenter - viewportCenter)
        if (distance < bestDistance) {
            bestDistance = distance
            bestPostId = postId
        }
    }
    return bestPostId
}

private fun formatRelativePostTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val diffMs = System.currentTimeMillis() - timestamp
    return when {
        diffMs < 60_000L -> "এইমাত্র"
        diffMs < 3_600_000L -> "${diffMs / 60_000L} মি. আগে"
        diffMs < 86_400_000L -> "${diffMs / 3_600_000L} ঘ. আগে"
        else -> SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}
