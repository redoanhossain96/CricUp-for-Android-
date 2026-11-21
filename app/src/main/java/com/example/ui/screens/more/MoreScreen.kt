package com.example.ui.screens.more

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import com.example.ui.theme.CricUpIcons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.example.ui.components.CricUpAvatar
import com.example.ui.theme.adaptiveEmeraldColor
import com.example.ui.theme.adaptiveGoldColor
import kotlinx.coroutines.launch
import com.example.data.model.User
import com.example.di.AppContainer
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.LocalThemeIsDark
import com.example.ui.theme.WicketRed

@Composable
fun MoreScreen(
    container: AppContainer,
    onNavigateToRankings: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToUserProfile: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserFlow = remember(container) { container.authRepository.getCurrentUserFlow() }
    val initialUser = remember(currentUserFlow) {
        (currentUserFlow as? kotlinx.coroutines.flow.StateFlow<User?>)?.value
    }
    val currentUser by currentUserFlow.collectAsState(initial = initialUser)
    val memberProfile by container.postRepository.memberProfileFlow.collectAsState()
    val isGuest by container.authRepository.isGuestMode.collectAsState()
    val currentUserId = currentUser?.uid?.takeIf { it.isNotBlank() }
        ?: remember(container) { container.authRepository.getCurrentUserId() }
    val isGoogleSignedIn = currentUser?.isGoogleSignedIn == true || container.authRepository.isGoogleSignedIn()
    var isSigningInGoogle by remember { mutableStateOf(false) }

    val resolvedDisplayName = remember(memberProfile.displayName, currentUser?.displayName) {
        val profileName = memberProfile.displayName.trim()
        val authName = currentUser?.displayName?.trim().orEmpty()
        when {
            !com.example.data.repository.AuthRepository.isPlaceholderProfileName(profileName) -> profileName
            !com.example.data.repository.AuthRepository.isPlaceholderProfileName(authName) -> authName
            profileName.isNotBlank() -> profileName
            authName.isNotBlank() -> authName
            else -> container.authRepository.getEffectiveUserDisplayName()
        }
    }
    val resolvedPhotoUrl = remember(memberProfile.photoUrl, currentUser?.photoUrl) {
        memberProfile.photoUrl?.takeIf { it.isNotBlank() }
            ?: currentUser?.photoUrl?.takeIf { it.isNotBlank() }
    }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var gatedAction by remember { mutableStateOf<String?>(null) }

    val isDarkState = LocalThemeIsDark.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(14.dp)
    ) {
        // User Profile Card (Tapping opens Full Cricket Profile; Edit sets Name, Photo & Bat/Bowl Style)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onNavigateToUserProfile(currentUserId) }
                    .testTag("more_profile_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CricUpAvatar(
                        model = resolvedPhotoUrl,
                        size = 54.dp,
                        fallbackText = if (isGuest) "G" else resolvedDisplayName.take(2),
                        borderColor = adaptiveEmeraldColor(),
                        borderWidth = 2.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isGuest) "Viewer Mode (ভিউয়ার মোড)" else resolvedDisplayName,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val batType = currentUser?.battingStyle?.ifBlank { "Right Hand" } ?: "Right Hand"
                        val bowlType = currentUser?.bowlingStyle?.ifBlank { "Right-arm Medium" } ?: "Right-arm Medium"
                        val roleType = currentUser?.primaryRole?.ifBlank { "All-Rounder" } ?: "All-Rounder"
                        val followerCount = currentUser?.followerIds?.size ?: 0
                        val roleText = if (isGuest) {
                            "শুধুমাত্র স্কোর ও কোড সার্চ • ফিচারের জন্য Gmail লগইন করুন"
                        } else {
                            "$roleType • $batType • $bowlType • ফলোয়ার: $followerCount"
                        }
                        Text(
                            text = roleText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Normal
                        )
                        if (!isGuest && currentUserId.isNotBlank()) {
                            Spacer(modifier = Modifier.height(3.dp))
                            if (isGoogleSignedIn && !currentUser?.email.isNullOrBlank()) {
                                Text(
                                    text = "Gmail: ${currentUser?.email}",
                                    color = adaptiveGoldColor(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("CricUp Scorer ID", currentUserId))
                                        Toast.makeText(context, "Scorer ID কপি হয়েছে: $currentUserId", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 2.dp)
                                    .testTag("copy_user_id_btn")
                            ) {
                                Text(
                                    text = "Scorer ID: $currentUserId",
                                    color = adaptiveEmeraldColor(),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = CricUpIcons.ContentCopy,
                                    contentDescription = "Copy Scorer ID",
                                    tint = adaptiveEmeraldColor(),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (isGuest || !isGoogleSignedIn) {
                                gatedAction = "প্রোফাইল এডিট ও কাস্টমাইজ করতে"
                            } else {
                                showEditProfileDialog = true
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("more_edit_profile_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit profile",
                            tint = adaptiveEmeraldColor(),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "এডিট",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (showEditProfileDialog) {
                val baseProfileUser = (currentUser ?: User(uid = currentUserId)).copy(
                    uid = currentUserId,
                    displayName = resolvedDisplayName,
                    photoUrl = resolvedPhotoUrl
                )
                EditCricketProfileDialog(
                    user = baseProfileUser,
                    onDismiss = { showEditProfileDialog = false },
                    onSave = { newName, newPhotoUrl, cleared, role, batStyle, bowlStyle, bio ->
                        scope.launch {
                            container.postRepository.saveMemberProfile(
                                name = newName,
                                photoUrl = newPhotoUrl,
                                userId = currentUserId,
                                clearPhoto = cleared
                            )
                            container.authRepository.updateUserCricketProfile(
                                uid = currentUserId,
                                displayName = newName,
                                photoUrl = newPhotoUrl,
                                clearPhoto = cleared,
                                primaryRole = role,
                                battingStyle = batStyle,
                                bowlingStyle = bowlStyle,
                                bio = bio
                            )
                            showEditProfileDialog = false
                            Toast.makeText(context, "ক্রিকেট প্রোফাইল আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            var moreGoogleAuthError by remember { mutableStateOf<String?>(null) }
            com.example.ui.components.GoogleAccountLinkCard(
                user = currentUser,
                isGoogleSignedIn = isGoogleSignedIn,
                isSigningIn = isSigningInGoogle,
                statusMessage = moreGoogleAuthError,
                onSignInWithGoogle = {
                    moreGoogleAuthError = null
                    isSigningInGoogle = true
                    com.example.util.GoogleSignInHelper.signInWithGoogle(
                        context = context,
                        scope = scope,
                        authRepository = container.authRepository,
                        onSuccess = { linkedUser ->
                            isSigningInGoogle = false
                            moreGoogleAuthError = null
                            val label = linkedUser.email.takeIf { it.isNotBlank() } ?: linkedUser.displayName
                            Toast.makeText(
                                context,
                                "Gmail ($label) সফলভাবে লিংক ও লগইন হয়েছে!",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onError = { errMsg ->
                            isSigningInGoogle = false
                            moreGoogleAuthError = errMsg
                            Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                        },
                        onCancelled = {
                            isSigningInGoogle = false
                        }
                    )
                },
                onSignInWithGoogleBrowser = {
                    moreGoogleAuthError = null
                    isSigningInGoogle = true
                    com.example.util.GoogleSignInHelper.signInWithGoogleViaBrowser(
                        context = context,
                        scope = scope,
                        authRepository = container.authRepository,
                        onSuccess = { linkedUser ->
                            isSigningInGoogle = false
                            moreGoogleAuthError = null
                            val label = linkedUser.email.takeIf { it.isNotBlank() } ?: linkedUser.displayName
                            Toast.makeText(
                                context,
                                "Gmail ($label) সফলভাবে লিংক ও লগইন হয়েছে!",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onError = { errMsg ->
                            isSigningInGoogle = false
                            moreGoogleAuthError = errMsg
                            Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                onSignOut = {
                    moreGoogleAuthError = null
                    com.example.util.GoogleSignInHelper.signOutFromGoogle(
                        context = context,
                        scope = scope,
                        authRepository = container.authRepository,
                        onComplete = {
                            Toast.makeText(
                                context,
                                "Gmail থেকে সাইন-আউট সম্পন্ন হয়েছে (ডিভাইস মোড চালু)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            )

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Appearance / Theme Switch
        item {
            Text(
                text = "APPEARANCE & THEME / থিম নির্বাচন",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { isDarkState.value = !isDarkState.value }
                    .testTag("more_theme_toggle"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isDarkState.value) Color(0xFF38BDF8).copy(alpha = 0.15f)
                                    else com.example.ui.theme.LightGoldSoftBg
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDarkState.value) CricUpIcons.DarkMode else CricUpIcons.LightMode,
                                contentDescription = null,
                                tint = com.example.ui.theme.adaptiveGoldColor(),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isDarkState.value) "Dark Mode (ডার্ক মোড চালু)" else "Golden-Green Light Theme (গোল্ডেন-গ্রীন লাইট)",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isDarkState.value) "Deep Stadium Golden-Green Dark theme active" else "Modern Golden-Green Light theme active • Switch to Dark anytime",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = isDarkState.value,
                        onCheckedChange = { isDarkState.value = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.primary,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Statistics & Rankings
        item {
            Text(
                text = "STATISTICS & RECORDS",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            MoreMenuItem(
                title = "আমার প্রোফাইল, দল ও ফলোয়ার",
                subtitle = "ম্যাচ, রান, উইকেট, ব্যাটিং-বোলিং ধরন, যেকোনো দলে যোগদান ও ফলো",
                icon = CricUpIcons.VerifiedUser,
                tint = adaptiveGoldColor(),
                testTag = "more_open_user_profile_btn",
                onClick = { onNavigateToUserProfile(currentUserId) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            MoreMenuItem(
                title = "Player & Team Rankings",
                subtitle = "Top run scorers, wicket takers, strike rates and economy",
                icon = CricUpIcons.Leaderboard,
                tint = EmeraldGreen,
                onClick = onNavigateToRankings
            )

            Spacer(modifier = Modifier.height(8.dp))

            MoreMenuItem(
                title = "Tournament Standings & NRR",
                subtitle = "Points table, net run rate, and champions directory",
                icon = CricUpIcons.WorkspacePremium,
                tint = Color(0xFF0284C7),
                onClick = onNavigateToRankings
            )

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Scoring & Commentary Preferences
        item {
            val context = androidx.compose.ui.platform.LocalContext.current
            val sharedPrefs = remember { context.getSharedPreferences("cricup_prefs", android.content.Context.MODE_PRIVATE) }
            var isCommentaryOn by remember { mutableStateOf(sharedPrefs.getBoolean("pref_commentary_enabled", true)) }
            var commentaryLang by remember { mutableStateOf(sharedPrefs.getString("pref_commentary_lang", "bn") ?: "bn") }

            Text(
                text = "SCORING & COMMENTARY SETTINGS",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isCommentaryOn) GoldYellow.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isCommentaryOn) "🎙️" else "🔇",
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "বল-বাই-বল ধারাভাষ্য (Commentary)",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isCommentaryOn) "লাইভ স্কোরিংয়ের সময় প্রতিটি বলের ধারাভাষ্য পর্দায় পপ-আপ হবে" else "ধারাভাষ্য পপ-আপ বন্ধ রয়েছে",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = isCommentaryOn,
                            onCheckedChange = { checked: Boolean ->
                                isCommentaryOn = checked
                                sharedPrefs.edit().putBoolean("pref_commentary_enabled", checked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoldYellow,
                                checkedTrackColor = EmeraldGreen
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ধারাভাষ্যের ভাষা (Commentary Language)",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (commentaryLang == "bn") "বাংলা ধারাভাষ্য সক্রিয় রয়েছে" else "English Commentary active",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = commentaryLang == "bn",
                                onClick = {
                                    commentaryLang = "bn"
                                    sharedPrefs.edit().putString("pref_commentary_lang", "bn").apply()
                                },
                                label = { Text("বাংলা", fontSize = 11.5.sp) }
                            )
                            FilterChip(
                                selected = commentaryLang == "en",
                                onClick = {
                                    commentaryLang = "en"
                                    sharedPrefs.edit().putString("pref_commentary_lang", "en").apply()
                                },
                                label = { Text("English", fontSize = 11.5.sp) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // System & Auth
        item {
            Text(
                text = "ACCOUNT & PLATFORM",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            MoreMenuItem(
                title = "About CricUp",
                subtitle = "Version ${com.example.BuildConfig.VERSION_NAME} • Professional Cricket Management & Live Scoring",
                icon = Icons.Default.Info,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onNavigateToAbout
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(EmeraldGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = CricUpIcons.VerifiedUser,
                            contentDescription = "Account Identity",
                            tint = EmeraldGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isGoogleSignedIn) {
                                "ক্লাউড পরিচয়: Gmail লিংকড (Cloud Sync)"
                            } else {
                                "ডিভাইস পরিচয়: সক্রিয় (Device Identity)"
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isGoogleSignedIn) {
                                "UID: $currentUserId\nGmail: ${currentUser?.email.orEmpty()}\nযেকোনো নতুন ফোনে এই Gmail দিয়ে লগইন করলে আপনার সম্পূর্ণ প্রোফাইল ফিরে পাবেন।"
                            } else {
                                "আইডি: $currentUserId\nলগইন ছাড়াই এই ডিভাইসের সকল ম্যাচ ও স্কোর আজীবন সংরক্ষিত থাকবে।"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }

    gatedAction?.let { actionLabel ->
        com.example.ui.components.GuestRestrictedDialog(
            actionName = actionLabel,
            container = container,
            onDismiss = { gatedAction = null }
        )
    }
}

@Composable
private fun MoreMenuItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    testTag: String = "",
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
