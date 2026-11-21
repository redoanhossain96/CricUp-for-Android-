package com.example.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.data.local.AppDatabase
import com.example.data.repository.AuthRepository
import com.example.data.repository.MatchRepository
import com.example.data.repository.PlayerRepository
import com.example.data.repository.ScoringRepository
import com.example.data.repository.TeamRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val prefs by lazy {
        appContext.getSharedPreferences("cricup_prefs", Context.MODE_PRIVATE)
    }
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t ->
        android.util.Log.w("AppContainer", "Background sync skipped: ${t.message}")
    })

    val database = AppDatabase.getInstance(appContext)

    val authRepository by lazy {
        AuthRepository(appContext).apply {
            onProfileSynced = { displayName, photoUrl, explicitlyCleared ->
                postRepository.syncProfileFromAuth(
                    name = displayName,
                    photoUrl = photoUrl,
                    explicitlyCleared = explicitlyCleared,
                    currentUserId = getCurrentUserId()
                )
            }
            onUserCricketProfileUpdated = { user ->
                syncScope.launch {
                    playerRepository.syncUserAsPlayerProfile(user)
                }
            }
            onAccountSwitching = { previousGoogleUid, _ ->
                teamRepository.stopSync()
                playerRepository.stopSync()
                tournamentRepository.stopSync()
                umpireRepository.stopSync()
                matchRepository.stopUserMatchesSync()
                teamRepository.clearAllTeams()
                playerRepository.clearAllPlayers()
                tournamentRepository.clearAllTournaments()
                umpireRepository.clearAllUmpires()
                matchRepository.clearUserOwnedLocalMatches(previousGoogleUid)
                restoreAllHiddenFeaturedPlayers()
            }
            onAccountLinked = { previousUid, linkedUser ->
                syncScope.launch {
                    runCatching {
                        matchRepository.migrateOwnerUid(previousUid, linkedUser.uid, linkedUser.displayName)
                        teamRepository.migrateOwnerUid(previousUid, linkedUser.uid)
                        playerRepository.migrateCreatorUid(previousUid, linkedUser.uid)
                        tournamentRepository.migrateCreatorUid(previousUid, linkedUser.uid)
                        umpireRepository.migrateCreatorUid(previousUid, linkedUser.uid)
                        postRepository.migrateAuthorId(
                            oldUid = previousUid,
                            newUid = linkedUser.uid,
                            authorName = linkedUser.displayName,
                            authorPhoto = linkedUser.photoUrl
                        )
                        playerRepository.syncUserAsPlayerProfile(linkedUser)
                        teamRepository.syncTeamsForUser(linkedUser.uid)
                        playerRepository.syncPlayersForUser(linkedUser.uid)
                        tournamentRepository.syncTournamentsForUser(linkedUser.uid)
                        umpireRepository.syncUmpiresForUser(linkedUser.uid)
                        matchRepository.syncUserMatches(linkedUser.uid)
                    }
                }
            }
            onUserAuthenticated = { linkedUser ->
                teamRepository.syncTeamsForUser(linkedUser.uid)
                playerRepository.syncPlayersForUser(linkedUser.uid)
                tournamentRepository.syncTournamentsForUser(linkedUser.uid)
                umpireRepository.syncUmpiresForUser(linkedUser.uid)
                matchRepository.syncUserMatches(linkedUser.uid)
            }
            onSignedOutCleanup = { previousUid ->
                teamRepository.stopSync()
                playerRepository.stopSync()
                tournamentRepository.stopSync()
                umpireRepository.stopSync()
                matchRepository.stopUserMatchesSync()
                restoreAllHiddenFeaturedPlayers()
                syncScope.launch {
                    runCatching {
                        teamRepository.clearAllTeams()
                        playerRepository.clearAllPlayers()
                        tournamentRepository.clearAllTournaments()
                        umpireRepository.clearAllUmpires()
                        matchRepository.clearUserOwnedLocalMatches(previousUid)
                    }
                }
            }
            val initialUser = (getCurrentUserFlow() as? kotlinx.coroutines.flow.StateFlow<com.example.data.model.User?>)?.value
            if (initialUser != null && isGoogleSignedIn()) {
                teamRepository.syncTeamsForUser(initialUser.uid)
                playerRepository.syncPlayersForUser(initialUser.uid)
                tournamentRepository.syncTournamentsForUser(initialUser.uid)
                umpireRepository.syncUmpiresForUser(initialUser.uid)
            }
            if (initialUser != null && (!AuthRepository.isPlaceholderProfileName(initialUser.displayName) || !initialUser.photoUrl.isNullOrBlank())) {
                postRepository.syncProfileFromAuth(
                    name = initialUser.displayName,
                    photoUrl = initialUser.photoUrl,
                    explicitlyCleared = false,
                    currentUserId = initialUser.uid
                )
                syncScope.launch {
                    playerRepository.syncUserAsPlayerProfile(initialUser)
                }
            }
        }
    }
    val teamRepository by lazy { TeamRepository(database.teamDao()) }
    val playerRepository by lazy { PlayerRepository(database.playerDao()) }
    val matchRepository by lazy { MatchRepository(database.matchDao(), database.ballEventDao()) }
    val scoringRepository by lazy {
        ScoringRepository(database.ballEventDao(), database.matchDao(), matchRepository)
    }
    val tournamentRepository by lazy {
        com.example.data.repository.TournamentRepository(database.tournamentDao())
    }
    val umpireRepository by lazy {
        com.example.data.repository.UmpireRepository(database.umpireDao())
    }
    val postRepository by lazy {
        com.example.data.repository.PostRepository(appContext)
    }
    val predictionRepository by lazy {
        com.example.data.repository.PredictionRepository(
            context = appContext,
            authRepository = authRepository
        )
    }

    private val _hiddenFeaturedPlayerKeysFlow by lazy {
        val saved = prefs.getStringSet("hidden_featured_player_keys", emptySet()).orEmpty().toSet()
        MutableStateFlow(saved)
    }
    val hiddenFeaturedPlayerKeysFlow: StateFlow<Set<String>>
        get() = _hiddenFeaturedPlayerKeysFlow.asStateFlow()

    fun hideFeaturedPlayer(player: com.example.util.FeaturedPlayerSummary) {
        val token = com.example.util.FeaturedPlayersAggregator.exclusionToken(player.id, player.name)
        val updated = (_hiddenFeaturedPlayerKeysFlow.value + token).toSet()
        _hiddenFeaturedPlayerKeysFlow.value = updated
        prefs.edit().putStringSet("hidden_featured_player_keys", updated).apply()
    }

    fun restoreAllHiddenFeaturedPlayers() {
        _hiddenFeaturedPlayerKeysFlow.value = emptySet()
        prefs.edit().remove("hidden_featured_player_keys").apply()
    }

    suspend fun toggleUserTeamMembership(teamId: String, join: Boolean): Result<Boolean> {
        val userRes = authRepository.updateUserJoinedTeams(teamId, join)
        val updatedUser = userRes.getOrElse { return Result.failure(it) }
        val player = playerRepository.syncUserAsPlayerProfile(updatedUser)
        playerRepository.setPlayerTeamMembership(player.id, teamId, join)
        if (join) {
            teamRepository.joinTeamAsPlayer(teamId, player.id)
        } else {
            teamRepository.leaveTeamAsPlayer(teamId, player.id)
        }
        return Result.success(join)
    }

    init {
        registerNetworkSyncCallback()
    }

    private fun registerNetworkSyncCallback() {
        try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val caps = cm.getNetworkCapabilities(network)
                    val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != false
                    if (hasInternet) {
                        syncScope.launch {
                            scoringRepository.syncAllPendingMatches()
                            if (authRepository.isGoogleSignedIn()) {
                                val uid = authRepository.getCurrentUserId()
                                teamRepository.syncPendingToCloud(uid)
                                playerRepository.syncPendingToCloud(uid)
                                tournamentRepository.syncPendingToCloud(uid)
                                umpireRepository.syncPendingToCloud(uid)
                            }
                        }
                    }
                }
            })
        } catch (e: Exception) {
            android.util.Log.w("AppContainer", "NetworkCallback registration skipped: ${e.message}")
        }
    }
}
