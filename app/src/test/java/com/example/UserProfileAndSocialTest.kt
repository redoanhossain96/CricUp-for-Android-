package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.PlayerRoles
import com.example.data.model.PostMediaType
import com.example.data.model.Team
import com.example.data.model.User
import com.example.data.repository.AuthRepository
import com.example.data.repository.PlayerRepository
import com.example.data.repository.PostRepository
import com.example.data.repository.TeamRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserProfileAndSocialTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "community_posts_cache.json").delete()
        context.getSharedPreferences("cricup_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("cricup_member_profile", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        File(context.filesDir, "community_posts_cache.json").delete()
    }

    @Test
    fun `updateUserCricketProfile persists batting bowling styles and role locally`(): Unit = runBlocking {
        val repo1 = AuthRepository(
            context = context,
            auth = null,
            firestore = null,
            emailOtpService = null,
            enableCloudSync = false
        )
        val uid = repo1.getCurrentUserId()

        val result = repo1.updateUserCricketProfile(
            uid = uid,
            displayName = "Shakib Member",
            photoUrl = null,
            primaryRole = PlayerRoles.ALL_ROUNDER,
            battingStyle = "Left Hand",
            bowlingStyle = "Left-arm Orthodox",
            bio = "Club Captain & All-rounder"
        )
        assertTrue(result.isSuccess)
        val saved = result.getOrThrow()
        assertEquals("Left Hand", saved.battingStyle)
        assertEquals("Left-arm Orthodox", saved.bowlingStyle)
        assertEquals("Club Captain & All-rounder", saved.bio)

        // Reload AuthRepository to ensure SharedPreferences durability
        val repo2 = AuthRepository(
            context = context,
            auth = null,
            firestore = null,
            emailOtpService = null,
            enableCloudSync = false
        )
        val reloaded = repo2.getCurrentUserFlow().first()
        assertEquals("Shakib Member", reloaded?.displayName)
        assertEquals("Left Hand", reloaded?.battingStyle)
        assertEquals("Left-arm Orthodox", reloaded?.bowlingStyle)
        assertEquals("Club Captain & All-rounder", reloaded?.bio)
    }

    @Test
    fun `toggleFollowUser follows unfollows directory member and rejects self follow`(): Unit = runBlocking {
        val authRepo = AuthRepository(
            context = context,
            auth = null,
            firestore = null,
            emailOtpService = null,
            enableCloudSync = false
        )
        val selfUid = authRepo.getCurrentUserId()
        val otherMember = User(
            uid = "member_tamim",
            displayName = "Tamim Iqbal",
            primaryRole = PlayerRoles.BATSMAN,
            battingStyle = "Left Hand"
        )
        authRepo.seedDirectoryUsers(listOf(otherMember))

        // Self-follow is rejected
        val selfRes = authRepo.toggleFollowUser(selfUid)
        assertTrue(selfRes.isFailure)

        // Follow target member
        val followRes = authRepo.toggleFollowUser("member_tamim")
        assertTrue(followRes.isSuccess)
        assertEquals(true, followRes.getOrNull())
        assertTrue(authRepo.getCurrentUserFlow().value?.followingIds?.contains("member_tamim") == true)
        val dirAfterFollow = authRepo.allUsersFlow.value.first { it.uid == "member_tamim" }
        assertTrue(dirAfterFollow.followerIds.contains(selfUid))

        // Unfollow target member
        val unfollowRes = authRepo.toggleFollowUser("member_tamim")
        assertTrue(unfollowRes.isSuccess)
        assertEquals(false, unfollowRes.getOrNull())
        assertFalse(authRepo.getCurrentUserFlow().value?.followingIds?.contains("member_tamim") == true)
        val dirAfterUnfollow = authRepo.allUsersFlow.value.first { it.uid == "member_tamim" }
        assertFalse(dirAfterUnfollow.followerIds.contains(selfUid))
    }

    @Test
    fun `user can join and leave any team as a player with bidirectional sync`(): Unit = runBlocking {
        val authRepo = AuthRepository(
            context = context,
            auth = null,
            firestore = null,
            emailOtpService = null,
            enableCloudSync = false
        )
        val teamRepo = TeamRepository(teamDao = db.teamDao(), firestore = null, enableCloudSync = false)
        val playerRepo = PlayerRepository(playerDao = db.playerDao(), firestore = null, enableCloudSync = false)

        val foreignTeam = Team(
            id = "team_sunrise",
            name = "Sunrise Cricketers",
            shortName = "SRC",
            ownerId = "other_captain"
        )
        teamRepo.saveTeam(foreignTeam)

        val user = authRepo.updateUserCricketProfile(
            displayName = "Mustafiz Member",
            photoUrl = null,
            primaryRole = PlayerRoles.BOWLER,
            battingStyle = "Left Hand",
            bowlingStyle = "Left-arm Fast"
        ).getOrThrow()

        val syncedPlayer = playerRepo.syncUserAsPlayerProfile(user)
        assertEquals("Mustafiz Member", syncedPlayer.fullName)

        // Join foreign team
        authRepo.updateUserJoinedTeams(foreignTeam.id, join = true)
        teamRepo.joinTeamAsPlayer(foreignTeam.id, syncedPlayer.id)
        playerRepo.setPlayerTeamMembership(syncedPlayer.id, foreignTeam.id, isMember = true)

        val updatedTeam = teamRepo.getTeamById(foreignTeam.id)!!
        assertTrue(updatedTeam.playerIds.contains(syncedPlayer.id))
        val updatedPlayer = playerRepo.getPlayerById(syncedPlayer.id)!!
        assertTrue(updatedPlayer.teamIds.contains(foreignTeam.id))
        assertTrue(authRepo.getCurrentUserFlow().value?.joinedTeamIds?.contains(foreignTeam.id) == true)

        // Leave foreign team
        authRepo.updateUserJoinedTeams(foreignTeam.id, join = false)
        teamRepo.leaveTeamAsPlayer(foreignTeam.id, syncedPlayer.id)
        playerRepo.setPlayerTeamMembership(syncedPlayer.id, foreignTeam.id, isMember = false)

        val teamAfterLeave = teamRepo.getTeamById(foreignTeam.id)!!
        assertFalse(teamAfterLeave.playerIds.contains(syncedPlayer.id))
        val playerAfterLeave = playerRepo.getPlayerById(syncedPlayer.id)!!
        assertFalse(playerAfterLeave.teamIds.contains(foreignTeam.id))
    }

    @Test
    fun `postRepository defaults categoryTag to blank when omitted`(): Unit = runBlocking {
        val postRepo = PostRepository(context = context, firestore = null, enableCloudListeners = false)
        val res = postRepo.createPost(
            postId = "p_no_cat",
            authorId = "u1",
            authorName = "Sakib",
            authorAvatarUrl = null,
            feeling = "Happy",
            contentText = "দারুণ আবহাওয়া আজ!",
            mediaType = PostMediaType.NONE
        )
        assertTrue(res.isSuccess)
        val saved = postRepo.postsFlow.first().first { it.id == "p_no_cat" }
        assertEquals("", saved.categoryTag)
        assertEquals("Happy", saved.feeling)
    }
}
