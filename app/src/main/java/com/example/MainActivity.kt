package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import com.example.ui.theme.CricUpIcons
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.di.AppContainer
import com.example.ui.screens.history.CricketHistoryScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.intro.AppIntroScreen
import com.example.ui.screens.match.CreateMatchScreen
import com.example.ui.screens.match.MatchDetailScreen
import com.example.ui.screens.more.AboutScreen
import com.example.ui.screens.more.MoreScreen
import com.example.ui.screens.more.RankingsScreen
import com.example.ui.screens.mycricket.MyCricketScreen
import com.example.ui.screens.scoring.LiveScoringScreen
import com.example.ui.theme.EmeraldGreen
import com.example.util.SampleDataInitializer

enum class NavDestination {
    INTRO,
    HOME,
    MY_CRICKET,
    LIVE,
    HISTORY,
    MORE,
    MATCH_DETAIL,
    LIVE_SCORING,
    CREATE_MATCH,
    RANKINGS,
    ABOUT,
    USER_PROFILE
}

data class BottomNavItem(
    val title: String,
    val destination: NavDestination,
    val icon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as CsmBimphaApplication).container
        // Initialize full access session automatically for the user
        appContainer.authRepository.initializeFullAccessSession()
        val recoveredFromCrash = intent?.getBooleanExtra("EXTRA_CRASH_RECOVERED", false) ?: false

        setContent {
            val prefs = remember { getSharedPreferences("cricup_prefs", android.content.Context.MODE_PRIVATE) }
            val isDarkState = remember { mutableStateOf(prefs.getBoolean("pref_dark_mode", false)) }

            LaunchedEffect(isDarkState.value) {
                prefs.edit().putBoolean("pref_dark_mode", isDarkState.value).apply()
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkState.value) {
                        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        androidx.activity.SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (isDarkState.value) {
                        androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        androidx.activity.SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }

            androidx.compose.runtime.CompositionLocalProvider(com.example.ui.theme.LocalThemeIsDark provides isDarkState) {
                com.example.ui.theme.CricUpTheme(darkTheme = isDarkState.value) {
                    MainAppContent(container = appContainer, recoveredFromCrash = recoveredFromCrash)
                }
            }
        }
    }
}

@Composable
fun MainAppContent(
    container: AppContainer,
    recoveredFromCrash: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var currentDestinationName by rememberSaveable { mutableStateOf(NavDestination.INTRO.name) }
    val currentDestination = remember(currentDestinationName) {
        runCatching { NavDestination.valueOf(currentDestinationName) }.getOrDefault(NavDestination.INTRO)
    }
    var selectedMatchId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedUserProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var returnDestinationName by rememberSaveable { mutableStateOf(NavDestination.HOME.name) }
    var myCricketInitialTab by rememberSaveable { mutableIntStateOf(0) }
    val isGuest by container.authRepository.isGuestMode.collectAsState()
    var rootGatedAction by remember { mutableStateOf<String?>(null) }

    fun navigateTo(dest: NavDestination) {
        if (isGuest && (dest == NavDestination.CREATE_MATCH || dest == NavDestination.LIVE_SCORING)) {
            rootGatedAction = if (dest == NavDestination.CREATE_MATCH) {
                "নতুন ম্যাচ তৈরি ও স্কোরিং করতে"
            } else {
                "লাইভ স্কোরিং পরিচালনা করতে"
            }
            return
        }
        currentDestinationName = dest.name
    }

    fun openUserProfile(uid: String) {
        if (currentDestination != NavDestination.USER_PROFILE) {
            returnDestinationName = currentDestination.name
        }
        selectedUserProfileId = uid
        navigateTo(NavDestination.USER_PROFILE)
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            SampleDataInitializer.seedInitialDataIfEmpty(container, context)
            container.scoringRepository.syncAllPendingMatches()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch(Dispatchers.IO) {
                    container.scoringRepository.syncAllPendingMatches()
                    val pendingRes = container.authRepository.consumePendingFirebaseAuthResult()
                    val pendingUser = pendingRes.getOrNull()
                    if (pendingUser != null) {
                        withContext(Dispatchers.Main) {
                            val label = pendingUser.email.takeIf { it.isNotBlank() } ?: pendingUser.displayName
                            Toast.makeText(
                                context,
                                "Gmail ($label) লগইন সফল হয়েছে!",
                                Toast.LENGTH_LONG
                            ).show()
                            if (currentDestination == NavDestination.INTRO) {
                                container.authRepository.initializeFullAccessSession()
                                currentDestinationName = NavDestination.HOME.name
                            }
                        }
                    } else {
                        pendingRes.exceptionOrNull()?.let { err ->
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    container.authRepository.mapAuthErrorMessage(err),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(recoveredFromCrash) {
        if (recoveredFromCrash) {
            Toast.makeText(context, "CricUp: সুরক্ষিতভাবে পুনরুদ্ধার করা হয়েছে।", Toast.LENGTH_LONG).show()
        }
    }

    val bottomNavItems = listOf(
        BottomNavItem("HOME", NavDestination.HOME, Icons.Default.Home, "bottom_nav_home"),
        BottomNavItem("MY CRICKET", NavDestination.MY_CRICKET, CricUpIcons.SportsCricket, "bottom_nav_my_cricket"),
        BottomNavItem("LIVE", NavDestination.LIVE, CricUpIcons.Sensors, "bottom_nav_live"),
        BottomNavItem("HISTORY", NavDestination.HISTORY, CricUpIcons.History, "bottom_nav_history"),
        BottomNavItem("MORE", NavDestination.MORE, CricUpIcons.MoreHoriz, "bottom_nav_more")
    )

    val isTopLevelDestination = currentDestination in listOf(
        NavDestination.HOME,
        NavDestination.MY_CRICKET,
        NavDestination.LIVE,
        NavDestination.HISTORY,
        NavDestination.MORE
    )

    // Handle Android system back gesture
    BackHandler(enabled = currentDestination != NavDestination.INTRO && (!isTopLevelDestination || currentDestination != NavDestination.HOME)) {
        when {
            currentDestination == NavDestination.USER_PROFILE -> {
                val targetBack = runCatching { NavDestination.valueOf(returnDestinationName) }.getOrDefault(NavDestination.HOME)
                navigateTo(if (targetBack == NavDestination.USER_PROFILE) NavDestination.HOME else targetBack)
            }
            currentDestination == NavDestination.LIVE_SCORING && selectedMatchId != null -> {
                navigateTo(NavDestination.MATCH_DETAIL)
            }
            !isTopLevelDestination -> {
                navigateTo(NavDestination.HOME)
            }
            currentDestination != NavDestination.HOME -> {
                navigateTo(NavDestination.HOME)
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination == item.destination
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (item.destination == NavDestination.MY_CRICKET) {
                                    myCricketInitialTab = 0
                                }
                                navigateTo(item.destination)
                            },
                            icon = {
                                val activeTint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                val inactiveTint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = if (selected) activeTint else inactiveTint,
                                    modifier = Modifier.size(26.dp)
                                )
                            },
                            label = {
                                val activeTint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                val inactiveTint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                Text(
                                    text = item.title,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (selected) activeTint else inactiveTint
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        },
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            when (currentDestination) {
                NavDestination.INTRO -> {
                    AppIntroScreen(
                        container = container,
                        onStart = {
                            navigateTo(NavDestination.HOME)
                        }
                    )
                }

                NavDestination.HOME -> {
                    HomeScreen(
                        container = container,
                        onNavigateToMatch = { matchId ->
                            selectedMatchId = matchId
                            navigateTo(NavDestination.MATCH_DETAIL)
                        },
                        onNavigateToCreateMatch = { navigateTo(NavDestination.CREATE_MATCH) },
                        onNavigateToHistory = { navigateTo(NavDestination.HISTORY) },
                        onNavigateToMyCricket = { tab ->
                            myCricketInitialTab = tab
                            navigateTo(NavDestination.MY_CRICKET)
                        },
                        onNavigateToAuth = { rootGatedAction = "অ্যাপের সকল ফিচার ব্যবহার করতে" },
                        onNavigateToUserProfile = { uid -> openUserProfile(uid) }
                    )
                }

                NavDestination.MY_CRICKET -> {
                    MyCricketScreen(
                        initialTab = myCricketInitialTab,
                        container = container,
                        onNavigateToMatch = { matchId ->
                            selectedMatchId = matchId
                            navigateTo(NavDestination.MATCH_DETAIL)
                        },
                        onNavigateToCreateMatch = { navigateTo(NavDestination.CREATE_MATCH) },
                        onNavigateToAuth = { rootGatedAction = "অ্যাপের সকল ফিচার ব্যবহার করতে" }
                    )
                }

                NavDestination.LIVE -> {
                    com.example.ui.screens.live.PublicLiveMatchesScreen(
                        container = container,
                        onNavigateToMatch = { matchId ->
                            selectedMatchId = matchId
                            navigateTo(NavDestination.MATCH_DETAIL)
                        },
                        onNavigateToCreateMatch = { navigateTo(NavDestination.CREATE_MATCH) },
                        onNavigateToAuth = { rootGatedAction = "নতুন ম্যাচ তৈরি ও স্কোরিং করতে" }
                    )
                }

                NavDestination.HISTORY -> {
                    CricketHistoryScreen(
                        container = container,
                        onNavigateToMatch = { matchId ->
                            selectedMatchId = matchId
                            navigateTo(NavDestination.MATCH_DETAIL)
                        }
                    )
                }

                NavDestination.MORE -> {
                    MoreScreen(
                        container = container,
                        onNavigateToRankings = { navigateTo(NavDestination.RANKINGS) },
                        onNavigateToAbout = { navigateTo(NavDestination.ABOUT) },
                        onNavigateToUserProfile = { uid -> openUserProfile(uid) }
                    )
                }

                NavDestination.MATCH_DETAIL -> {
                    selectedMatchId?.let { matchId ->
                        MatchDetailScreen(
                            matchId = matchId,
                            container = container,
                            onBack = { navigateTo(NavDestination.HOME) },
                            onNavigateToScoring = { scMatchId ->
                                if (isGuest) {
                                    rootGatedAction = "লাইভ স্কোরিং পরিচালনা করতে"
                                } else {
                                    selectedMatchId = scMatchId
                                    navigateTo(NavDestination.LIVE_SCORING)
                                }
                            }
                        )
                    }
                }

                NavDestination.LIVE_SCORING -> {
                    if (isGuest) {
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            rootGatedAction = "লাইভ স্কোরিং পরিচালনা করতে"
                            currentDestinationName = NavDestination.MATCH_DETAIL.name
                        }
                    } else {
                        selectedMatchId?.let { matchId ->
                            LiveScoringScreen(
                                matchId = matchId,
                                container = container,
                                onBack = { navigateTo(NavDestination.MATCH_DETAIL) }
                            )
                        }
                    }
                }

                NavDestination.CREATE_MATCH -> {
                    if (isGuest) {
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            rootGatedAction = "নতুন ম্যাচ তৈরি ও স্কোরিং করতে"
                            currentDestinationName = NavDestination.HOME.name
                        }
                    } else {
                        CreateMatchScreen(
                            container = container,
                            onBack = { navigateTo(NavDestination.HOME) },
                            onMatchCreated = { matchId ->
                                selectedMatchId = matchId
                                navigateTo(NavDestination.LIVE_SCORING)
                            }
                        )
                    }
                }

                NavDestination.RANKINGS -> {
                    RankingsScreen(
                        container = container,
                        onBack = { navigateTo(NavDestination.MORE) }
                    )
                }

                NavDestination.ABOUT -> {
                    AboutScreen(onBack = { navigateTo(NavDestination.MORE) })
                }

                NavDestination.USER_PROFILE -> {
                    com.example.ui.screens.more.UserProfileScreen(
                        targetUserId = selectedUserProfileId ?: "",
                        container = container,
                        onBack = {
                            val targetBack = runCatching { NavDestination.valueOf(returnDestinationName) }.getOrDefault(NavDestination.HOME)
                            navigateTo(if (targetBack == NavDestination.USER_PROFILE) NavDestination.HOME else targetBack)
                        },
                        onSelectUser = { uid ->
                            selectedUserProfileId = uid
                        }
                    )
                }
            }
        }
    }

    rootGatedAction?.let { actionLabel ->
        com.example.ui.components.GuestRestrictedDialog(
            actionName = actionLabel,
            container = container,
            onDismiss = { rootGatedAction = null }
        )
    }
}
