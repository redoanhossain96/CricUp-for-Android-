package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.InningsScorecard
import com.example.data.model.Match
import com.example.data.model.MatchPlayerSnapshot
import com.example.data.model.MatchStatus
import com.example.data.model.MatchTeamSnapshot
import com.example.data.model.Umpire
import com.example.data.repository.MatchRepository
import com.example.data.repository.ScoringRepository
import com.example.data.repository.UmpireRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MatchStartAndUmpireStabilityTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var umpireRepository: UmpireRepository
    private lateinit var matchRepository: MatchRepository
    private lateinit var scoringRepository: ScoringRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        umpireRepository = UmpireRepository(
            umpireDao = db.umpireDao(),
            firestore = null,
            enableCloudSync = false
        )
        matchRepository = MatchRepository(
            matchDao = db.matchDao(),
            ballEventDao = db.ballEventDao(),
            firestore = null,
            enableCloudListeners = false
        )
        scoringRepository = ScoringRepository(
            ballEventDao = db.ballEventDao(),
            matchDao = db.matchDao(),
            matchRepository = matchRepository,
            firestore = null,
            enableCloudListeners = false
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `creating match with umpires and heavy base64 player photos compacts payload under SQLite limit and starts cleanly`(): Unit = runBlocking {
        val ownerUid = "user_crash_fix_1"
        val bigPhoto = "data:image/jpeg;base64," + "A".repeat(95_000)
        val teamLogo = "data:image/jpeg;base64," + "B".repeat(40_000)

        val squadA = (1..16).map { i ->
            MatchPlayerSnapshot(
                id = "pa_$i",
                name = "Player A$i",
                role = "All-Rounder",
                isCaptain = i == 1,
                photoUrl = bigPhoto
            )
        }
        val squadB = (1..16).map { i ->
            MatchPlayerSnapshot(
                id = "pb_$i",
                name = "Player B$i",
                role = "All-Rounder",
                isCaptain = i == 1,
                photoUrl = bigPhoto
            )
        }
        val xiA = squadA.take(11)
        val xiB = squadB.take(11)

        // Pre-register umpire with photo and association
        val umpirePhoto = "data:image/jpeg;base64," + "U".repeat(25_000)
        umpireRepository.saveUmpire(
            Umpire(
                id = "ump_1",
                fullName = "Sharfuddoula Saikat",
                association = "BCB Elite Panel",
                phone = "01711000000",
                photoUrl = umpirePhoto,
                certificationLevel = "International",
                creatorId = ownerUid
            )
        )

        // Simulate CreateMatchScreen Step 6 saving umpire name before creating match
        val reSaveRes = umpireRepository.saveUmpire(
            Umpire(fullName = "Sharfuddoula Saikat", creatorId = ownerUid)
        )
        assertTrue(reSaveRes.isSuccess)

        val match = Match(
            id = "K7M9P2",
            matchCode = "K7M9P2",
            code = "K7M9P2",
            title = "Final Cup",
            format = "T20",
            totalOvers = 20,
            venue = "Mirpur",
            teamA = MatchTeamSnapshot("tA", "Dhaka Tigers", "DHT", teamLogo),
            teamB = MatchTeamSnapshot("tB", "Chattogram Kings", "CTK", teamLogo),
            team1CaptainId = "pa_1",
            team2CaptainId = "pb_1",
            fullSquad_A = squadA,
            fullSquad_B = squadB,
            playingXI_A = xiA,
            playingXI_B = xiB,
            umpire1 = "Sharfuddoula Saikat",
            umpire2 = "Masudur Rahman",
            tossWinnerTeamId = "tA",
            tossDecision = "BAT",
            battingFirstTeamId = "tA",
            bowlingFirstTeamId = "tB",
            status = MatchStatus.LIVE,
            ownerId = ownerUid,
            ownerUid = ownerUid,
            isPublic = true,
            isOfflineScored = false,
            firstInnings = InningsScorecard(
                inningsNumber = 1,
                battingTeamId = "tA",
                battingTeamName = "Dhaka Tigers",
                bowlingTeamId = "tB",
                bowlingTeamName = "Chattogram Kings"
            )
        )

        val createRes = matchRepository.createMatch(match)
        assertTrue("Match creation must succeed", createRes.isSuccess)
        val createdId = createRes.getOrThrow()
        assertEquals("K7M9P2", createdId)

        // Stored SQLite row must be strictly under the 700 KB safe storage limit (well under 2 MB CursorWindow)
        val rawEntity = db.matchDao().getMatchById(createdId)
        assertNotNull(rawEntity)
        assertTrue(
            "Stored match JSON (${rawEntity!!.jsonPayload.length} chars) must stay safely below SQLite/Firestore limits",
            rawEntity.jsonPayload.length <= MatchRepository.MAX_SAFE_STORAGE_JSON_CHARS
        )

        // Match flow loads smoothly for LiveScoringScreen and preserves umpires & team logos
        val loaded = withTimeout(3_000) {
            matchRepository.getMatchFlowById(createdId).first { it != null }
        }
        assertNotNull(loaded)
        assertEquals("Sharfuddoula Saikat", loaded!!.umpire1)
        assertEquals("Masudur Rahman", loaded.umpire2)
        assertEquals(teamLogo, loaded.teamA.logoUrl)
        assertEquals(11, loaded.playingXI_A.size)
        assertEquals(11, loaded.playingXI_B.size)

        // Opening selection ("ম্যাচ শুরু করুন") succeeds cleanly
        val openingRes = scoringRepository.selectOpeningPlayers(
            matchId = createdId,
            strikerId = "pa_1",
            strikerName = "Player A1",
            nonStrikerId = "pa_2",
            nonStrikerName = "Player A2",
            bowlerId = "pb_1",
            bowlerName = "Player B1"
        )
        assertTrue(openingRes.isSuccess)
        val startedMatch = openingRes.getOrThrow()
        assertEquals("pa_1", startedMatch.currentStrikerId)
        assertEquals("pa_2", startedMatch.currentNonStrikerId)
        assertEquals("pb_1", startedMatch.currentBowlerId)
    }

    @Test
    fun `re-saving existing umpire by name during match start preserves photo and metadata`(): Unit = runBlocking {
        val ownerUid = "user_umpire_preserve"
        val savedPhoto = "data:image/jpeg;base64," + "Z".repeat(5_000)

        umpireRepository.saveUmpire(
            Umpire(
                id = "ump_keep_1",
                fullName = "Gazi Sohel",
                association = "Dhaka Umpires",
                phone = "01812345678",
                photoUrl = savedPhoto,
                certificationLevel = "National",
                matchesOfficiated = 14,
                creatorId = ownerUid
            )
        )

        // Match start saves umpire with only fullName & creatorId
        umpireRepository.saveUmpire(
            Umpire(fullName = "gazi sohel", creatorId = ownerUid)
        )

        val umpires = withTimeout(3_000) {
            umpireRepository.getMyUmpiresFlow(ownerUid).first { it.isNotEmpty() }
        }
        assertEquals(1, umpires.size)
        val saved = umpires.first()
        assertEquals("ump_keep_1", saved.id)
        assertEquals(savedPhoto, saved.photoUrl)
        assertEquals("Dhaka Umpires", saved.association)
        assertEquals("01812345678", saved.phone)
        assertEquals("National", saved.certificationLevel)
        assertEquals(14, saved.matchesOfficiated)
    }

    @Test
    fun `offline solo match with heavy squads and umpires starts immediately without network`(): Unit = runBlocking {
        val ownerUid = "user_offline_solo"
        val bigPhoto = "data:image/jpeg;base64," + "C".repeat(120_000)
        val xiA = (1..11).map { MatchPlayerSnapshot("oa_$it", "Off A$it", photoUrl = bigPhoto) }
        val xiB = (1..11).map { MatchPlayerSnapshot("ob_$it", "Off B$it", photoUrl = bigPhoto) }

        val offlineMatch = Match(
            id = "OFFLINE_TEST1234",
            matchCode = "OFFLINE",
            code = "OFFLINE",
            title = "Practice Match",
            teamA = MatchTeamSnapshot("t1", "Alpha", "ALP"),
            teamB = MatchTeamSnapshot("t2", "Beta", "BET"),
            fullSquad_A = xiA,
            fullSquad_B = xiB,
            playingXI_A = xiA,
            playingXI_B = xiB,
            umpire1 = "Self / Solo",
            battingFirstTeamId = "t1",
            bowlingFirstTeamId = "t2",
            status = MatchStatus.LIVE,
            ownerId = ownerUid,
            ownerUid = ownerUid,
            isPublic = false,
            isOfflineScored = true
        )

        val res = matchRepository.createMatch(offlineMatch)
        assertTrue(res.isSuccess)
        val id = res.getOrThrow()
        val entity = db.matchDao().getMatchById(id)
        assertNotNull(entity)
        assertTrue(entity!!.jsonPayload.length <= MatchRepository.MAX_SAFE_STORAGE_JSON_CHARS)

        val loaded = matchRepository.getMatchById(id)
        assertNotNull(loaded)
        assertTrue(loaded!!.isOfflineOnlyMatch())
        assertEquals("Self / Solo", loaded.umpire1)
    }
}
