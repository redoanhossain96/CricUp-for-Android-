package com.example.data.repository

import com.example.data.model.PlayerRoles
import com.example.data.model.User
import com.example.data.service.EmailOtpService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean

class AuthRepository(
    private val context: android.content.Context? = null,
    private val auth: FirebaseAuth? = try { FirebaseAuth.getInstance() } catch (_: Exception) { null },
    private val firestore: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (_: Exception) { null },
    val emailOtpService: EmailOtpService? = try { EmailOtpService(firestore ?: FirebaseFirestore.getInstance()) } catch (_: Exception) { null },
    private val enableCloudSync: Boolean = firestore != null
) {
    companion object {
        private const val PROFILE_PREFS = "cricup_member_profile"
        private const val KEY_DISPLAY_NAME = "member_display_name"
        private const val KEY_PHOTO_URL = "member_photo_url"
        private const val KEY_PHOTO_CLEARED = "member_photo_explicitly_cleared"
        private const val KEY_PRIMARY_ROLE = "member_primary_role"
        private const val KEY_BATTING_STYLE = "member_batting_style"
        private const val KEY_BOWLING_STYLE = "member_bowling_style"
        private const val KEY_BIO = "member_bio"
        private const val KEY_JOINED_TEAMS = "member_joined_teams"
        private const val KEY_FOLLOWERS = "member_followers"
        private const val KEY_FOLLOWING = "member_following"
        private const val KEY_GOOGLE_UID = "google_linked_uid"
        private const val KEY_LAST_GOOGLE_UID = "last_signed_in_google_uid"
        private const val KEY_GOOGLE_EMAIL = "google_linked_email"
        private const val KEY_GOOGLE_NAME = "google_linked_name"
        private const val KEY_GOOGLE_PHOTO = "google_linked_photo"
        private const val KEY_DEVICE_BACKUP_NAME = "device_backup_name"
        private const val KEY_DEVICE_BACKUP_PHOTO = "device_backup_photo"
        private const val KEY_PREDICTION_POINTS = "member_prediction_points"
        private const val KEY_PREDICTIONS_WON = "member_predictions_won"
        private const val KEY_PREDICTIONS_TOTAL = "member_predictions_total"
        private const val SEARCHED_SCORER_PREFS = "cricup_searched_scorer_prefs"
        private const val KEY_SEARCHED_SCORERS_PREFIX = "searched_scorers_"
        private const val MAX_SEARCHED_SCORER_HISTORY = 30
        private val PLACEHOLDER_NAME_REGEX = Regex("^User[ _-][A-Za-z0-9]{3,8}$", RegexOption.IGNORE_CASE)

        fun isPlaceholderProfileName(name: String?): Boolean {
            val clean = name?.trim().orEmpty()
            if (clean.isBlank()) return true
            if (clean.equals("Guest", ignoreCase = true) || clean.equals("Member", ignoreCase = true)) return true
            return PLACEHOLDER_NAME_REGEX.matches(clean)
        }
    }

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, t ->
        android.util.Log.w("AuthRepository", "Profile sync warning: ${t.message}")
    })
    private val _isGuestMode = MutableStateFlow(false)
    val isGuestMode: StateFlow<Boolean> = _isGuestMode.asStateFlow()
    private val profileRecoveryAttempted = AtomicBoolean(false)
    private var userDocListener: ListenerRegistration? = null
    private var activeSyncedUid: String? = null
    private var allUsersListener: ListenerRegistration? = null
    @Volatile
    private var signedInGoogleUid: String? = null
    @Volatile
    var onProfileSynced: ((displayName: String, photoUrl: String?, explicitlyCleared: Boolean) -> Unit)? = null
    @Volatile
    var onUserCricketProfileUpdated: ((User) -> Unit)? = null
    @Volatile
    var onAccountLinked: (suspend (previousUid: String, linkedUser: User) -> Unit)? = null
    @Volatile
    var onAccountSwitching: (suspend (previousGoogleUid: String, nextGoogleUid: String) -> Unit)? = null
    @Volatile
    var onUserAuthenticated: ((User) -> Unit)? = null
    @Volatile
    var onSignedOutCleanup: ((previousUid: String) -> Unit)? = null

    val permanentDeviceId: String by lazy {
        var id: String? = null
        if (context != null) {
            val prefs = context.getSharedPreferences("cricup_device_pref", android.content.Context.MODE_PRIVATE)
            id = prefs.getString("device_uid", null)
            if (id.isNullOrBlank()) {
                val androidId = try {
                    android.provider.Settings.Secure.getString(
                        context.contentResolver,
                        android.provider.Settings.Secure.ANDROID_ID
                    )
                } catch (_: Exception) { null }
                id = if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
                    "dev_$androidId"
                } else {
                    "dev_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
                }
                prefs.edit().putString("device_uid", id).apply()
            }
        }
        if (id.isNullOrBlank()) {
            "dev_" + (auth?.currentUser?.uid ?: "default_device")
        } else {
            id
        }
    }

    private fun profilePrefs(): android.content.SharedPreferences? =
        context?.getSharedPreferences(PROFILE_PREFS, android.content.Context.MODE_PRIVATE)

    private fun readSavedProfileName(): String? {
        return profilePrefs()?.getString(KEY_DISPLAY_NAME, null)
            ?.trim()
            ?.takeUnless { isPlaceholderProfileName(it) }
    }

    private fun readSavedProfilePhoto(): String? {
        return profilePrefs()?.getString(KEY_PHOTO_URL, null)?.takeIf { it.isNotBlank() }
    }

    private fun isPhotoExplicitlyCleared(): Boolean {
        return profilePrefs()?.getBoolean(KEY_PHOTO_CLEARED, false) == true
    }

    private fun persistProfileLocally(
        displayName: String?,
        photoUrl: String?,
        explicitlyCleared: Boolean = false
    ) {
        val prefs = profilePrefs() ?: return
        prefs.edit().apply {
            val cleanName = displayName?.trim().orEmpty()
            if (!isPlaceholderProfileName(cleanName)) {
                putString(KEY_DISPLAY_NAME, cleanName)
            }
            if (explicitlyCleared) {
                remove(KEY_PHOTO_URL)
                putBoolean(KEY_PHOTO_CLEARED, true)
            } else if (!photoUrl.isNullOrBlank()) {
                putString(KEY_PHOTO_URL, photoUrl)
                putBoolean(KEY_PHOTO_CLEARED, false)
            }
        }.apply()
    }

    private fun readSavedRole(): String =
        profilePrefs()?.getString(KEY_PRIMARY_ROLE, null)?.takeIf { it.isNotBlank() }
            ?: com.example.data.model.PlayerRoles.ALL_ROUNDER

    private fun readSavedBattingStyle(): String =
        profilePrefs()?.getString(KEY_BATTING_STYLE, null)?.takeIf { it.isNotBlank() } ?: "Right Hand"

    private fun readSavedBowlingStyle(): String =
        profilePrefs()?.getString(KEY_BOWLING_STYLE, null)?.takeIf { it.isNotBlank() } ?: "Right-arm Medium"

    private fun readSavedBio(): String =
        profilePrefs()?.getString(KEY_BIO, null).orEmpty()

    private fun readSavedIdSet(key: String): List<String> =
        profilePrefs()?.getStringSet(key, emptySet())?.filter { it.isNotBlank() }?.sorted() ?: emptyList()

    private fun readSavedPredictionPoints(): Int =
        profilePrefs()?.getInt(KEY_PREDICTION_POINTS, 0)?.coerceAtLeast(0) ?: 0

    private fun readSavedPredictionsWon(): Int =
        profilePrefs()?.getInt(KEY_PREDICTIONS_WON, 0)?.coerceAtLeast(0) ?: 0

    private fun readSavedPredictionsTotal(): Int =
        profilePrefs()?.getInt(KEY_PREDICTIONS_TOTAL, 0)?.coerceAtLeast(0) ?: 0

    private fun persistPredictionStatsLocally(points: Int, won: Int, total: Int) {
        profilePrefs()?.edit()?.apply {
            putInt(KEY_PREDICTION_POINTS, points.coerceAtLeast(0))
            putInt(KEY_PREDICTIONS_WON, won.coerceAtLeast(0))
            putInt(KEY_PREDICTIONS_TOTAL, total.coerceAtLeast(0))
        }?.apply()
    }

    private fun persistCricketDetailsLocally(
        primaryRole: String? = null,
        battingStyle: String? = null,
        bowlingStyle: String? = null,
        bio: String? = null,
        joinedTeamIds: List<String>? = null,
        followerIds: List<String>? = null,
        followingIds: List<String>? = null
    ) {
        val prefs = profilePrefs() ?: return
        prefs.edit().apply {
            primaryRole?.takeIf { it.isNotBlank() }?.let { putString(KEY_PRIMARY_ROLE, it) }
            battingStyle?.takeIf { it.isNotBlank() }?.let { putString(KEY_BATTING_STYLE, it) }
            bowlingStyle?.takeIf { it.isNotBlank() }?.let { putString(KEY_BOWLING_STYLE, it) }
            bio?.let { putString(KEY_BIO, it) }
            joinedTeamIds?.let { putStringSet(KEY_JOINED_TEAMS, it.toSet()) }
            followerIds?.let { putStringSet(KEY_FOLLOWERS, it.toSet()) }
            followingIds?.let { putStringSet(KEY_FOLLOWING, it.toSet()) }
        }.apply()
    }

    private fun readSavedGoogleUid(): String? =
        profilePrefs()?.getString(KEY_GOOGLE_UID, null)?.takeIf { it.isNotBlank() }

    private fun readSavedGoogleEmail(): String =
        profilePrefs()?.getString(KEY_GOOGLE_EMAIL, null).orEmpty()

    private fun readSavedGoogleName(): String? =
        profilePrefs()?.getString(KEY_GOOGLE_NAME, null)?.takeUnless { isPlaceholderProfileName(it) }

    private fun readSavedGooglePhoto(): String? =
        profilePrefs()?.getString(KEY_GOOGLE_PHOTO, null)?.takeIf { it.isNotBlank() }

    private fun persistGoogleSessionLocally(uid: String, email: String, name: String?, photoUrl: String?) {
        profilePrefs()?.edit()?.apply {
            putString(KEY_GOOGLE_UID, uid)
            putString(KEY_LAST_GOOGLE_UID, uid)
            putString(KEY_GOOGLE_EMAIL, email)
            if (!name.isNullOrBlank() && !isPlaceholderProfileName(name)) putString(KEY_GOOGLE_NAME, name)
            if (!photoUrl.isNullOrBlank()) putString(KEY_GOOGLE_PHOTO, photoUrl)
        }?.apply()
    }

    private fun clearGoogleSessionLocally() {
        profilePrefs()?.edit()?.apply {
            remove(KEY_GOOGLE_UID)
            remove(KEY_GOOGLE_EMAIL)
            remove(KEY_GOOGLE_NAME)
            remove(KEY_GOOGLE_PHOTO)
        }?.apply()
    }

    private fun resolvedActiveGoogleUid(): String? {
        val firebaseUid = try { auth?.currentUser?.uid } catch (_: Exception) { null }
        if (!firebaseUid.isNullOrBlank()) return firebaseUid
        val inMemory = signedInGoogleUid?.takeIf { it.isNotBlank() }
        if (inMemory != null) return inMemory
        return if (auth == null) readSavedGoogleUid() else null
    }

    private fun defaultPlaceholderName(uid: String = getCurrentUserId()): String =
        "User ${uid.takeLast(4).uppercase()}"

    private fun buildInitialLocalUser(): User {
        val devUid = permanentDeviceId
        val googleUid = resolvedActiveGoogleUid()
        val activeUid = googleUid ?: devUid
        val isGoogle = !googleUid.isNullOrBlank()
        val savedName = readSavedProfileName() ?: if (isGoogle) readSavedGoogleName() else null
        val savedPhoto = when {
            isPhotoExplicitlyCleared() -> null
            else -> readSavedProfilePhoto() ?: if (isGoogle) readSavedGooglePhoto() else null
        }
        val activeEmail = if (isGoogle) {
            try { auth?.currentUser?.email?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }
                ?: readSavedGoogleEmail()
        } else {
            ""
        }
        val now = System.currentTimeMillis()
        return User(
            uid = activeUid,
            email = activeEmail,
            displayName = savedName ?: defaultPlaceholderName(activeUid),
            role = User.ROLE_MEMBER,
            photoUrl = savedPhoto,
            primaryRole = readSavedRole(),
            battingStyle = readSavedBattingStyle(),
            bowlingStyle = readSavedBowlingStyle(),
            bio = readSavedBio(),
            linkedPlayerId = activeUid,
            joinedTeamIds = readSavedIdSet(KEY_JOINED_TEAMS),
            followerIds = readSavedIdSet(KEY_FOLLOWERS),
            followingIds = readSavedIdSet(KEY_FOLLOWING),
            predictionPoints = readSavedPredictionPoints(),
            predictionsWon = readSavedPredictionsWon(),
            predictionsTotal = readSavedPredictionsTotal(),
            deviceId = devUid,
            linkedDeviceIds = listOf(devUid),
            provider = if (isGoogle) "google" else "device_identity",
            createdAt = now,
            lastLoginAt = now
        )
    }

    private val _currentUserFlow = MutableStateFlow<User?>(buildInitialLocalUser())
    private val currentUserStateFlow: StateFlow<User?> = _currentUserFlow.asStateFlow()
    private val _allUsersFlow = MutableStateFlow<List<User>>(listOfNotNull(_currentUserFlow.value))
    val allUsersFlow: StateFlow<List<User>> = _allUsersFlow.asStateFlow()

    init {
        signedInGoogleUid = resolvedActiveGoogleUid()
        _isGuestMode.value = !isGoogleSignedIn()
        if (enableCloudSync && firestore != null) {
            startUserProfileSync()
        }
    }

    private fun upsertUserInDirectory(user: User) {
        if (user.uid.isBlank()) return
        val currentList = _allUsersFlow.value.toMutableList()
        val idx = currentList.indexOfFirst { it.uid == user.uid }
        if (idx >= 0) {
            currentList[idx] = user
        } else {
            currentList.add(user)
        }
        _allUsersFlow.value = currentList
    }

    fun seedDirectoryUsers(users: List<User>) {
        users.forEach { upsertUserInDirectory(it) }
    }

    private fun applyResolvedUserProfile(incoming: User?, persistToPrefs: Boolean = true) {
        if (incoming == null) return
        val current = _currentUserFlow.value
        val activeUid = getCurrentUserId()
        val photoCleared = isPhotoExplicitlyCleared()

        val incomingName = incoming.displayName.trim()
        val currentName = current?.displayName?.trim().orEmpty()
        val resolvedName = when {
            !isPlaceholderProfileName(incomingName) -> incomingName
            !isPlaceholderProfileName(currentName) -> currentName
            else -> readSavedProfileName() ?: incomingName.ifBlank { defaultPlaceholderName(activeUid) }
        }

        val incomingPhoto = incoming.photoUrl?.takeIf { it.isNotBlank() }
        val currentPhoto = current?.photoUrl?.takeIf { it.isNotBlank() }
        val savedPhoto = readSavedProfilePhoto()
        val resolvedPhoto = when {
            incomingPhoto != null -> incomingPhoto
            photoCleared -> null
            currentPhoto != null -> currentPhoto
            else -> savedPhoto
        }

        val resolvedRole = incoming.primaryRole.ifBlank { current?.primaryRole ?: readSavedRole() }
        val resolvedBat = incoming.battingStyle.ifBlank { current?.battingStyle ?: readSavedBattingStyle() }
        val resolvedBowl = incoming.bowlingStyle.ifBlank { current?.bowlingStyle ?: readSavedBowlingStyle() }
        val resolvedBio = incoming.bio.ifBlank { current?.bio ?: readSavedBio() }
        val resolvedTeams = (incoming.joinedTeamIds + current?.joinedTeamIds.orEmpty()).distinct()
        val resolvedFollowers = if (incoming.followerIds.isNotEmpty() || current == null) {
            incoming.followerIds
        } else {
            current.followerIds
        }
        val resolvedFollowing = (incoming.followingIds + current?.followingIds.orEmpty()).distinct()
        val resolvedLinkedDevices = (incoming.linkedDeviceIds + current?.linkedDeviceIds.orEmpty() + permanentDeviceId)
            .filter { it.isNotBlank() }
            .distinct()
        val resolvedEmail = incoming.email.ifBlank { current?.email.orEmpty() }
        val resolvedProvider = if (isGoogleSignedIn()) "google" else incoming.provider.ifBlank { "device_identity" }
        val resolvedPredPoints = maxOf(incoming.predictionPoints, current?.predictionPoints ?: 0, readSavedPredictionPoints())
        val resolvedPredWon = maxOf(incoming.predictionsWon, current?.predictionsWon ?: 0, readSavedPredictionsWon())
        val resolvedPredTotal = maxOf(incoming.predictionsTotal, current?.predictionsTotal ?: 0, readSavedPredictionsTotal())

        val merged = incoming.copy(
            uid = activeUid,
            email = resolvedEmail,
            deviceId = permanentDeviceId,
            linkedDeviceIds = resolvedLinkedDevices,
            provider = resolvedProvider,
            displayName = resolvedName,
            photoUrl = resolvedPhoto,
            primaryRole = resolvedRole,
            battingStyle = resolvedBat,
            bowlingStyle = resolvedBowl,
            bio = resolvedBio,
            linkedPlayerId = incoming.linkedPlayerId.ifBlank { activeUid },
            joinedTeamIds = resolvedTeams,
            followerIds = resolvedFollowers,
            followingIds = resolvedFollowing,
            predictionPoints = resolvedPredPoints,
            predictionsWon = resolvedPredWon,
            predictionsTotal = resolvedPredTotal
        )
        _currentUserFlow.value = merged
        upsertUserInDirectory(merged)

        if (persistToPrefs) {
            if (!isPlaceholderProfileName(resolvedName) || resolvedPhoto != null) {
                persistProfileLocally(resolvedName, resolvedPhoto, explicitlyCleared = false)
            }
            persistCricketDetailsLocally(
                primaryRole = resolvedRole,
                battingStyle = resolvedBat,
                bowlingStyle = resolvedBowl,
                bio = resolvedBio,
                joinedTeamIds = resolvedTeams,
                followerIds = resolvedFollowers,
                followingIds = resolvedFollowing
            )
            persistPredictionStatsLocally(resolvedPredPoints, resolvedPredWon, resolvedPredTotal)
            if (isGoogleSignedIn()) {
                persistGoogleSessionLocally(activeUid, resolvedEmail, resolvedName, resolvedPhoto)
            }
        }
        if (!isPlaceholderProfileName(resolvedName) || resolvedPhoto != null) {
            onProfileSynced?.invoke(resolvedName, resolvedPhoto, false)
        }
        onUserCricketProfileUpdated?.invoke(merged)
    }

    private fun startUserProfileSync(forceRestart: Boolean = false) {
        val fs = firestore ?: return
        val uid = getCurrentUserId()
        if (!forceRestart && activeSyncedUid == uid && userDocListener != null) return
        activeSyncedUid = uid
        userDocListener?.remove()
        userDocListener = fs.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("AuthRepository", "User snapshot error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    applyResolvedUserProfile(user, persistToPrefs = true)
                    val hasRealName = !isPlaceholderProfileName(user?.displayName)
                    val hasPhoto = !user?.photoUrl.isNullOrBlank() || isPhotoExplicitlyCleared()
                    if (!hasRealName || !hasPhoto) {
                        triggerProfileRecoveryIfNeeded()
                    }
                } else {
                    triggerProfileRecoveryIfNeeded()
                }
            }
    }

    /**
     * Attaches the shared 250-user directory snapshot listener lazily on demand
     * (e.g., when opening the member directory or inspecting another member's profile)
     * instead of eagerly fetching 250 user documents with photos on every cold launch.
     */
    fun ensureUsersDirectorySynced() {
        val fs = firestore ?: return
        if (!enableCloudSync || allUsersListener != null) return
        allUsersListener = fs.collection("users").limit(250)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val cloudUsers = snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(User::class.java)?.let { u ->
                            if (u.uid.isBlank()) u.copy(uid = doc.id) else u
                        }
                    } catch (_: Exception) { null }
                }
                val selfUid = getCurrentUserId()
                val selfUser = _currentUserFlow.value
                val mergedList = buildList {
                    if (selfUser != null) add(selfUser)
                    cloudUsers.filter { it.uid != selfUid }.forEach { add(it) }
                }.distinctBy { it.uid }
                if (mergedList != _allUsersFlow.value) {
                    _allUsersFlow.value = mergedList
                }
            }
    }

    private fun triggerProfileRecoveryIfNeeded() {
        if (!enableCloudSync || firestore == null) return
        if (!profileRecoveryAttempted.compareAndSet(false, true)) return
        repoScope.launch {
            recoverExistingProfileFromDatabase()
        }
    }

    /**
     * Searches existing Firestore records associated with this user/device
     * (users/{authUid}, users by email, users by deviceId, and authored posts/matches)
     * so an existing user name or photo is never lost after app updates or data resets.
     */
    private suspend fun recoverExistingProfileFromDatabase() {
        val fs = firestore ?: return
        val uid = getCurrentUserId()
        var recoveredName: String? = readSavedProfileName()?.takeUnless { isPlaceholderProfileName(it) }
        var recoveredPhoto: String? = if (isPhotoExplicitlyCleared()) null else readSavedProfilePhoto()
        var recoveredEmail: String = try { auth?.currentUser?.email.orEmpty() } catch (_: Exception) { "" }
            .ifBlank { if (isGoogleSignedIn()) readSavedGoogleEmail() else "" }

        fun adoptCandidate(name: String?, photo: String?, email: String? = null) {
            val cleanName = name?.trim()
            if (recoveredName == null && !isPlaceholderProfileName(cleanName)) {
                recoveredName = cleanName
            }
            if (recoveredPhoto == null && !isPhotoExplicitlyCleared() && !photo.isNullOrBlank()) {
                recoveredPhoto = photo
            }
            if (recoveredEmail.isBlank() && !email.isNullOrBlank()) {
                recoveredEmail = email
            }
        }

        try {
            // 1. Check primary doc directly (in case listener hasn't emitted yet)
            val primarySnap = fs.collection("users").document(uid).get().await()
            if (primarySnap.exists()) {
                val u = primarySnap.toObject(User::class.java)
                adoptCandidate(u?.displayName, u?.photoUrl, u?.email)
            }

            // 2. Check legacy FirebaseAuth UID doc if user previously logged in under FirebaseAuth UID
            val authUid = try { auth?.currentUser?.uid } catch (_: Exception) { null }
            if ((recoveredName == null || recoveredPhoto == null) && !authUid.isNullOrBlank() && authUid != uid) {
                try {
                    val authSnap = fs.collection("users").document(authUid).get().await()
                    if (authSnap.exists()) {
                        val u = authSnap.toObject(User::class.java)
                        adoptCandidate(u?.displayName, u?.photoUrl, u?.email)
                    }
                } catch (_: Exception) {}
            }

            // 3. Check users by email if available
            if ((recoveredName == null || recoveredPhoto == null) && recoveredEmail.isNotBlank()) {
                try {
                    val emailSnaps = fs.collection("users")
                        .whereEqualTo("email", recoveredEmail.trim().lowercase())
                        .limit(5)
                        .get()
                        .await()
                    for (doc in emailSnaps.documents) {
                        val u = doc.toObject(User::class.java)
                        adoptCandidate(u?.displayName, u?.photoUrl, u?.email)
                    }
                } catch (_: Exception) {}
            }

            // 4. Check users where deviceId == permanentDeviceId
            if (recoveredName == null || recoveredPhoto == null) {
                try {
                    val devSnaps = fs.collection("users")
                        .whereEqualTo("deviceId", uid)
                        .limit(5)
                        .get()
                        .await()
                    for (doc in devSnaps.documents) {
                        val u = doc.toObject(User::class.java)
                        adoptCandidate(u?.displayName, u?.photoUrl, u?.email)
                    }
                } catch (_: Exception) {}
            }

            // 5. Check community posts authored by this user/device
            if (recoveredName == null || recoveredPhoto == null) {
                try {
                    val postSnaps = fs.collection("posts")
                        .whereEqualTo("authorId", uid)
                        .limit(10)
                        .get()
                        .await()
                    val sortedPosts = postSnaps.documents
                        .mapNotNull { doc ->
                            val ts = doc.getLong("createdAt") ?: 0L
                            val aName = doc.getString("authorName")
                            val aPhoto = doc.getString("authorAvatarUrl")
                            Triple(ts, aName, aPhoto)
                        }
                        .sortedByDescending { it.first }
                    for ((_, aName, aPhoto) in sortedPosts) {
                        adoptCandidate(aName, aPhoto)
                    }
                } catch (_: Exception) {}
            }

            // 6. Check matches owned by this user/device (ownerName fallback)
            if (recoveredName == null) {
                try {
                    val matchSnaps = fs.collection("matches")
                        .whereEqualTo("ownerUid", uid)
                        .limit(10)
                        .get()
                        .await()
                    for (doc in matchSnaps.documents) {
                        adoptCandidate(doc.getString("ownerName"), null)
                    }
                } catch (_: Exception) {}
            }

            val finalName = recoveredName
            val finalPhoto = recoveredPhoto
            if (finalName != null || finalPhoto != null) {
                val baseUser = _currentUserFlow.value ?: buildInitialLocalUser()
                val recoveredUser = baseUser.copy(
                    uid = uid,
                    email = recoveredEmail.ifBlank { baseUser.email },
                    displayName = finalName ?: baseUser.displayName,
                    photoUrl = finalPhoto ?: baseUser.photoUrl,
                    deviceId = uid,
                    lastLoginAt = System.currentTimeMillis()
                )
                applyResolvedUserProfile(recoveredUser, persistToPrefs = true)

                val patch = mutableMapOf<String, Any?>(
                    "uid" to uid,
                    "deviceId" to uid,
                    "lastLoginAt" to System.currentTimeMillis()
                )
                if (finalName != null) patch["displayName"] = finalName
                if (finalPhoto != null) patch["photoUrl"] = finalPhoto
                if (recoveredEmail.isNotBlank()) patch["email"] = recoveredEmail
                fs.collection("users").document(uid).set(patch, SetOptions.merge()).await()
            } else if (!primarySnap.exists()) {
                val initial = buildInitialLocalUser()
                fs.collection("users").document(uid).set(initial, SetOptions.merge()).await()
            }
        } catch (e: Exception) {
            android.util.Log.w("AuthRepository", "Profile recovery skipped: ${e.message}")
        }
    }

    fun initializeFullAccessSession(): User {
        _isGuestMode.value = !isGoogleSignedIn()
        val current = _currentUserFlow.value ?: buildInitialLocalUser()
        _currentUserFlow.value = current
        if (enableCloudSync && firestore != null) {
            if (userDocListener == null) {
                startUserProfileSync()
            }
            val activeUid = getCurrentUserId()
            repoScope.launch {
                try {
                    val docRef = firestore.collection("users").document(activeUid)
                    val existing = docRef.get().await()
                    if (!existing.exists()) {
                        recoverExistingProfileFromDatabase()
                    } else {
                        val dbUser = existing.toObject(User::class.java)
                        applyResolvedUserProfile(dbUser, persistToPrefs = true)
                        val patch = mutableMapOf<String, Any>(
                            "uid" to activeUid,
                            "deviceId" to permanentDeviceId,
                            "lastLoginAt" to System.currentTimeMillis()
                        )
                        val savedName = readSavedProfileName()
                        val savedPhoto = readSavedProfilePhoto()
                        if (savedName != null && !isPlaceholderProfileName(savedName) && isPlaceholderProfileName(dbUser?.displayName)) {
                            patch["displayName"] = savedName
                        }
                        if (!savedPhoto.isNullOrBlank() && dbUser?.photoUrl.isNullOrBlank() && !isPhotoExplicitlyCleared()) {
                            patch["photoUrl"] = savedPhoto
                        }
                        docRef.set(patch, SetOptions.merge()).await()
                    }
                } catch (_: Exception) {}
            }
        }
        return _currentUserFlow.value ?: current
    }

    fun getCurrentUserId(): String {
        return resolvedActiveGoogleUid() ?: permanentDeviceId
    }

    fun isGoogleSignedIn(): Boolean {
        return !resolvedActiveGoogleUid().isNullOrBlank()
    }

    fun getEffectiveUserDisplayName(): String {
        val currentName = _currentUserFlow.value?.displayName?.trim()
        if (!currentName.isNullOrBlank() && !isPlaceholderProfileName(currentName)) return currentName
        return readSavedProfileName() ?: defaultPlaceholderName()
    }

    fun isRegisteredUser(): Boolean = isGoogleSignedIn()

    fun isUserLoggedIn(): Boolean = isGoogleSignedIn()

    fun enterGuestMode() {
        _isGuestMode.value = true
    }

    /**
     * Enters viewer mode when not signed in with Gmail.
     */
    suspend fun loginAsGuest(): Result<User> {
        _isGuestMode.value = !isGoogleSignedIn()
        val user = initializeFullAccessSession()
        return Result.success(user)
    }

    /**
     * Creates or updates a unique user profile in Firestore.
     * Preserves existing roles, account creation timestamps, and avoids duplicate profiles.
     */
    suspend fun createOrUpdateUserProfile(
        uid: String,
        email: String,
        displayName: String,
        provider: String,
        photoUrl: String? = null
    ): User {
        val fs = firestore ?: return User(
            uid = uid,
            email = email,
            displayName = displayName.ifBlank { email.substringBefore("@") },
            role = User.ROLE_MEMBER,
            provider = provider,
            photoUrl = photoUrl
        )
        val userRef = fs.collection("users").document(uid)
        val now = System.currentTimeMillis()

        return try {
            val existingDoc = userRef.get().await()
            if (existingDoc.exists()) {
                val dbUser = existingDoc.toObject(User::class.java)
                val preservedRole = dbUser?.role ?: User.ROLE_MEMBER
                val updatedUser = (dbUser ?: User(uid = uid)).copy(
                    email = email.ifBlank { dbUser?.email ?: "" },
                    displayName = displayName.ifBlank { dbUser?.displayName ?: email.substringBefore("@") },
                    role = preservedRole,
                    provider = provider,
                    photoUrl = photoUrl ?: dbUser?.photoUrl,
                    lastLoginAt = now
                )
                userRef.set(updatedUser).await()
                updatedUser
            } else {
                val newUser = User(
                    uid = uid,
                    email = email,
                    displayName = displayName.ifBlank { email.substringBefore("@") },
                    role = User.ROLE_MEMBER,
                    provider = provider,
                    photoUrl = photoUrl,
                    createdAt = now,
                    lastLoginAt = now
                )
                userRef.set(newUser).await()
                newUser
            }
        } catch (e: Exception) {
            User(
                uid = uid,
                email = email,
                displayName = displayName.ifBlank { email.substringBefore("@") },
                role = User.ROLE_MEMBER,
                provider = provider,
                photoUrl = photoUrl
            )
        }
    }

    fun getFirebaseAuth(): FirebaseAuth? = auth

    fun mapAuthErrorMessage(throwable: Throwable): String {
        val raw = throwable.message.orEmpty()
        if (throwable is com.google.firebase.FirebaseNetworkException) {
            return "ইন্টারনেট সংযোগ পাওয়া যায়নি। নেটওয়ার্ক পরীক্ষা করে আবার চেষ্টা করুন।"
        }
        if (throwable is com.google.firebase.auth.FirebaseAuthException) {
            return when (throwable.errorCode) {
                "ERROR_OPERATION_NOT_ALLOWED" ->
                    "Firebase Console → Authentication → Sign-in method থেকে Google Provider চালু (Enable) করা নেই।"
                "ERROR_NETWORK_REQUEST_FAILED" ->
                    "ইন্টারনেট সংযোগ ব্যর্থ হয়েছে। অনুগ্রহ করে নেটওয়ার্ক চেক করুন।"
                "ERROR_WEB_CONTEXT_CANCELED" ->
                    "ব্রাউজার থেকে Google লগইন বাতিল করা হয়েছে।"
                "ERROR_WEB_CONTEXT_ALREADY_PRESENTED" ->
                    "লগইন পেজ ইতোমধ্যে চালু আছে—একটু অপেক্ষা করুন।"
                "ERROR_INVALID_CREDENTIAL" ->
                    "Google টোকেন যাচাই ব্যর্থ হয়েছে (Web Client ID বা SHA-1 কনফিগারেশন চেক করুন)।"
                "ERROR_USER_DISABLED" ->
                    "এই Gmail অ্যাকাউন্টটি নিষ্ক্রিয় রয়েছে।"
                else -> throwable.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Google লগইন ব্যর্থ হয়েছে (${throwable.errorCode})।"
            }
        }
        return when {
            raw.contains("DEVELOPER_ERROR", ignoreCase = true) ||
                raw.contains("10:") ||
                raw.contains("SHA-1", ignoreCase = true) ->
                "অ্যাপের SHA-1 বা OAuth ক্লায়েন্ট কনফিগারেশন মেলেনি। 'ব্রাউজার দিয়ে Gmail লগইন' ব্যবহার করুন।"
            raw.contains("network", ignoreCase = true) ->
                "ইন্টারনেট সংযোগ ব্যর্থ হয়েছে। আবার চেষ্টা করুন।"
            else -> throwable.localizedMessage?.takeIf { it.isNotBlank() } ?: "Google লগইন সম্পন্ন হয়নি।"
        }
    }

    /**
     * Completes sign-in/linking for any authenticated [FirebaseUser] returned by either
     * Credential Manager ID token sign-in or Firebase Federated Browser OAuth.
     */
    suspend fun completeFirebaseUserLogin(
        firebaseUser: com.google.firebase.auth.FirebaseUser,
        fallbackDisplayName: String? = null,
        fallbackPhotoUrl: String? = null
    ): Result<User> {
        return try {
            val linkedUser = linkAndSyncGoogleUser(
                googleUid = firebaseUser.uid,
                email = firebaseUser.email.orEmpty(),
                googleDisplayName = fallbackDisplayName?.takeIf { it.isNotBlank() } ?: firebaseUser.displayName,
                googlePhotoUrl = fallbackPhotoUrl?.takeIf { it.isNotBlank() } ?: firebaseUser.photoUrl?.toString()
            )
            _isGuestMode.value = false
            Result.success(linkedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Recovers a pending Firebase Federated OAuth login if Android recreated the Activity
     * while the Chrome Custom Tab / browser flow was active.
     */
    suspend fun consumePendingFirebaseAuthResult(): Result<User?> {
        val firebaseAuth = auth ?: return Result.success(null)
        return try {
            val pendingTask = firebaseAuth.pendingAuthResult ?: return Result.success(null)
            val authResult = pendingTask.await()
            val firebaseUser = authResult?.user ?: return Result.success(null)
            val linked = completeFirebaseUserLogin(firebaseUser).getOrThrow()
            Result.success(linked)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Authenticates with Google Identity ID token via Firebase Auth Credential
     * and links the pre-existing device profile, cricket stats, and ownership to the Gmail account.
     */
    suspend fun signInWithGoogleCredential(
        idToken: String,
        displayName: String? = null,
        photoUrl: String? = null
    ): Result<User> {
        return try {
            val firebaseAuth = auth ?: throw IllegalStateException("Auth unavailable")
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: throw Exception("Google Authentication failed")
            completeFirebaseUserLogin(
                firebaseUser = firebaseUser,
                fallbackDisplayName = displayName,
                fallbackPhotoUrl = photoUrl
            )
        } catch (e: Exception) {
            Result.failure(IllegalStateException(mapAuthErrorMessage(e), e))
        }
    }

    /**
     * Links a Google/Gmail account identity to any existing device-local CricUp profile,
     * merging cloud and local cricket details, teams, followers, and migrating local ownership.
     */
    suspend fun linkAndSyncGoogleUser(
        googleUid: String,
        email: String,
        googleDisplayName: String? = null,
        googlePhotoUrl: String? = null
    ): User {
        val previousUid = getCurrentUserId()
        val deviceUid = permanentDeviceId
        val lastGoogleUid = profilePrefs()?.getString(KEY_LAST_GOOGLE_UID, null)?.takeIf { it.isNotBlank() }
        val isSwitchingDifferentGoogleAccount = lastGoogleUid != null && lastGoogleUid != googleUid
        val canAdoptLocal = !isSwitchingDifferentGoogleAccount && (previousUid == deviceUid || previousUid.isBlank())
        val now = System.currentTimeMillis()

        if (isSwitchingDifferentGoogleAccount) {
            // Wipe previous Gmail user's local preferences and Room data before loading the new account
            profilePrefs()?.edit()?.apply {
                remove(KEY_DISPLAY_NAME)
                remove(KEY_PHOTO_URL)
                remove(KEY_PHOTO_CLEARED)
                remove(KEY_PRIMARY_ROLE)
                remove(KEY_BATTING_STYLE)
                remove(KEY_BOWLING_STYLE)
                remove(KEY_BIO)
                remove(KEY_JOINED_TEAMS)
                remove(KEY_FOLLOWERS)
                remove(KEY_FOLLOWING)
                remove(KEY_PREDICTION_POINTS)
                remove(KEY_PREDICTIONS_WON)
                remove(KEY_PREDICTIONS_TOTAL)
                remove(KEY_DEVICE_BACKUP_NAME)
                remove(KEY_DEVICE_BACKUP_PHOTO)
            }?.apply()
            _currentUserFlow.value = null
            _allUsersFlow.value = emptyList()
            try {
                onAccountSwitching?.invoke(lastGoogleUid!!, googleUid)
            } catch (e: Exception) {
                android.util.Log.w("AuthRepository", "Account switch cleanup warning: ${e.message}")
            }
        }
        val currentSnapshot = if (isSwitchingDifferentGoogleAccount) null else _currentUserFlow.value

        // Back up pure device name/photo before switching into Google profile so Sign Out restores cleanly
        if (canAdoptLocal) {
            profilePrefs()?.edit()?.apply {
                readSavedProfileName()?.let { putString(KEY_DEVICE_BACKUP_NAME, it) }
                readSavedProfilePhoto()?.let { putString(KEY_DEVICE_BACKUP_PHOTO, it) }
            }?.apply()
        }

        val fs = firestore
        val cloudGoogleUser = if (fs != null) {
            try {
                fs.collection("users").document(googleUid).get().await().toObject(User::class.java)
            } catch (_: Exception) { null }
        } else null

        val cloudDeviceUser = if (fs != null && canAdoptLocal && deviceUid != googleUid) {
            try {
                fs.collection("users").document(deviceUid).get().await().toObject(User::class.java)
            } catch (_: Exception) { null }
        } else null

        val localName = if (canAdoptLocal) {
            currentSnapshot?.displayName?.takeUnless { isPlaceholderProfileName(it) } ?: readSavedProfileName()
        } else null
        val localPhoto = if (canAdoptLocal && !isPhotoExplicitlyCleared()) {
            currentSnapshot?.photoUrl?.takeIf { it.isNotBlank() } ?: readSavedProfilePhoto()
        } else null
        val localRole = if (canAdoptLocal) currentSnapshot?.primaryRole?.takeIf { it.isNotBlank() } ?: readSavedRole() else PlayerRoles.ALL_ROUNDER
        val localBat = if (canAdoptLocal) currentSnapshot?.battingStyle?.takeIf { it.isNotBlank() } ?: readSavedBattingStyle() else "Right Hand"
        val localBowl = if (canAdoptLocal) currentSnapshot?.bowlingStyle?.takeIf { it.isNotBlank() } ?: readSavedBowlingStyle() else "Right-arm Medium"
        val localBio = if (canAdoptLocal) currentSnapshot?.bio ?: readSavedBio() else ""
        val localTeams = if (canAdoptLocal) (currentSnapshot?.joinedTeamIds.orEmpty() + readSavedIdSet(KEY_JOINED_TEAMS)).distinct() else emptyList()
        val localFollowers = if (canAdoptLocal) (currentSnapshot?.followerIds.orEmpty() + readSavedIdSet(KEY_FOLLOWERS)).distinct() else emptyList()
        val localFollowing = if (canAdoptLocal) (currentSnapshot?.followingIds.orEmpty() + readSavedIdSet(KEY_FOLLOWING)).distinct() else emptyList()

        val cleanEmail = email.trim().lowercase().ifBlank { cloudGoogleUser?.email.orEmpty() }
        val cleanGoogleDisplayName = googleDisplayName?.trim()
            ?.takeUnless { isPlaceholderProfileName(it) || it.equals("Google User", ignoreCase = true) }

        val resolvedName = listOfNotNull(
            cloudGoogleUser?.displayName?.trim()?.takeUnless { isPlaceholderProfileName(it) },
            localName?.trim()?.takeUnless { isPlaceholderProfileName(it) },
            cloudDeviceUser?.displayName?.trim()?.takeUnless { isPlaceholderProfileName(it) },
            cleanGoogleDisplayName,
            cleanEmail.substringBefore("@").takeIf { it.isNotBlank() }
        ).firstOrNull() ?: defaultPlaceholderName(googleUid)

        val resolvedPhoto = listOfNotNull(
            cloudGoogleUser?.photoUrl?.takeIf { it.isNotBlank() },
            localPhoto?.takeIf { it.isNotBlank() },
            cloudDeviceUser?.photoUrl?.takeIf { it.isNotBlank() },
            googlePhotoUrl?.takeIf { it.isNotBlank() }
        ).firstOrNull()

        val resolvedRole = cloudGoogleUser?.primaryRole?.takeIf { it.isNotBlank() }
            ?: cloudDeviceUser?.primaryRole?.takeIf { it.isNotBlank() }
            ?: localRole
        val resolvedBat = cloudGoogleUser?.battingStyle?.takeIf { it.isNotBlank() }
            ?: cloudDeviceUser?.battingStyle?.takeIf { it.isNotBlank() }
            ?: localBat
        val resolvedBowl = cloudGoogleUser?.bowlingStyle?.takeIf { it.isNotBlank() }
            ?: cloudDeviceUser?.bowlingStyle?.takeIf { it.isNotBlank() }
            ?: localBowl
        val resolvedBio = cloudGoogleUser?.bio?.takeIf { it.isNotBlank() }
            ?: cloudDeviceUser?.bio?.takeIf { it.isNotBlank() }
            ?: localBio

        val resolvedTeams = (cloudGoogleUser?.joinedTeamIds.orEmpty() +
            cloudDeviceUser?.joinedTeamIds.orEmpty() + localTeams).distinct()
        val resolvedFollowers = (cloudGoogleUser?.followerIds.orEmpty() +
            cloudDeviceUser?.followerIds.orEmpty() + localFollowers).distinct()
        val resolvedFollowing = (cloudGoogleUser?.followingIds.orEmpty() +
            cloudDeviceUser?.followingIds.orEmpty() + localFollowing).distinct()
        val resolvedLinkedDevices = (cloudGoogleUser?.linkedDeviceIds.orEmpty() + listOf(deviceUid))
            .filter { it.isNotBlank() }
            .distinct()

        val mergedPredPoints = if (canAdoptLocal) {
            maxOf(cloudGoogleUser?.predictionPoints ?: 0, cloudDeviceUser?.predictionPoints ?: 0, currentSnapshot?.predictionPoints ?: 0, readSavedPredictionPoints())
        } else {
            cloudGoogleUser?.predictionPoints ?: 0
        }
        val mergedPredWon = if (canAdoptLocal) {
            maxOf(cloudGoogleUser?.predictionsWon ?: 0, cloudDeviceUser?.predictionsWon ?: 0, currentSnapshot?.predictionsWon ?: 0, readSavedPredictionsWon())
        } else {
            cloudGoogleUser?.predictionsWon ?: 0
        }
        val mergedPredTotal = if (canAdoptLocal) {
            maxOf(cloudGoogleUser?.predictionsTotal ?: 0, cloudDeviceUser?.predictionsTotal ?: 0, currentSnapshot?.predictionsTotal ?: 0, readSavedPredictionsTotal())
        } else {
            cloudGoogleUser?.predictionsTotal ?: 0
        }

        val mergedUser = User(
            uid = googleUid,
            email = cleanEmail,
            displayName = resolvedName,
            role = cloudGoogleUser?.role?.ifBlank { User.ROLE_MEMBER } ?: User.ROLE_MEMBER,
            photoUrl = resolvedPhoto,
            primaryRole = resolvedRole,
            battingStyle = resolvedBat,
            bowlingStyle = resolvedBowl,
            bio = resolvedBio,
            linkedPlayerId = cloudGoogleUser?.linkedPlayerId?.takeIf { it.isNotBlank() } ?: googleUid,
            joinedTeamIds = resolvedTeams,
            followerIds = resolvedFollowers,
            followingIds = resolvedFollowing,
            predictionPoints = mergedPredPoints,
            predictionsWon = mergedPredWon,
            predictionsTotal = mergedPredTotal,
            isSuspended = cloudGoogleUser?.isSuspended ?: false,
            isBlocked = cloudGoogleUser?.isBlocked ?: false,
            deviceId = deviceUid,
            linkedDeviceIds = resolvedLinkedDevices,
            provider = "google",
            createdAt = cloudGoogleUser?.createdAt ?: now,
            lastLoginAt = now
        )

        signedInGoogleUid = googleUid
        persistGoogleSessionLocally(googleUid, cleanEmail, resolvedName, resolvedPhoto)
        persistProfileLocally(resolvedName, resolvedPhoto, explicitlyCleared = false)
        persistCricketDetailsLocally(
            primaryRole = resolvedRole,
            battingStyle = resolvedBat,
            bowlingStyle = resolvedBowl,
            bio = resolvedBio,
            joinedTeamIds = resolvedTeams,
            followerIds = resolvedFollowers,
            followingIds = resolvedFollowing
        )
        persistPredictionStatsLocally(mergedPredPoints, mergedPredWon, mergedPredTotal)

        _isGuestMode.value = false
        _currentUserFlow.value = mergedUser
        upsertUserInDirectory(mergedUser)
        onProfileSynced?.invoke(resolvedName, resolvedPhoto, false)
        onUserCricketProfileUpdated?.invoke(mergedUser)

        if (fs != null) {
            try {
                fs.collection("users").document(googleUid).set(mergedUser, SetOptions.merge()).await()
                if (deviceUid.isNotBlank() && deviceUid != googleUid) {
                    fs.collection("users").document(deviceUid).set(
                        mapOf(
                            "uid" to deviceUid,
                            "linkedUserUid" to googleUid,
                            "email" to cleanEmail,
                            "displayName" to resolvedName,
                            "provider" to "google_linked",
                            "lastLoginAt" to now
                        ),
                        SetOptions.merge()
                    ).await()
                }
            } catch (e: Exception) {
                android.util.Log.w("AuthRepository", "Firestore link sync warning: ${e.message}")
            }
        }

        if (enableCloudSync && fs != null) {
            startUserProfileSync(forceRestart = true)
        }

        if (!isSwitchingDifferentGoogleAccount && previousUid.isNotBlank() && previousUid != googleUid && previousUid.startsWith("dev_")) {
            migrateSearchedScorerUsers(previousUid, googleUid)
            try {
                onAccountLinked?.invoke(previousUid, mergedUser)
            } catch (e: Exception) {
                android.util.Log.w("AuthRepository", "Ownership link warning: ${e.message}")
            }
        }
        onUserAuthenticated?.invoke(mergedUser)
        return mergedUser
    }

    fun signOut() {
        val previousUid = getCurrentUserId()
        try {
            auth?.signOut()
        } catch (_: Exception) {}
        signedInGoogleUid = null
        clearGoogleSessionLocally()

        val prefs = profilePrefs()
        val backupName = prefs?.getString(KEY_DEVICE_BACKUP_NAME, null)
            ?.trim()
            ?.takeUnless { isPlaceholderProfileName(it) }
        val backupPhoto = prefs?.getString(KEY_DEVICE_BACKUP_PHOTO, null)
            ?.takeIf { it.isNotBlank() }

        prefs?.edit()?.apply {
            if (!backupName.isNullOrBlank()) {
                putString(KEY_DISPLAY_NAME, backupName)
            } else {
                remove(KEY_DISPLAY_NAME)
            }
            if (!backupPhoto.isNullOrBlank()) {
                putString(KEY_PHOTO_URL, backupPhoto)
                putBoolean(KEY_PHOTO_CLEARED, false)
            } else {
                remove(KEY_PHOTO_URL)
                remove(KEY_PHOTO_CLEARED)
            }
            remove(KEY_PRIMARY_ROLE)
            remove(KEY_BATTING_STYLE)
            remove(KEY_BOWLING_STYLE)
            remove(KEY_BIO)
            remove(KEY_JOINED_TEAMS)
            remove(KEY_FOLLOWERS)
            remove(KEY_FOLLOWING)
            remove(KEY_PREDICTION_POINTS)
            remove(KEY_PREDICTIONS_WON)
            remove(KEY_PREDICTIONS_TOTAL)
        }?.apply()

        _isGuestMode.value = true
        val deviceUser = buildInitialLocalUser()
        _currentUserFlow.value = deviceUser
        _allUsersFlow.value = listOf(deviceUser)
        onSignedOutCleanup?.invoke(previousUid)
        onProfileSynced?.invoke(
            if (isPlaceholderProfileName(deviceUser.displayName)) "" else deviceUser.displayName,
            deviceUser.photoUrl,
            deviceUser.photoUrl == null
        )
        if (enableCloudSync && firestore != null) {
            startUserProfileSync(forceRestart = true)
        }
    }

    fun getCurrentUserFlow(): StateFlow<User?> = currentUserStateFlow

    suspend fun updateUserProfile(
        uid: String,
        displayName: String,
        photoUrl: String?,
        clearPhoto: Boolean = false
    ): Result<Unit> {
        val cleanName = displayName.trim()
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("নাম খালি রাখা যাবে না"))

        val existingPhoto = _currentUserFlow.value?.photoUrl ?: readSavedProfilePhoto()
        val resolvedPhoto = when {
            clearPhoto -> null
            !photoUrl.isNullOrBlank() -> photoUrl
            else -> existingPhoto
        }

        persistProfileLocally(
            displayName = cleanName,
            photoUrl = resolvedPhoto,
            explicitlyCleared = clearPhoto
        )
        // Always ensure even custom names update KEY_DISPLAY_NAME even if user explicitly picks something
        profilePrefs()?.edit()?.putString(KEY_DISPLAY_NAME, cleanName)?.apply()

        val targetUid = uid.ifBlank { getCurrentUserId() }
        val base = _currentUserFlow.value ?: buildInitialLocalUser()
        val updatedUser = base.copy(
            uid = targetUid,
            deviceId = permanentDeviceId,
            displayName = cleanName,
            photoUrl = resolvedPhoto,
            lastLoginAt = System.currentTimeMillis()
        )
        _currentUserFlow.value = updatedUser
        upsertUserInDirectory(updatedUser)
        onProfileSynced?.invoke(cleanName, resolvedPhoto, clearPhoto)
        onUserCricketProfileUpdated?.invoke(updatedUser)

        val fs = firestore ?: return Result.success(Unit)
        return try {
            val payload = mutableMapOf<String, Any?>(
                "uid" to targetUid,
                "deviceId" to permanentDeviceId,
                "displayName" to cleanName,
                "role" to User.ROLE_MEMBER,
                "lastLoginAt" to System.currentTimeMillis()
            )
            if (clearPhoto) {
                payload["photoUrl"] = null
            } else if (!resolvedPhoto.isNullOrBlank()) {
                payload["photoUrl"] = resolvedPhoto
            }
            fs.collection("users").document(targetUid)
                .set(payload, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserCricketProfile(
        uid: String = getCurrentUserId(),
        displayName: String,
        photoUrl: String?,
        clearPhoto: Boolean = false,
        primaryRole: String,
        battingStyle: String,
        bowlingStyle: String,
        bio: String = ""
    ): Result<User> {
        val cleanName = displayName.trim()
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("নাম খালি রাখা যাবে না"))

        val base = _currentUserFlow.value ?: buildInitialLocalUser()
        val resolvedPhoto = when {
            clearPhoto -> null
            !photoUrl.isNullOrBlank() -> photoUrl
            else -> base.photoUrl ?: readSavedProfilePhoto()
        }
        val cleanRole = primaryRole.ifBlank { base.primaryRole }
        val cleanBat = battingStyle.ifBlank { base.battingStyle }
        val cleanBowl = bowlingStyle.ifBlank { base.bowlingStyle }
        val cleanBio = bio.trim()

        persistProfileLocally(cleanName, resolvedPhoto, explicitlyCleared = clearPhoto)
        profilePrefs()?.edit()?.putString(KEY_DISPLAY_NAME, cleanName)?.apply()
        persistCricketDetailsLocally(
            primaryRole = cleanRole,
            battingStyle = cleanBat,
            bowlingStyle = cleanBowl,
            bio = cleanBio
        )

        val targetUid = uid.ifBlank { getCurrentUserId() }
        val updated = base.copy(
            uid = targetUid,
            deviceId = permanentDeviceId,
            displayName = cleanName,
            photoUrl = resolvedPhoto,
            primaryRole = cleanRole,
            battingStyle = cleanBat,
            bowlingStyle = cleanBowl,
            bio = cleanBio,
            linkedPlayerId = base.linkedPlayerId.ifBlank { targetUid },
            lastLoginAt = System.currentTimeMillis()
        )
        _currentUserFlow.value = updated
        upsertUserInDirectory(updated)
        onProfileSynced?.invoke(cleanName, resolvedPhoto, clearPhoto)
        onUserCricketProfileUpdated?.invoke(updated)

        val fs = firestore ?: return Result.success(updated)
        return try {
            val payload = mutableMapOf<String, Any?>(
                "uid" to targetUid,
                "deviceId" to permanentDeviceId,
                "displayName" to cleanName,
                "role" to User.ROLE_MEMBER,
                "primaryRole" to cleanRole,
                "battingStyle" to cleanBat,
                "bowlingStyle" to cleanBowl,
                "bio" to cleanBio,
                "linkedPlayerId" to updated.linkedPlayerId,
                "lastLoginAt" to updated.lastLoginAt
            )
            if (clearPhoto) {
                payload["photoUrl"] = null
            } else if (!resolvedPhoto.isNullOrBlank()) {
                payload["photoUrl"] = resolvedPhoto
            }
            fs.collection("users").document(targetUid)
                .set(payload, SetOptions.merge())
                .await()
            Result.success(updated)
        } catch (e: Exception) {
            Result.success(updated)
        }
    }

    suspend fun updateUserJoinedTeams(teamId: String, join: Boolean): Result<User> {
        val cleanTeamId = teamId.trim()
        if (cleanTeamId.isBlank()) return Result.failure(IllegalArgumentException("Invalid team"))
        val base = _currentUserFlow.value ?: buildInitialLocalUser()
        val updatedTeams = if (join) {
            (base.joinedTeamIds + cleanTeamId).distinct()
        } else {
            base.joinedTeamIds.filter { it != cleanTeamId }
        }
        val updated = base.copy(
            joinedTeamIds = updatedTeams,
            linkedPlayerId = base.linkedPlayerId.ifBlank { base.uid },
            lastLoginAt = System.currentTimeMillis()
        )
        _currentUserFlow.value = updated
        upsertUserInDirectory(updated)
        persistCricketDetailsLocally(joinedTeamIds = updatedTeams)
        onUserCricketProfileUpdated?.invoke(updated)

        val fs = firestore
        if (fs != null) {
            try {
                fs.collection("users").document(updated.uid)
                    .set(
                        mapOf(
                            "uid" to updated.uid,
                            "joinedTeamIds" to updatedTeams,
                            "linkedPlayerId" to updated.effectivePlayerId
                        ),
                        SetOptions.merge()
                    )
                    .await()
            } catch (_: Exception) {}
        }
        return Result.success(updated)
    }

    /**
     * Toggles following status from current user -> [targetUid].
     * Returns true if now following, false if unfollowed.
     */
    suspend fun toggleFollowUser(targetUid: String): Result<Boolean> {
        val cleanTarget = targetUid.trim()
        val myUser = _currentUserFlow.value ?: buildInitialLocalUser()
        if (cleanTarget.isBlank() || cleanTarget == myUser.uid) {
            return Result.failure(IllegalArgumentException("নিজেকে ফলো করা যাবে না"))
        }
        val alreadyFollowing = myUser.followingIds.contains(cleanTarget)
        val shouldFollow = !alreadyFollowing

        val updatedMyFollowing = if (shouldFollow) {
            (myUser.followingIds + cleanTarget).distinct()
        } else {
            myUser.followingIds.filter { it != cleanTarget }
        }
        val updatedSelf = myUser.copy(followingIds = updatedMyFollowing)
        _currentUserFlow.value = updatedSelf
        upsertUserInDirectory(updatedSelf)
        persistCricketDetailsLocally(followingIds = updatedMyFollowing)

        // Update target user's followerIds in local directory cache too
        val existingTarget = _allUsersFlow.value.firstOrNull { it.uid == cleanTarget }
        if (existingTarget != null) {
            val updatedTargetFollowers = if (shouldFollow) {
                (existingTarget.followerIds + myUser.uid).distinct()
            } else {
                existingTarget.followerIds.filter { it != myUser.uid }
            }
            upsertUserInDirectory(existingTarget.copy(followerIds = updatedTargetFollowers))
        }

        val fs = firestore
        if (fs != null) {
            try {
                val myUpdate = if (shouldFollow) {
                    com.google.firebase.firestore.FieldValue.arrayUnion(cleanTarget)
                } else {
                    com.google.firebase.firestore.FieldValue.arrayRemove(cleanTarget)
                }
                fs.collection("users").document(myUser.uid)
                    .set(mapOf("uid" to myUser.uid, "followingIds" to myUpdate), SetOptions.merge())
                    .await()

                val targetUpdate = if (shouldFollow) {
                    com.google.firebase.firestore.FieldValue.arrayUnion(myUser.uid)
                } else {
                    com.google.firebase.firestore.FieldValue.arrayRemove(myUser.uid)
                }
                fs.collection("users").document(cleanTarget)
                    .set(mapOf("uid" to cleanTarget, "followerIds" to targetUpdate), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                android.util.Log.w("AuthRepository", "Follow sync failed: ${e.message}")
            }
        }
        return Result.success(shouldFollow)
    }

    /**
     * Awards settled spectator prediction points and win/total counters to the current user profile
     * and atomically syncs the increment to Firestore.
     */
    suspend fun recordPredictionOutcome(
        winsAdded: Int,
        lossesAdded: Int,
        pointsEarned: Int
    ): Result<User> {
        val settledCount = (winsAdded.coerceAtLeast(0) + lossesAdded.coerceAtLeast(0))
        val safePoints = pointsEarned.coerceAtLeast(0)
        val base = _currentUserFlow.value ?: buildInitialLocalUser()
        if (settledCount <= 0 && safePoints <= 0) return Result.success(base)

        val newPoints = (base.predictionPoints + safePoints).coerceAtLeast(0)
        val newWon = (base.predictionsWon + winsAdded.coerceAtLeast(0)).coerceAtLeast(0)
        val newTotal = (base.predictionsTotal + settledCount).coerceAtLeast(newWon)

        val updated = base.copy(
            predictionPoints = newPoints,
            predictionsWon = newWon,
            predictionsTotal = newTotal,
            lastLoginAt = System.currentTimeMillis()
        )
        _currentUserFlow.value = updated
        upsertUserInDirectory(updated)
        persistPredictionStatsLocally(newPoints, newWon, newTotal)

        val fs = firestore
        if (fs != null && updated.uid.isNotBlank()) {
            try {
                val updates = mutableMapOf<String, Any>(
                    "uid" to updated.uid,
                    "lastLoginAt" to updated.lastLoginAt
                )
                if (safePoints > 0) {
                    updates["predictionPoints"] = com.google.firebase.firestore.FieldValue.increment(safePoints.toLong())
                }
                if (winsAdded > 0) {
                    updates["predictionsWon"] = com.google.firebase.firestore.FieldValue.increment(winsAdded.toLong())
                }
                if (settledCount > 0) {
                    updates["predictionsTotal"] = com.google.firebase.firestore.FieldValue.increment(settledCount.toLong())
                }
                fs.collection("users").document(updated.uid)
                    .set(updates, SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                android.util.Log.w("AuthRepository", "Prediction points cloud sync skipped: ${e.message}")
            }
        }
        return Result.success(updated)
    }

    suspend fun updateUserRole(targetUid: String, newRole: String): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Cloud unavailable"))
        return try {
            fs.collection("users").document(targetUid).update("role", newRole).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setUserSuspended(targetUid: String, isSuspended: Boolean): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Cloud unavailable"))
        return try {
            fs.collection("users").document(targetUid).update("isSuspended", isSuspended).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAllUsers(): Result<List<User>> {
        val fs = firestore ?: return Result.success(emptyList())
        return try {
            val snapshot = fs.collection("users").get().await()
            val list = snapshot.documents.mapNotNull { it.toObject(User::class.java) }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun searchedScorerPrefs(): android.content.SharedPreferences? =
        context?.getSharedPreferences(SEARCHED_SCORER_PREFS, android.content.Context.MODE_PRIVATE)

    private fun compactUserForSuggestionHistory(user: User): User {
        val compactPhoto = user.photoUrl?.takeIf {
            it.isNotBlank() && !it.startsWith("data:image", ignoreCase = true) && it.length <= 512
        }
        return User(
            uid = user.uid.trim(),
            email = user.email.trim(),
            displayName = user.displayName.trim(),
            photoUrl = compactPhoto,
            primaryRole = user.primaryRole
        )
    }

    private fun persistSearchedScorerUsers(ownerUid: String, users: List<User>) {
        val cleanOwner = ownerUid.trim()
        if (cleanOwner.isBlank()) return
        val prefs = searchedScorerPrefs() ?: return
        val trimmed = users
            .filter { it.uid.isNotBlank() && it.uid != cleanOwner }
            .distinctBy { it.uid }
            .take(MAX_SEARCHED_SCORER_HISTORY)
            .map { compactUserForSuggestionHistory(it) }
        val json = com.example.util.JsonHelper.toJson(trimmed.toTypedArray())
        prefs.edit().putString("$KEY_SEARCHED_SCORERS_PREFIX$cleanOwner", json).apply()
    }

    /**
     * Returns ONLY the CricUp users/IDs that [ownerUid] has previously searched for or added.
     * Never returns unsearched users or other accounts' devices automatically.
     */
    fun getPreviouslySearchedScorerUsers(ownerUid: String = getCurrentUserId()): List<User> {
        val cleanOwner = ownerUid.trim()
        if (cleanOwner.isBlank()) return emptyList()
        val json = searchedScorerPrefs()?.getString("$KEY_SEARCHED_SCORERS_PREFIX$cleanOwner", null)
            ?.takeIf { it.isNotBlank() } ?: return emptyList()
        return com.example.util.JsonHelper.fromJson<Array<User>>(json)
            ?.toList()
            .orEmpty()
            .filter { it.uid.isNotBlank() && it.uid != cleanOwner }
            .distinctBy { it.uid }
    }

    fun recordSearchedScorerUser(user: User, ownerUid: String = getCurrentUserId()): List<User> {
        val cleanOwner = ownerUid.trim()
        val cleanTarget = user.uid.trim()
        if (cleanOwner.isBlank() || cleanTarget.isBlank() || cleanTarget == cleanOwner) {
            return getPreviouslySearchedScorerUsers(cleanOwner)
        }
        val compact = compactUserForSuggestionHistory(user)
        val existing = getPreviouslySearchedScorerUsers(cleanOwner).filterNot { it.uid == cleanTarget }
        val updated = (listOf(compact) + existing).take(MAX_SEARCHED_SCORER_HISTORY)
        persistSearchedScorerUsers(cleanOwner, updated)
        return updated
    }

    fun removeSearchedScorerUser(targetUid: String, ownerUid: String = getCurrentUserId()): List<User> {
        val cleanOwner = ownerUid.trim()
        val cleanTarget = targetUid.trim()
        if (cleanOwner.isBlank()) return emptyList()
        val updated = getPreviouslySearchedScorerUsers(cleanOwner).filterNot { it.uid == cleanTarget }
        persistSearchedScorerUsers(cleanOwner, updated)
        return updated
    }

    fun clearSearchedScorerUsers(ownerUid: String) {
        val cleanOwner = ownerUid.trim()
        if (cleanOwner.isBlank()) return
        searchedScorerPrefs()?.edit()?.remove("$KEY_SEARCHED_SCORERS_PREFIX$cleanOwner")?.apply()
    }

    fun migrateSearchedScorerUsers(oldUid: String, newUid: String) {
        val cleanOld = oldUid.trim()
        val cleanNew = newUid.trim()
        if (cleanOld.isBlank() || cleanNew.isBlank() || cleanOld == cleanNew) return
        val oldList = getPreviouslySearchedScorerUsers(cleanOld)
        if (oldList.isEmpty()) return
        val existingNew = getPreviouslySearchedScorerUsers(cleanNew)
        val merged = (existingNew + oldList)
            .filter { it.uid.isNotBlank() && it.uid != cleanNew }
            .distinctBy { it.uid }
            .take(MAX_SEARCHED_SCORER_HISTORY)
        persistSearchedScorerUsers(cleanNew, merged)
        clearSearchedScorerUsers(cleanOld)
    }

    /**
     * Searches CricUp members strictly on explicit user query by User ID, Display Name, or Gmail.
     * Returns an empty list whenever [query] is blank.
     */
    suspend fun searchUsers(query: String, excludeUid: String = ""): Result<List<User>> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return Result.success(emptyList())
        val qLower = cleanQuery.lowercase()
        val cleanExclude = excludeUid.trim()

        val candidatesByUid = linkedMapOf<String, User>()
        fun absorb(user: User?, fallbackId: String? = null) {
            val u = user ?: return
            val resolvedUid = u.uid.ifBlank { fallbackId.orEmpty() }.trim()
            if (resolvedUid.isBlank() || resolvedUid == cleanExclude) return
            val normalized = if (u.uid == resolvedUid) u else u.copy(uid = resolvedUid)
            if (normalized.provider.equals("google_linked", ignoreCase = true) &&
                !normalized.uid.equals(cleanQuery, ignoreCase = true)
            ) {
                return
            }
            val prev = candidatesByUid[resolvedUid]
            if (prev == null ||
                (prev.displayName.isBlank() && normalized.displayName.isNotBlank()) ||
                (prev.email.isBlank() && normalized.email.isNotBlank()) ||
                (prev.photoUrl.isNullOrBlank() && !normalized.photoUrl.isNullOrBlank())
            ) {
                candidatesByUid[resolvedUid] = normalized
            }
        }

        // Seed with already-known local directory/history entries for fast offline or test matches
        _allUsersFlow.value.forEach { absorb(it) }
        getPreviouslySearchedScorerUsers().forEach { absorb(it) }

        val fs = firestore
        if (fs != null) {
            try {
                // 1. Direct document ID lookup (works for any UID even beyond 250 users)
                if (!cleanQuery.contains("/") && !cleanQuery.contains(" ")) {
                    try {
                        val doc = fs.collection("users").document(cleanQuery).get().await()
                        if (doc.exists()) {
                            absorb(doc.toObject(User::class.java), doc.id)
                        }
                    } catch (_: Exception) {}
                }

                // 2. Exact email (Gmail) lookup
                if (cleanQuery.contains("@")) {
                    try {
                        val emailSnap = fs.collection("users")
                            .whereEqualTo("email", qLower)
                            .limit(20)
                            .get()
                            .await()
                        emailSnap.documents.forEach { doc ->
                            absorb(doc.toObject(User::class.java), doc.id)
                        }
                        if (cleanQuery != qLower) {
                            val rawEmailSnap = fs.collection("users")
                                .whereEqualTo("email", cleanQuery)
                                .limit(20)
                                .get()
                                .await()
                            rawEmailSnap.documents.forEach { doc ->
                                absorb(doc.toObject(User::class.java), doc.id)
                            }
                        }
                    } catch (_: Exception) {}
                }

                // 3. Partial match scan for Name / UID / Gmail substring
                try {
                    val dirSnap = fs.collection("users").limit(250).get().await()
                    dirSnap.documents.forEach { doc ->
                        absorb(doc.toObject(User::class.java), doc.id)
                    }
                } catch (_: Exception) {}
            } catch (e: Exception) {
                if (candidatesByUid.isEmpty()) return Result.failure(e)
            }
        }

        val matches = candidatesByUid.values.filter { user ->
            user.uid.contains(qLower, ignoreCase = true) ||
                user.displayName.contains(qLower, ignoreCase = true) ||
                user.email.contains(qLower, ignoreCase = true)
        }.sortedWith(
            compareByDescending<User> {
                it.uid.equals(cleanQuery, ignoreCase = true) || it.email.equals(qLower, ignoreCase = true)
            }.thenByDescending {
                it.displayName.startsWith(cleanQuery, ignoreCase = true) ||
                    it.email.startsWith(qLower, ignoreCase = true) ||
                    it.uid.startsWith(cleanQuery, ignoreCase = true)
            }.thenBy { it.displayName.lowercase() }
        )

        return Result.success(matches)
    }
}
