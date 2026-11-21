package com.example.ui.screens.home

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.CommunityPost
import com.example.data.model.PostMediaType
import com.example.data.repository.PostRepository
import com.example.di.AppContainer
import com.example.ui.components.CricUpAvatar
import com.example.ui.components.GuestRestrictedDialog
import com.example.ui.components.GuestVisitorBanner
import com.example.ui.theme.DynamicHeroCardGradient
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldNeon
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.LightHeroCardGradient
import com.example.ui.theme.LocalThemeIsDark
import com.example.ui.theme.StadiumCyanLight
import com.example.ui.theme.WicketRed
import com.example.ui.theme.adaptiveEmeraldColor
import com.example.ui.theme.adaptiveGoldColor
import com.example.util.FeaturedPlayerSummary
import com.example.util.FeaturedPlayersAggregator
import com.example.util.ImageModelResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun HomeScreen(
    container: AppContainer,
    onNavigateToMatch: (String) -> Unit,
    onNavigateToCreateMatch: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToMyCricket: (Int) -> Unit,
    onNavigateToAuth: () -> Unit = {},
    onNavigateToUserProfile: (String) -> Unit = {}
) {
    val currentUserFlow = remember(container) { container.authRepository.getCurrentUserFlow() }
    val initialUser = remember(currentUserFlow) {
        (currentUserFlow as? kotlinx.coroutines.flow.StateFlow<com.example.data.model.User?>)?.value
    }
    val currentUser by currentUserFlow.collectAsState(initial = initialUser)
    val isGuest by container.authRepository.isGuestMode.collectAsState()

    val currentUserId = currentUser?.uid?.takeIf { it.isNotBlank() }
        ?: remember(container) { container.authRepository.getCurrentUserId() }

    val allMatches by remember(currentUserId) {
        container.matchRepository.getMyMatchesFlow(currentUserId)
    }.collectAsState(initial = emptyList())
    val topPlayers by remember(currentUserId) {
        container.playerRepository.getMyPlayersFlow(currentUserId)
    }.collectAsState(initial = emptyList())
    val communityPosts by container.postRepository.postsFlow.collectAsState(initial = emptyList())
    val isRefreshingFeed by container.postRepository.isRefreshingFeed.collectAsState()
    val hasMoreCloudPosts by container.postRepository.hasMoreCloudPosts.collectAsState()
    val memberProfile by container.postRepository.memberProfileFlow.collectAsState()
    val memberDisplayName = remember(memberProfile.displayName, currentUser?.displayName) {
        val profileName = memberProfile.displayName.trim()
        val authName = currentUser?.displayName?.trim().orEmpty()
        when {
            !com.example.data.repository.AuthRepository.isPlaceholderProfileName(profileName) -> profileName
            !com.example.data.repository.AuthRepository.isPlaceholderProfileName(authName) -> authName
            profileName.isNotBlank() -> profileName
            authName.isNotBlank() -> authName
            else -> container.postRepository.getSavedMemberDisplayName(
                container.authRepository.getEffectiveUserDisplayName()
            )
        }
    }
    val memberAvatarUrl = remember(memberProfile.photoUrl, currentUser?.photoUrl) {
        memberProfile.photoUrl?.takeIf { it.isNotBlank() }
            ?: currentUser?.photoUrl?.takeIf { it.isNotBlank() }
    }

    val isDark = LocalThemeIsDark.current.value
    val adaptiveGold = adaptiveGoldColor()
    val adaptiveEmerald = adaptiveEmeraldColor()

    var postFilter by remember { mutableStateOf("সব") }
    var visiblePostLimit by remember { mutableIntStateOf(PostRepository.DEFAULT_FEED_PAGE_SIZE) }
    var fullScreenImageSource by remember { mutableStateOf<String?>(null) }
    var activeVideoPost by remember { mutableStateOf<CommunityPost?>(null) }
    var selectedFeaturedPlayer by remember { mutableStateOf<FeaturedPlayerSummary?>(null) }

    val filteredPosts = remember(communityPosts, postFilter, currentUserId) {
        when (postFilter) {
            "ছবি/ভিডিও" -> communityPosts.filter { it.resolvedMediaType() != PostMediaType.NONE }
            "আমার পোস্ট" -> communityPosts.filter { it.authorId == currentUserId }
            else -> communityPosts
        }
    }
    val visiblePosts = remember(filteredPosts, visiblePostLimit) {
        filteredPosts.take(visiblePostLimit)
    }

    val hasMoreLocalOrCloud = filteredPosts.size > visiblePosts.size || hasMoreCloudPosts
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var isScreenResumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isScreenResumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isScreenResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var isFeedVideoMuted by rememberSaveable { mutableStateOf(true) }
    val visibleVideoPostIds = remember(visiblePosts) {
        visiblePosts
            .filter { it.resolvedMediaType() == PostMediaType.VIDEO }
            .map { it.id }
            .toSet()
    }
    val activeAutoplayVideoPostId by remember(
        listState,
        visibleVideoPostIds,
        isScreenResumed,
        fullScreenImageSource,
        activeVideoPost
    ) {
        derivedStateOf {
            if (!isScreenResumed || fullScreenImageSource != null || activeVideoPost != null || visibleVideoPostIds.isEmpty()) {
                null
            } else {
                val layoutInfo = listState.layoutInfo
                val items = layoutInfo.visibleItemsInfo.mapNotNull { item ->
                    val k = item.key as? String ?: return@mapNotNull null
                    Triple(k, item.offset, item.size)
                }
                selectCenteredAutoplayVideoPostId(
                    visibleItems = items,
                    videoPostIds = visibleVideoPostIds,
                    viewportStart = layoutInfo.viewportStartOffset,
                    viewportEnd = layoutInfo.viewportEndOffset
                )
            }
        }
    }

    var lastAutoRefreshAtMs by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val reachedFeedBottom by remember(visiblePosts.size, filteredPosts.size) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            listState.firstVisibleItemIndex > 0 && totalItems > 0 && lastVisible >= totalItems - 2
        }
    }

    LaunchedEffect(reachedFeedBottom, isRefreshingFeed, postFilter) {
        if (reachedFeedBottom && !isRefreshingFeed) {
            val canExpand = filteredPosts.size > visiblePosts.size || hasMoreCloudPosts
            val now = System.currentTimeMillis()
            if (canExpand) {
                val nextLimit = visiblePostLimit + PostRepository.DEFAULT_FEED_PAGE_SIZE
                visiblePostLimit = nextLimit
                lastAutoRefreshAtMs = now
                container.postRepository.refreshAndLoadMorePosts(nextLimit)
            } else if (now - lastAutoRefreshAtMs >= 3_000L) {
                lastAutoRefreshAtMs = now
                container.postRepository.refreshAndLoadMorePosts(
                    visiblePostLimit.coerceAtLeast(PostRepository.DEFAULT_FEED_PAGE_SIZE)
                )
            }
        }
    }

    var showGuestDialog by remember { mutableStateOf(false) }
    var guestAttemptedAction by remember { mutableStateOf("ম্যাচ তৈরি") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchCodeInput by remember { mutableStateOf("") }
    var isSearchingCode by remember { mutableStateOf(false) }
    var searchErrorMessage by remember { mutableStateOf<String?>(null) }

    val hiddenFeaturedKeys by container.hiddenFeaturedPlayerKeysFlow.collectAsState()

    // Global Automatic Featured Players aggregated off the main thread across all users' matches & player database
    val globalFeaturedPlayers by produceState<List<com.example.util.FeaturedPlayerSummary>>(
        initialValue = emptyList(),
        allMatches,
        topPlayers,
        hiddenFeaturedKeys
    ) {
        value = withContext(Dispatchers.Default) {
            FeaturedPlayersAggregator.computeTopPlayers(
                matches = allMatches,
                dbPlayers = topPlayers,
                limit = 10,
                excludedKeys = hiddenFeaturedKeys
            )
        }
    }

    fun attemptAction(actionName: String, action: () -> Unit) {
        if (isGuest) {
            guestAttemptedAction = actionName
            showGuestDialog = true
        } else {
            action()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // App Header / Branding + Editable User Avatar + Light/Dark Theme Switch
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.cricup_logo),
                        contentDescription = "CricUp Logo",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, adaptiveEmerald.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CricUp",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Cricket & Community Hub",
                            color = adaptiveEmerald,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable { onNavigateToUserProfile(currentUserId) }
                        .testTag("home_header_profile_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    CricUpAvatar(
                        model = memberAvatarUrl,
                        size = 38.dp,
                        fallbackText = memberDisplayName.take(2),
                        borderColor = adaptiveGold,
                        borderWidth = 1.5.dp
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                GoldenGreenThemeTogglePill()
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Guest Mode Restriction Notice & Upgrade to Verified Login Banner
        if (isGuest) {
            item {
                GuestVisitorBanner(
                    onNavigateToAuth = onNavigateToAuth,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }

        // COMPACT SIDE-BY-SIDE MATCH CENTRE & LIVE WATCH BY CODE PANELS
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Left Panel: Compact Match Centre
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { attemptAction("নতুন ম্যাচ তৈরি করতে") { onNavigateToCreateMatch() } }
                        .testTag("hero_new_match_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        Brush.horizontalGradient(listOf(EmeraldGreen, StadiumCyanLight, GoldYellow))
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isDark) DynamicHeroCardGradient else LightHeroCardGradient)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(EmeraldNeon.copy(alpha = 0.22f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "🏏 MATCH CENTRE",
                                    color = EmeraldNeon,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.6.sp,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "নতুন ম্যাচ",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                            Text(
                                text = "টস ও লাইভ স্কোরিং",
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { attemptAction("নতুন ম্যাচ তৈরি করতে") { onNavigateToCreateMatch() } },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldNeon),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("hero_new_match_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "New Match",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.5.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Right Panel: Compact Match Code Live Watch
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("home_search_match_code_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, adaptiveGold.copy(alpha = 0.55f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE53935).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = CricUpIcons.Sensors,
                                        contentDescription = null,
                                        tint = Color(0xFFE53935),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "ম্যাচ কোড দিয়ে লাইভ দেখুন",
                                    color = adaptiveGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 2,
                                    lineHeight = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = searchCodeInput,
                                onValueChange = {
                                    searchCodeInput = it
                                    searchErrorMessage = null
                                },
                                placeholder = {
                                    Text("কোড (CR7K9M)", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                singleLine = true,
                                trailingIcon = {
                                    if (searchCodeInput.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                searchCodeInput = ""
                                                searchErrorMessage = null
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldYellow,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("home_match_code_input")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                val clean = com.example.util.MatchCodeGenerator.normalizeCode(searchCodeInput)
                                if (clean.isNotBlank()) {
                                    isSearchingCode = true
                                    searchErrorMessage = null
                                    scope.launch {
                                        val searchResult = container.matchRepository.searchMatchDetailed(clean)
                                        isSearchingCode = false
                                        when (searchResult) {
                                            is com.example.data.repository.MatchSearchResult.Success -> {
                                                searchCodeInput = ""
                                                onNavigateToMatch(searchResult.match.id)
                                            }
                                            is com.example.data.repository.MatchSearchResult.NotFound -> {
                                                searchErrorMessage = "এই কোডে কোনো ম্যাচ পাওয়া যায়নি। ম্যাচ কোডটি চেক করুন।"
                                            }
                                            is com.example.data.repository.MatchSearchResult.PrivateMatch -> {
                                                searchErrorMessage = "এই ম্যাচটি প্রাইভেট (শুধুমাত্র নির্মাতা দেখতে পারবেন)"
                                            }
                                            is com.example.data.repository.MatchSearchResult.PermissionDenied -> {
                                                searchErrorMessage = "সিকিউরিটি রুলস সমস্যা (Permission Denied)"
                                            }
                                            is com.example.data.repository.MatchSearchResult.NetworkError -> {
                                                searchErrorMessage = "ইন্টারনেট সংযোগ চেক করুন"
                                            }
                                            is com.example.data.repository.MatchSearchResult.Error -> {
                                                searchErrorMessage = searchResult.message
                                            }
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldYellow),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            enabled = searchCodeInput.isNotBlank() && !isSearchingCode,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("home_match_code_btn")
                        ) {
                            if (isSearchingCode) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("লাইভ দেখুন", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            searchErrorMessage?.let { errMsg ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errMsg,
                    color = Color(0xFFE53935),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // QUICK ACTIONS PANEL (DIRECTLY BELOW MATCH CENTRE & LIVE CODE PANELS)
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(GoldYellow)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "QUICK ACTIONS",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionItem(
                    title = "Match",
                    icon = Icons.Default.AddCircle,
                    tint = EmeraldGreen,
                    modifier = Modifier.weight(1f),
                    onClick = { attemptAction("নতুন ম্যাচ তৈরি করতে") { onNavigateToCreateMatch() } }
                )
                QuickActionItem(
                    title = "Tourney",
                    icon = CricUpIcons.WorkspacePremium,
                    tint = GoldYellow,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToMyCricket(1) }
                )
                QuickActionItem(
                    title = "Teams",
                    icon = CricUpIcons.Group,
                    tint = Color(0xFF0284C7),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToMyCricket(2) }
                )
                QuickActionItem(
                    title = "Players",
                    icon = Icons.Default.Person,
                    tint = Color(0xFF7C3AED),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToMyCricket(3) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // FEATURED PLAYERS (Global Automatic Showcase above latest Cricket Feed)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 4.dp, height = 22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(GoldYellow)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FEATURED PLAYERS (সেরা পারফরমার)",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.4.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (hiddenFeaturedKeys.isNotEmpty() && !isGuest) {
                        TextButton(
                            onClick = {
                                attemptAction("Featured তালিকা পরিবর্তন করতে") {
                                    container.restoreAllHiddenFeaturedPlayers()
                                    Toast.makeText(context, "রিমুভ করা খেলোয়াড় পুনরায় দেখানো হচ্ছে", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("restore_featured_players_btn")
                        ) {
                            Text(
                                text = "ফেরান (${hiddenFeaturedKeys.size})",
                                color = adaptiveEmerald,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = "আপনার ম্যাচ ও খেলোয়াড় ডাটাবেজ থেকে স্বয়ংক্রিয়ভাবে সেরা খেলোয়াড়",
                    color = adaptiveGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (globalFeaturedPlayers.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (hiddenFeaturedKeys.isNotEmpty()) {
                                "সব Featured খেলোয়াড় রিমুভ করা হয়েছে (${hiddenFeaturedKeys.size} জন লুকানো)"
                            } else {
                                "ম্যাচ স্কোরিং শুরু হলে সেরা খেলোয়াড়রা এখানে স্বয়ংক্রিয়ভাবে প্রদর্শিত হবেন"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        if (hiddenFeaturedKeys.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = { container.restoreAllHiddenFeaturedPlayers() },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("পুনরায় দেখান", color = adaptiveEmerald, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(globalFeaturedPlayers, key = { idx, p -> "${idx}_${p.id}_${p.name}" }) { index, player ->
                        val isTopRank = index == 0
                        val resolvedPhoto = remember(player.photoUrl) { ImageModelResolver.resolveForCoil(player.photoUrl) }
                        Card(
                            modifier = Modifier
                                .width(214.dp)
                                .clickable { selectedFeaturedPlayer = player }
                                .testTag("featured_player_card_${player.id}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isTopRank) 4.dp else 2.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isTopRank) 1.5.dp else 1.dp,
                                color = if (isTopRank) adaptiveGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Large Top Edge-to-Edge Photo Banner
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(172.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = if (isTopRank) {
                                                    listOf(Color(0xFF14532D), Color(0xFF0A2E1A))
                                                } else {
                                                    listOf(Color(0xFF1E3A2F), Color(0xFF0F241C))
                                                }
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (resolvedPhoto != null) {
                                        AsyncImage(
                                            model = resolvedPhoto,
                                            contentDescription = player.name,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box(
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.14f))
                                                    .border(
                                                        1.5.dp,
                                                        if (isTopRank) GoldYellow else EmeraldNeon.copy(alpha = 0.7f),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = player.name.trim().take(2).uppercase(),
                                                    color = Color.White,
                                                    fontSize = 24.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = player.role,
                                                color = Color.White.copy(alpha = 0.8f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    // Compact Top-Left Rank Badge Overlay
                                    Surface(
                                        color = if (isTopRank) GoldYellow else Color.Black.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = if (isTopRank) "#1 ⭐" else "#${index + 1}",
                                            color = if (isTopRank) Color(0xFF1B1400) else Color.White,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }

                                    // Top-Right Remove Button from Featured Players List (Registered users only)
                                    if (!isGuest) {
                                        IconButton(
                                            onClick = {
                                                attemptAction("Featured তালিকা পরিবর্তন করতে") {
                                                    container.hideFeaturedPlayer(player)
                                                    Toast.makeText(
                                                        context,
                                                        "${player.name} Featured লিস্ট থেকে রিমুভ হয়েছে",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .minimumInteractiveComponentSize()
                                            .padding(4.dp)
                                            .testTag("remove_featured_player_${player.id}")
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.62f))
                                                .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "রিমুভ ${player.name}",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    }
                                }

                                // Bottom White/Surface Name + 2x2 Stats Grid
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 9.dp)
                                ) {
                                    Text(
                                        text = player.name,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FeaturedStatInline(
                                            label = "Total Match:",
                                            value = "${player.matchesPlayed}",
                                            valueColor = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1.15f)
                                        )
                                        FeaturedStatInline(
                                            label = "NRR:",
                                            value = player.formattedNrr,
                                            valueColor = if (player.netRunRate >= 0) adaptiveEmerald else WicketRed,
                                            modifier = Modifier.weight(0.85f)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FeaturedStatInline(
                                            label = "Total Wk:",
                                            value = "${player.totalWickets}",
                                            valueColor = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1.15f)
                                        )
                                        FeaturedStatInline(
                                            label = "SR:",
                                            value = player.formattedStrikeRate,
                                            valueColor = adaptiveGold,
                                            modifier = Modifier.weight(0.85f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }

        // LATEST CRICKET FEED SECTION (Max 10 posts initially, scroll down to refresh & load more)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 4.dp, height = 22.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Brush.verticalGradient(listOf(adaptiveEmerald, adaptiveGold)))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "latest Cricket Feed",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = "${visiblePosts.size}/${filteredPosts.size} পোস্ট",
                    color = adaptiveGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            CommunityPostComposerCard(
                container = container,
                currentUserId = currentUserId,
                memberName = memberDisplayName,
                memberAvatarUrl = memberAvatarUrl,
                onMemberProfileUpdated = { _, _ -> },
                isGuest = isGuest,
                onRequireAuth = { actionName -> attemptAction(actionName) {} }
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("সব", "ছবি/ভিডিও", "আমার পোস্ট"), key = { it }) { filterLabel ->
                    val selected = postFilter == filterLabel
                    FilterChip(
                        selected = selected,
                        onClick = {
                            postFilter = filterLabel
                            visiblePostLimit = PostRepository.DEFAULT_FEED_PAGE_SIZE
                        },
                        label = {
                            Text(
                                text = filterLabel,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            selectedBorderColor = adaptiveGold
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (visiblePosts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "এখনো কোনো পোস্ট শেয়ার করা হয়নি",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "উপরের বক্সে আপনার লেখা, অনুভূতি, ছবি বা ভিডিও শেয়ার করে শুরু করুন!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        } else {
            items(visiblePosts, key = { "community_post_${it.id}" }) { post ->
                CommunityPostCard(
                    post = post,
                    currentUserId = currentUserId,
                    currentUserName = memberDisplayName,
                    currentUserAvatarUrl = memberAvatarUrl,
                    container = container,
                    onImageClick = { img -> fullScreenImageSource = img },
                    onVideoClick = { p -> activeVideoPost = p },
                    onAuthorClick = { authorId -> onNavigateToUserProfile(authorId) },
                    isGuest = isGuest,
                    onRequireAuth = { actionName -> attemptAction(actionName) {} },
                    isVideoAutoplayActive = post.id == activeAutoplayVideoPostId,
                    isVideoMuted = isFeedVideoMuted,
                    onToggleVideoMute = { isFeedVideoMuted = !isFeedVideoMuted }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Automatic scroll-based pagination indicator (non-clickable; loads next page on scroll)
            if (isRefreshingFeed || !hasMoreLocalOrCloud) {
                item(key = "feed_pagination_footer") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("feed_auto_pagination_status"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isRefreshingFeed) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = adaptiveEmerald
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "নতুন পোস্ট লোড হচ্ছে...",
                                color = adaptiveEmerald,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "সর্বশেষ সকল পোস্ট দেখানো হয়েছে",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }
    }

    if (showGuestDialog) {
        GuestRestrictedDialog(
            actionName = guestAttemptedAction,
            container = container,
            onDismiss = { showGuestDialog = false },
            onNavigateToAuth = {
                showGuestDialog = false
                onNavigateToAuth()
            }
        )
    }

    fullScreenImageSource?.let { imgSrc ->
        FullScreenPostImageDialog(
            imageSource = imgSrc,
            onDismiss = { fullScreenImageSource = null }
        )
    }

    activeVideoPost?.let { vidPost ->
        PostVideoPlayerDialog(
            post = vidPost,
            container = container,
            onDismiss = { activeVideoPost = null }
        )
    }

    LaunchedEffect(selectedFeaturedPlayer) {
        if (selectedFeaturedPlayer != null) {
            container.authRepository.ensureUsersDirectorySynced()
        }
    }

    selectedFeaturedPlayer?.let { featured ->
        val allUsers by container.authRepository.allUsersFlow.collectAsState()
        val matchedUser = remember(featured, allUsers) {
            allUsers.firstOrNull { u ->
                u.uid == featured.id ||
                    u.linkedPlayerId == featured.id ||
                    (featured.name.isNotBlank() && u.displayName.equals(featured.name, ignoreCase = true))
            }
        }
        FeaturedPlayerDetailDialog(
            player = featured,
            onDismiss = { selectedFeaturedPlayer = null },
            onViewAllPlayers = {
                selectedFeaturedPlayer = null
                onNavigateToMyCricket(3)
            },
            onOpenUserProfile = matchedUser?.let { u ->
                {
                    selectedFeaturedPlayer = null
                    onNavigateToUserProfile(u.uid)
                }
            },
            onRemoveFromFeatured = {
                container.hideFeaturedPlayer(featured)
                selectedFeaturedPlayer = null
                Toast.makeText(context, "${featured.name} Featured লিস্ট থেকে রিমুভ হয়েছে", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun FeaturedStatInline(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun FeaturedPlayerDetailDialog(
    player: FeaturedPlayerSummary,
    onDismiss: () -> Unit,
    onViewAllPlayers: () -> Unit,
    onOpenUserProfile: (() -> Unit)? = null,
    onRemoveFromFeatured: () -> Unit = {}
) {
    val emerald = adaptiveEmeraldColor()
    val gold = adaptiveGoldColor()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CricUpAvatar(
                    model = player.photoUrl,
                    size = 52.dp,
                    fallbackText = player.name.take(2),
                    borderColor = gold,
                    borderWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = player.name,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${player.role} • ${player.matchesPlayed} ম্যাচ",
                        color = emerald,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                FeaturedDetailRow("Total Match", "${player.matchesPlayed}")
                FeaturedDetailRow("Total Runs & Best", player.battingSpellSummary)
                FeaturedDetailRow("Total Wickets", "${player.totalWickets}")
                FeaturedDetailRow("Best Bowling Spell", player.bowlingSpellSummary)
                FeaturedDetailRow("Strike Rate (SR)", player.formattedStrikeRate)
                FeaturedDetailRow("ARPM (Avg Run/Match)", player.formattedArpm)
                FeaturedDetailRow("Net Run Rate (NRR)", player.formattedNrr)
                if (player.legalBallsBowled > 0) {
                    FeaturedDetailRow(
                        "Bowling Economy",
                        String.format(Locale.US, "%.2f", player.bowlingEconomy)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onRemoveFromFeatured,
                    modifier = Modifier
                        .align(Alignment.End)
                        .height(44.dp)
                        .testTag("dialog_remove_featured_player_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = WicketRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("লিস্ট থেকে রিমুভ করুন", color = WicketRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            if (onOpenUserProfile != null) {
                Button(
                    onClick = onOpenUserProfile,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("প্রোফাইল ও ফলো")
                }
            } else {
                Button(
                    onClick = onViewAllPlayers,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("সব খেলোয়াড়")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onOpenUserProfile != null) {
                    TextButton(onClick = onViewAllPlayers, modifier = Modifier.height(48.dp)) {
                        Text("সব খেলোয়াড়")
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                    Text("বন্ধ করুন")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun FeaturedDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QuickActionItem(
    title: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f))
                    .border(1.dp, tint.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
