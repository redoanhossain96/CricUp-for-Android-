package com.example.ui.screens.more

import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import com.example.ui.theme.CricUpIcons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlayerRoles
import com.example.data.model.Team
import com.example.data.model.User
import com.example.di.AppContainer
import com.example.ui.components.CricUpAvatar
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.WicketRed
import com.example.ui.theme.adaptiveEmeraldColor
import com.example.ui.theme.adaptiveGoldColor
import com.example.util.FeaturedPlayerSummary
import com.example.util.FeaturedPlayersAggregator
import com.example.util.MediaHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PROFILE_BOWLING_STYLES = listOf(
    "Right-arm Medium",
    "Right-arm Fast",
    "Right-arm Off Spin",
    "Right-arm Leg Spin",
    "Left-arm Fast",
    "Left-arm Orthodox",
    "Left-arm Chinaman",
    "None"
)

private val PROFILE_ROLE_OPTIONS = listOf(
    PlayerRoles.BATSMAN,
    PlayerRoles.BOWLER,
    PlayerRoles.ALL_ROUNDER,
    PlayerRoles.WICKETKEEPER
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    targetUserId: String,
    container: AppContainer,
    onBack: () -> Unit,
    onSelectUser: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val emerald = adaptiveEmeraldColor()
    val gold = adaptiveGoldColor()

    val currentUserFlow = remember(container) { container.authRepository.getCurrentUserFlow() }
    val selfUser by currentUserFlow.collectAsState()
    val selfUid = selfUser?.uid?.takeIf { it.isNotBlank() } ?: container.authRepository.getCurrentUserId()
    val effectiveTargetUid = targetUserId.ifBlank { selfUid }
    val isSelf = effectiveTargetUid == selfUid

    val allUsers by container.authRepository.allUsersFlow.collectAsState()
    val allMatches by remember(effectiveTargetUid) {
        container.matchRepository.getMyMatchesFlow(effectiveTargetUid)
    }.collectAsState(initial = emptyList())
    val allPlayers by remember(effectiveTargetUid) {
        container.playerRepository.getMyPlayersFlow(effectiveTargetUid)
    }.collectAsState(initial = emptyList())
    val allTeams by remember(effectiveTargetUid) {
        container.teamRepository.getMyTeamsFlow(effectiveTargetUid)
    }.collectAsState(initial = emptyList())
    val posts by container.postRepository.postsFlow.collectAsState(initial = emptyList())

    val profileUser = remember(effectiveTargetUid, selfUser, allUsers, allPlayers, posts) {
        val ownUser = selfUser
        if (isSelf && ownUser != null) {
            ownUser
        } else {
            allUsers.firstOrNull { it.uid == effectiveTargetUid }
                ?: run {
                    val matchedPlayer = allPlayers.firstOrNull { it.id == effectiveTargetUid || it.creatorId == effectiveTargetUid }
                    val matchedPost = posts.firstOrNull { it.authorId == effectiveTargetUid }
                    User(
                        uid = effectiveTargetUid,
                        displayName = matchedPlayer?.fullName
                            ?: matchedPost?.authorName
                            ?: "CricUp Player",
                        photoUrl = matchedPlayer?.photoUrl ?: matchedPost?.authorAvatarUrl,
                        primaryRole = matchedPlayer?.primaryRole ?: PlayerRoles.ALL_ROUNDER,
                        battingStyle = matchedPlayer?.battingStyle ?: "Right Hand",
                        bowlingStyle = matchedPlayer?.bowlingStyle ?: "Right-arm Medium",
                        linkedPlayerId = matchedPlayer?.id ?: effectiveTargetUid,
                        joinedTeamIds = matchedPlayer?.teamIds.orEmpty()
                    )
                }
        }
    }

    val careerStats by produceState(
        initialValue = FeaturedPlayerSummary(
            id = profileUser.effectivePlayerId,
            name = profileUser.displayName.ifBlank { "Member" },
            role = profileUser.primaryRole,
            photoUrl = profileUser.photoUrl
        ),
        profileUser,
        allMatches,
        allPlayers
    ) {
        value = withContext(Dispatchers.Default) {
            FeaturedPlayersAggregator.computeUserCareerStats(
                user = profileUser,
                matches = allMatches,
                dbPlayers = allPlayers
            )
        }
    }

    val memberTeams = remember(allTeams, profileUser) {
        val pid = profileUser.effectivePlayerId
        allTeams.filter { team ->
            !team.isArchived && (
                profileUser.joinedTeamIds.contains(team.id) ||
                    team.playerIds.contains(pid) ||
                    team.playerIds.contains(profileUser.uid) ||
                    team.ownerId == profileUser.uid
                )
        }
    }

    val isFollowingTarget = remember(selfUser?.followingIds, effectiveTargetUid) {
        selfUser?.followingIds?.contains(effectiveTargetUid) == true
    }

    var showEditCricketProfileDialog by remember { mutableStateOf(false) }
    var showJoinTeamsDialog by remember { mutableStateOf(false) }
    var showMembersDirectoryDialog by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(showMembersDirectoryDialog, isSelf) {
        if (showMembersDirectoryDialog || !isSelf) {
            container.authRepository.ensureUsersDirectorySynced()
        }
    }
    val isViewerGuest by container.authRepository.isGuestMode.collectAsState()
    var gatedAction by remember { mutableStateOf<String?>(null) }
    val isGoogleSignedIn = isSelf && (profileUser.isGoogleSignedIn || container.authRepository.isGoogleSignedIn())
    var isSigningInGoogle by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isSelf) "আমার ক্রিকেট প্রোফাইল" else profileUser.displayName.ifBlank { "ক্রিকেটার প্রোফাইল" },
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("user_profile_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showMembersDirectoryDialog = true },
                        modifier = Modifier.height(48.dp).testTag("open_members_directory_btn")
                    ) {
                        Icon(CricUpIcons.People, contentDescription = "All Players", tint = emerald, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ক্রিকেটার", color = emerald, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isViewerGuest && isSelf) {
                item {
                    com.example.ui.components.GuestVisitorBanner(
                        onNavigateToAuth = { gatedAction = "প্রোফাইল এডিট, দলে যোগদান ও ফলো করতে" }
                    )
                }
            }

            // 1. Identity Header + Style + Follow / Edit Action
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("user_profile_header_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.2.dp, emerald.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CricUpAvatar(
                            model = profileUser.photoUrl,
                            size = 86.dp,
                            fallbackText = profileUser.displayName.ifBlank { "U" }.take(2),
                            borderColor = gold,
                            borderWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = profileUser.displayName.ifBlank { "CricUp Member" },
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (!profileUser.email.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Gmail: ${profileUser.email}",
                                color = emerald,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (profileUser.bio.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = profileUser.bio,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        // Role, Batting Style & Bowling Style Badges
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StyleBadge("${PlayerRoles.getRoleIcon(profileUser.primaryRole)} ${profileUser.primaryRole}", emerald)
                            val batLabel = if (profileUser.battingStyle.contains("Left", ignoreCase = true)) {
                                "🏏 বামহাতি ব্যাটসম্যান (LHB)"
                            } else {
                                "🏏 ডানহাতি ব্যাটসম্যান (RHB)"
                            }
                            StyleBadge(batLabel, gold)
                            if (!profileUser.bowlingStyle.equals("None", ignoreCase = true)) {
                                StyleBadge("🎯 ${profileUser.bowlingStyle}", Color(0xFF0284C7))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Followers / Following Strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            FollowMetric(label = "ফলোয়ার (Followers)", count = profileUser.followerIds.size)
                            FollowMetric(label = "ফলোয়িং (Following)", count = profileUser.followingIds.size)
                            FollowMetric(label = "দল (Teams)", count = memberTeams.size)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (isSelf) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (isViewerGuest) {
                                            gatedAction = "ক্রিকেট প্রোফাইল এডিট করতে"
                                        } else {
                                            showEditCricketProfileDialog = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("edit_cricket_profile_btn")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("প্রোফাইল এডিট", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (isViewerGuest) {
                                            gatedAction = "যেকোনো দলে খেলোয়াড় হিসেবে যোগ দিতে"
                                        } else {
                                            showJoinTeamsDialog = true
                                        }
                                    },
                                    border = BorderStroke(1.2.dp, gold),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("open_join_teams_btn")
                                ) {
                                    Icon(CricUpIcons.Group, contentDescription = null, tint = gold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("দলে যোগ দিন", color = gold, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (isViewerGuest) {
                                        gatedAction = "এই ক্রিকেটারকে ফলো করতে"
                                    } else {
                                        scope.launch {
                                            val res = container.authRepository.toggleFollowUser(profileUser.uid)
                                            res.onSuccess { nowFollowing ->
                                                Toast.makeText(
                                                    context,
                                                    if (nowFollowing) "ফলো করা হয়েছে!" else "আনফলো করা হয়েছে",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowingTarget) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isFollowingTarget) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                                ),
                                border = if (isFollowingTarget) BorderStroke(1.dp, emerald) else null,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("follow_user_btn_${profileUser.uid}")
                            ) {
                                Icon(
                                    imageVector = if (isFollowingTarget) Icons.Default.Check else CricUpIcons.PersonAdd,
                                    contentDescription = if (isFollowingTarget) "Following" else "Follow",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isFollowingTarget) "Following (ফলো করা হচ্ছে)" else "Follow (ফলো করুন)",
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }

            // 2. Own Match, Run, Wicket & Performance Stats Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("user_profile_stats_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "ব্যক্তিগত ক্রিকেট পরিসংখ্যান (Career Stats)",
                            color = gold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CareerStatTile("মোট ম্যাচ", "${careerStats.matchesPlayed}", emerald, Modifier.weight(1f))
                            CareerStatTile("মোট রান", "${careerStats.totalRuns}", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                            CareerStatTile("মোট উইকেট", "${careerStats.totalWickets}", WicketRed, Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CareerStatTile("Strike Rate", careerStats.formattedStrikeRate, gold, Modifier.weight(1f))
                            CareerStatTile("Best ব্যাটিং", careerStats.bestKnockText, emerald, Modifier.weight(1f))
                            CareerStatTile("Best বোলিং", careerStats.bestBowlingSpellText, Color(0xFF0284C7), Modifier.weight(1f))
                        }
                    }
                }
            }

            // 2b. Spectator Live Prediction Points & Rank Card
            item(key = "user_profile_prediction_stats") {
                val totalPreds = profileUser.predictionsTotal.coerceAtLeast(0)
                val wonPreds = profileUser.predictionsWon.coerceAtLeast(0)
                val winRatePct = if (totalPreds > 0) ((wonPreds * 100f) / totalPreds).toInt() else 0
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user_profile_prediction_stats_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.2.dp, gold.copy(alpha = 0.55f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎯 স্পেক্টেটর প্রেডিকশন অর্জন",
                                color = emerald,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Surface(
                                color = gold.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, gold.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = profileUser.predictionRankLabel,
                                    color = gold,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CareerStatTile(
                                label = "মোট কয়েন",
                                value = "🪙 ${profileUser.predictionPoints}",
                                valueColor = gold,
                                modifier = Modifier.weight(1f)
                            )
                            CareerStatTile(
                                label = "সঠিক প্রেডিকশন",
                                value = "$wonPreds / $totalPreds",
                                valueColor = emerald,
                                modifier = Modifier.weight(1f)
                            )
                            CareerStatTile(
                                label = "সফলতার হার",
                                value = "$winRatePct%",
                                valueColor = Color(0xFF0284C7),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 3. Teams Section ("যে দলগুলোর সদস্য")
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("user_profile_teams_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "দলের সদস্যপদ (${memberTeams.size}টি দল)",
                                color = emerald,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (isSelf) {
                                TextButton(
                                    onClick = {
                                        if (isViewerGuest) {
                                            gatedAction = "দলে খেলোয়াড় হিসেবে যোগ দিতে"
                                        } else {
                                            showJoinTeamsDialog = true
                                        }
                                    },
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("দলে যোগ দিন", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        if (memberTeams.isEmpty()) {
                            Text(
                                text = if (isSelf) {
                                    "আপনি এখনো কোনো দলের খেলোয়াড় সদস্য হননি। 'দলে যোগ দিন' বাটনে চাপ দিয়ে যেকোনো দলে খেলতে পারেন।"
                                } else {
                                    "এখনো কোনো দলের সদস্য তথ্য নেই।"
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        } else {
                            memberTeams.forEach { team ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        CricUpAvatar(
                                            model = team.logoUrl,
                                            size = 38.dp,
                                            fallbackText = team.shortName.ifBlank { team.name }.take(3),
                                            borderColor = emerald
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(team.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Text("${team.shortName} • স্কোয়াড: ${team.playerIds.size} জন", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (isSelf && team.ownerId != profileUser.uid) {
                                        TextButton(
                                            onClick = {
                                                if (isViewerGuest) {
                                                    gatedAction = "দলের সদস্যপদ পরিবর্তন করতে"
                                                } else {
                                                    scope.launch {
                                                        container.toggleUserTeamMembership(team.id, join = false)
                                                        Toast.makeText(context, "${team.name} দল থেকে বের হয়েছেন", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Text("ছেড়ে দিন", color = WicketRed, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isSelf) {
                item {
                    var profileGoogleAuthError by remember { mutableStateOf<String?>(null) }
                    com.example.ui.components.GoogleAccountLinkCard(
                        user = profileUser,
                        isGoogleSignedIn = isGoogleSignedIn,
                        isSigningIn = isSigningInGoogle,
                        statusMessage = profileGoogleAuthError,
                        onSignInWithGoogle = {
                            profileGoogleAuthError = null
                            isSigningInGoogle = true
                            com.example.util.GoogleSignInHelper.signInWithGoogle(
                                context = context,
                                scope = scope,
                                authRepository = container.authRepository,
                                onSuccess = { linkedUser ->
                                    isSigningInGoogle = false
                                    profileGoogleAuthError = null
                                    val label = linkedUser.email.takeIf { it.isNotBlank() } ?: linkedUser.displayName
                                    Toast.makeText(
                                        context,
                                        "Gmail ($label) সফলভাবে প্রোফাইলে লিংক হয়েছে!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                onError = { err ->
                                    isSigningInGoogle = false
                                    profileGoogleAuthError = err
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                },
                                onCancelled = {
                                    isSigningInGoogle = false
                                }
                            )
                        },
                        onSignInWithGoogleBrowser = {
                            profileGoogleAuthError = null
                            isSigningInGoogle = true
                            com.example.util.GoogleSignInHelper.signInWithGoogleViaBrowser(
                                context = context,
                                scope = scope,
                                authRepository = container.authRepository,
                                onSuccess = { linkedUser ->
                                    isSigningInGoogle = false
                                    profileGoogleAuthError = null
                                    val label = linkedUser.email.takeIf { it.isNotBlank() } ?: linkedUser.displayName
                                    Toast.makeText(
                                        context,
                                        "Gmail ($label) সফলভাবে প্রোফাইলে লিংক হয়েছে!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                onError = { err ->
                                    isSigningInGoogle = false
                                    profileGoogleAuthError = err
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        onSignOut = {
                            profileGoogleAuthError = null
                            com.example.util.GoogleSignInHelper.signOutFromGoogle(
                                context = context,
                                scope = scope,
                                authRepository = container.authRepository,
                                onComplete = {
                                    Toast.makeText(
                                        context,
                                        "Gmail থেকে সাইন-আউট সম্পন্ন হয়েছে",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    )
                }
            }
        }
    }

    if (showEditCricketProfileDialog) {
        EditCricketProfileDialog(
            user = profileUser,
            onDismiss = { showEditCricketProfileDialog = false },
            onSave = { name, photoUrl, clearPhoto, role, batStyle, bowlStyle, bio ->
                scope.launch {
                    container.postRepository.saveMemberProfile(
                        name = name,
                        photoUrl = photoUrl,
                        userId = profileUser.uid,
                        clearPhoto = clearPhoto
                    )
                    container.authRepository.updateUserCricketProfile(
                        uid = profileUser.uid,
                        displayName = name,
                        photoUrl = photoUrl,
                        clearPhoto = clearPhoto,
                        primaryRole = role,
                        battingStyle = batStyle,
                        bowlingStyle = bowlStyle,
                        bio = bio
                    )
                    showEditCricketProfileDialog = false
                    Toast.makeText(context, "ক্রিকেট প্রোফাইল সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showJoinTeamsDialog) {
        JoinAnyTeamDialog(
            allTeams = allTeams.filter { !it.isArchived },
            joinedTeamIds = memberTeams.map { it.id }.toSet(),
            onToggleJoin = { team, shouldJoin ->
                scope.launch {
                    container.toggleUserTeamMembership(team.id, shouldJoin)
                    Toast.makeText(
                        context,
                        if (shouldJoin) "${team.name} দলে খেলোয়াড় হিসেবে যোগ দিয়েছেন!" else "${team.name} থেকে বের হয়েছেন",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { showJoinTeamsDialog = false }
        )
    }

    if (showMembersDirectoryDialog) {
        MembersDirectoryDialog(
            allUsers = allUsers,
            selfUid = selfUid,
            followingIds = selfUser?.followingIds.orEmpty().toSet(),
            onToggleFollow = { targetUid ->
                if (isViewerGuest) {
                    gatedAction = "অন্য ক্রিকেটারকে ফলো করতে"
                } else {
                    scope.launch { container.authRepository.toggleFollowUser(targetUid) }
                }
            },
            onSelectUser = { targetUid ->
                showMembersDirectoryDialog = false
                onSelectUser(targetUid)
            },
            onDismiss = { showMembersDirectoryDialog = false }
        )
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
private fun StyleBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun FollowMetric(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun CareerStatTile(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                color = valueColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditCricketProfileDialog(
    user: User,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        photoUrl: String?,
        clearPhoto: Boolean,
        role: String,
        battingStyle: String,
        bowlingStyle: String,
        bio: String
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val emerald = adaptiveEmeraldColor()
    val gold = adaptiveGoldColor()

    var nameDraft by remember(user) { mutableStateOf(user.displayName) }
    var photoDraft by remember(user) { mutableStateOf(user.photoUrl) }
    var photoCleared by remember(user) { mutableStateOf(false) }
    var roleDraft by remember(user) { mutableStateOf(user.primaryRole.ifBlank { PlayerRoles.ALL_ROUNDER }) }
    var batStyleDraft by remember(user) { mutableStateOf(user.battingStyle.ifBlank { "Right Hand" }) }
    var bowlStyleDraft by remember(user) { mutableStateOf(user.bowlingStyle.ifBlank { "Right-arm Medium" }) }
    var bioDraft by remember(user) { mutableStateOf(user.bio) }
    var isProcessingPhoto by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            isProcessingPhoto = true
            scope.launch {
                val compressed = MediaHelper.compressAvatarToBase64(context, uri)
                isProcessingPhoto = false
                if (compressed != null) {
                    photoDraft = compressed
                    photoCleared = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("প্রোফাইল ও খেলার ধরন", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        CricUpAvatar(model = photoDraft, size = 64.dp, fallbackText = nameDraft.ifBlank { "U" }.take(2), borderColor = emerald)
                        if (isProcessingPhoto) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = gold)
                    }
                    OutlinedButton(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(CricUpIcons.Image, contentDescription = "Pick Photo", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ছবি", fontSize = 12.sp)
                    }
                    if (!photoDraft.isNullOrBlank()) {
                        TextButton(
                            onClick = { photoDraft = null; photoCleared = true },
                            modifier = Modifier.height(48.dp)
                        ) { Text("মুছুন", color = WicketRed) }
                    }
                }

                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text("নাম (Display Name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("cricket_profile_name_input")
                )

                OutlinedTextField(
                    value = bioDraft,
                    onValueChange = { bioDraft = it },
                    label = { Text("পরিচিতি / Bio (ঐচ্ছিক)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("মূল ভূমিকা (Role):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = gold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PROFILE_ROLE_OPTIONS.forEach { r ->
                        val selected = roleDraft.equals(r, ignoreCase = true)
                        FilterChip(
                            selected = selected,
                            onClick = { roleDraft = r },
                            label = { Text("${PlayerRoles.getRoleIcon(r)} $r", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer)
                        )
                    }
                }

                Text("ব্যাটিং ধরন (Batting Style):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = gold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("Right Hand" to "ডানহাতি (RHB)", "Left Hand" to "বামহাতি (LHB)").forEach { (key, label) ->
                        FilterChip(
                            selected = batStyleDraft == key,
                            onClick = { batStyleDraft = key },
                            label = { Text(label, fontSize = 11.5.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text("বোলিং ধরন (Bowling Style):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = gold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PROFILE_BOWLING_STYLES.forEach { style ->
                        FilterChip(
                            selected = bowlStyleDraft == style,
                            onClick = { bowlStyleDraft = style },
                            label = { Text(style, fontSize = 10.5.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameDraft.isNotBlank()) {
                        onSave(nameDraft.trim(), photoDraft, photoCleared, roleDraft, batStyleDraft, bowlStyleDraft, bioDraft)
                    }
                },
                enabled = nameDraft.isNotBlank() && !isProcessingPhoto,
                modifier = Modifier.height(48.dp).testTag("save_cricket_profile_btn")
            ) { Text("সংরক্ষণ করুন") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) { Text("বাতিল") }
        }
    )
}

@Composable
private fun JoinAnyTeamDialog(
    allTeams: List<Team>,
    joinedTeamIds: Set<String>,
    onToggleJoin: (Team, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val emerald = adaptiveEmeraldColor()
    var query by remember { mutableStateOf("") }
    val filtered = remember(allTeams, query) {
        if (query.isBlank()) allTeams
        else allTeams.filter {
            it.name.contains(query, ignoreCase = true) || it.shortName.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("যেকোনো দলের সদস্য হন", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("দলের নাম খুঁজুন...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (filtered.isEmpty()) {
                    Text("কোনো দল পাওয়া যায়নি। My Cricket > Teams থেকে নতুন দল তৈরি করুন।", fontSize = 12.sp)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(filtered, key = { it.id }) { team ->
                            val isJoined = joinedTeamIds.contains(team.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    CricUpAvatar(model = team.logoUrl, size = 36.dp, fallbackText = team.shortName.take(3))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(team.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${team.shortName} • ${team.playerIds.size} জন", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onToggleJoin(team, !isJoined) },
                                    border = BorderStroke(1.dp, if (isJoined) WicketRed else emerald),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(40.dp).testTag("dialog_join_team_${team.id}")
                                ) {
                                    Text(
                                        text = if (isJoined) "সদস্য ✓" else "যোগ দিন",
                                        color = if (isJoined) emerald else emerald,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) { Text("সম্পন্ন") }
        }
    )
}

@Composable
private fun MembersDirectoryDialog(
    allUsers: List<User>,
    selfUid: String,
    followingIds: Set<String>,
    onToggleFollow: (String) -> Unit,
    onSelectUser: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val emerald = adaptiveEmeraldColor()
    var query by remember { mutableStateOf("") }
    val filtered = remember(allUsers, query) {
        allUsers
            .filter {
                query.isBlank() ||
                    it.displayName.contains(query, ignoreCase = true) ||
                    it.primaryRole.contains(query, ignoreCase = true)
            }
            .sortedByDescending { it.followerIds.size }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("সকল ক্রিকেটার ও মেম্বার", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("নাম বা ধরন লিখুন...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered, key = { it.uid }) { member ->
                        val isSelf = member.uid == selfUid
                        val isFollowing = followingIds.contains(member.uid)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .clickable { onSelectUser(member.uid) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                CricUpAvatar(model = member.photoUrl, size = 38.dp, fallbackText = member.displayName.take(2))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (isSelf) "${member.displayName} (আপনি)" else member.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${member.primaryRole} • ফলোয়ার: ${member.followerIds.size}",
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!isSelf) {
                                OutlinedButton(
                                    onClick = { onToggleFollow(member.uid) },
                                    border = BorderStroke(1.dp, emerald),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Text(
                                        text = if (isFollowing) "Following" else "Follow",
                                        color = emerald,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) { Text("বন্ধ করুন") }
        }
    )
}
