package com.example.util

import com.example.data.model.Match
import com.example.data.model.Player
import com.example.data.model.PlayerRoles
import java.util.Locale
import kotlin.math.roundToInt

data class FeaturedPlayerSummary(
    val id: String,
    val name: String,
    val role: String,
    val photoUrl: String? = null,
    val matchesPlayed: Int = 0,
    val battingInnings: Int = 0,
    val totalRuns: Int = 0,
    val ballsFaced: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val highestScore: Int = 0,
    val bestKnockText: String = "—",
    val totalWickets: Int = 0,
    val legalBallsBowled: Int = 0,
    val runsConceded: Int = 0,
    val bestSpellWickets: Int = 0,
    val bestSpellRuns: Int = 0,
    val bestBowlingSpellText: String = "—"
) {
    val strikeRate: Double
        get() = if (ballsFaced > 0) (totalRuns.toDouble() * 100.0) / ballsFaced else 0.0

    val averageRunPerMatch: Double
        get() = if (matchesPlayed > 0) totalRuns.toDouble() / matchesPlayed else 0.0

    val bowlingEconomy: Double
        get() = if (legalBallsBowled > 0) (runsConceded.toDouble() * 6.0) / legalBallsBowled else 0.0

    val battingRunRate: Double
        get() = if (ballsFaced > 0) (totalRuns.toDouble() * 6.0) / ballsFaced else 0.0

    val netRunRate: Double
        get() = when {
            ballsFaced > 0 && legalBallsBowled > 0 -> battingRunRate - bowlingEconomy
            ballsFaced > 0 -> battingRunRate
            legalBallsBowled > 0 -> -bowlingEconomy
            else -> 0.0
        }

    val oversFormatted: String
        get() = "${legalBallsBowled / 6}.${legalBallsBowled % 6}"

    val battingSpellSummary: String
        get() = if (battingInnings > 0 || totalRuns > 0 || ballsFaced > 0) {
            "$totalRuns রান ($bestKnockText)"
        } else {
            "—"
        }

    val bowlingSpellSummary: String
        get() = if (legalBallsBowled > 0 || totalWickets > 0) {
            "$totalWickets উই. ($bestBowlingSpellText)"
        } else {
            "—"
        }

    val formattedStrikeRate: String
        get() = String.format(Locale.US, "%.1f", strikeRate)

    val formattedArpm: String
        get() = String.format(Locale.US, "%.1f", averageRunPerMatch)

    val formattedNrr: String
        get() = if (ballsFaced <= 0 && legalBallsBowled <= 0) {
            "0.00"
        } else {
            String.format(Locale.US, "%+.2f", netRunRate)
        }

    val impactScore: Double
        get() {
            val srBonus = if (ballsFaced >= 10 && strikeRate >= 130.0) 12.0 else 0.0
            val econBonus = if (legalBallsBowled >= 12 && bowlingEconomy <= 6.5) 10.0 else 0.0
            return totalRuns.toDouble() +
                (totalWickets * 25.0) +
                (sixes * 2.0) +
                fours.toDouble() +
                (matchesPlayed * 2.0) +
                srBonus +
                econBonus
        }
}

object FeaturedPlayersAggregator {

    private class MutableAccumulator(
        var id: String,
        var name: String,
        var role: String = PlayerRoles.ALL_ROUNDER,
        var photoUrl: String? = null
    ) {
        val matchIds = LinkedHashSet<String>()
        var extraMatches = 0
        var battingInnings = 0
        var totalRuns = 0
        var ballsFaced = 0
        var fours = 0
        var sixes = 0
        var highestScore = 0
        var bestKnockBalls = 0
        var bestKnockNotOut = false

        var totalWickets = 0
        var legalBallsBowled = 0
        var runsConceded = 0
        var bestSpellWickets = -1
        var bestSpellRuns = Int.MAX_VALUE
        var bestSpellBalls = 0

        fun updateBestKnock(runs: Int, balls: Int, isOut: Boolean) {
            val better = runs > highestScore ||
                (runs == highestScore && !isOut && bestKnockNotOut.not()) ||
                (runs == highestScore && isOut == bestKnockNotOut && balls in 1 until bestKnockBalls)
            if (better || battingInnings == 0) {
                if (runs > highestScore || (runs == highestScore && bestKnockBalls == 0)) {
                    highestScore = runs
                    bestKnockBalls = balls
                    bestKnockNotOut = !isOut
                }
            }
        }

        fun updateBestBowlingSpell(wickets: Int, conceded: Int, balls: Int) {
            if (wickets <= 0 && balls <= 0) return
            val isBetter = wickets > bestSpellWickets ||
                (wickets == bestSpellWickets && conceded < bestSpellRuns)
            if (isBetter) {
                bestSpellWickets = wickets
                bestSpellRuns = conceded
                bestSpellBalls = balls
            }
        }

        fun toSummary(): FeaturedPlayerSummary {
            val totalMatches = matchIds.size + extraMatches
            val knockText = when {
                battingInnings == 0 && totalRuns == 0 -> "—"
                bestKnockBalls > 0 -> {
                    val star = if (bestKnockNotOut) "*" else ""
                    "$highestScore$star($bestKnockBalls)"
                }
                else -> "$highestScore"
            }

            val bowlSpellText = when {
                legalBallsBowled <= 0 && totalWickets <= 0 -> "—"
                bestSpellWickets >= 0 && bestSpellRuns != Int.MAX_VALUE -> {
                    val oversPart = if (bestSpellBalls > 0) " • ${bestSpellBalls / 6}.${bestSpellBalls % 6} Ov" else ""
                    "$bestSpellWickets/$bestSpellRuns$oversPart"
                }
                else -> "$totalWickets/$runsConceded • ${legalBallsBowled / 6}.${legalBallsBowled % 6} Ov"
            }

            return FeaturedPlayerSummary(
                id = id,
                name = name.ifBlank { "Unknown Player" },
                role = role.ifBlank { PlayerRoles.ALL_ROUNDER },
                photoUrl = photoUrl,
                matchesPlayed = totalMatches,
                battingInnings = battingInnings,
                totalRuns = totalRuns,
                ballsFaced = ballsFaced,
                fours = fours,
                sixes = sixes,
                highestScore = highestScore,
                bestKnockText = knockText,
                totalWickets = totalWickets,
                legalBallsBowled = legalBallsBowled,
                runsConceded = runsConceded,
                bestSpellWickets = bestSpellWickets.coerceAtLeast(0),
                bestSpellRuns = if (bestSpellRuns == Int.MAX_VALUE) 0 else bestSpellRuns,
                bestBowlingSpellText = bowlSpellText
            )
        }
    }

    fun exclusionToken(id: String, name: String): String {
        val cleanId = id.trim()
        return if (cleanId.isNotBlank() && !cleanId.startsWith("name:")) {
            "id:$cleanId"
        } else {
            "name:${name.trim().lowercase(Locale.ROOT)}"
        }
    }

    private fun isExcludedSummary(summary: FeaturedPlayerSummary, excludedKeys: Set<String>): Boolean {
        if (excludedKeys.isEmpty()) return false
        val cleanId = summary.id.trim()
        val normNameKey = "name:${summary.name.trim().lowercase(Locale.ROOT)}"
        return excludedKeys.contains(cleanId) ||
            excludedKeys.contains("id:$cleanId") ||
            excludedKeys.contains(normNameKey)
    }

    /**
     * Automatically aggregates career batting & bowling performance across ALL users' matches
     * synced to Firestore/Room plus registered player profiles, returning top performers ranked by MVP impact.
     */
    fun computeTopPlayers(
        matches: List<Match>,
        dbPlayers: List<Player>,
        limit: Int = 10,
        excludedKeys: Set<String> = emptySet()
    ): List<FeaturedPlayerSummary> {
        val accumulators = LinkedHashMap<String, MutableAccumulator>()
        val nameToKey = HashMap<String, String>()

        fun normalizeName(name: String): String = name.trim().lowercase(Locale.ROOT)

        fun getOrCreate(playerId: String?, playerName: String): MutableAccumulator {
            val cleanId = playerId?.trim().orEmpty()
            val normName = normalizeName(playerName)

            val normKey = if (normName.isNotBlank()) nameToKey[normName] else null
            val existingKey = when {
                cleanId.isNotBlank() && accumulators.containsKey(cleanId) -> cleanId
                normKey != null -> normKey
                cleanId.isNotBlank() -> cleanId
                else -> "name:$normName"
            }

            val acc = accumulators.getOrPut(existingKey) {
                MutableAccumulator(
                    id = cleanId.ifBlank { existingKey },
                    name = playerName.trim()
                )
            }
            if (acc.name.isBlank() && playerName.isNotBlank()) {
                acc.name = playerName.trim()
            }
            if (cleanId.isNotBlank() && acc.id.startsWith("name:")) {
                acc.id = cleanId
            }
            if (normName.isNotBlank() && !nameToKey.containsKey(normName)) {
                nameToKey[normName] = existingKey
            }
            return acc
        }

        // 1. Seed identity metadata (name, role, photoUrl) from global players collection
        dbPlayers.forEach { p ->
            if (p.isArchived || p.fullName.isBlank()) return@forEach
            val acc = getOrCreate(p.id, p.fullName)
            if (p.primaryRole.isNotBlank()) acc.role = p.primaryRole
            if (!p.photoUrl.isNullOrBlank()) acc.photoUrl = p.photoUrl
        }

        // 2. Aggregate match scorecards & squad snapshots across all users' matches
        matches.forEach { match ->
            val matchKey = match.id.ifBlank { "${match.title}_${match.matchDate}" }

            // Enrich photos & roles from match squads
            (match.playingXI_A + match.playingXI_B + match.fullSquad_A + match.fullSquad_B).forEach { snap ->
                if (snap.name.isBlank() && snap.id.isBlank()) return@forEach
                val acc = getOrCreate(snap.id, snap.name)
                if (acc.photoUrl.isNullOrBlank() && !snap.photoUrl.isNullOrBlank()) {
                    acc.photoUrl = snap.photoUrl
                }
                if (snap.role.isNotBlank() && acc.role == PlayerRoles.ALL_ROUNDER) {
                    acc.role = snap.role
                }
            }

            // Record participation for playing XI when available
            (match.playingXI_A + match.playingXI_B).forEach { snap ->
                if (snap.name.isNotBlank() || snap.id.isNotBlank()) {
                    getOrCreate(snap.id, snap.name).matchIds.add(matchKey)
                }
            }

            listOfNotNull(match.firstInnings, match.secondInnings).forEach { innings ->
                innings.batters.forEach { batter ->
                    if (batter.playerName.isBlank() && batter.playerId.isBlank()) return@forEach
                    val acc = getOrCreate(batter.playerId, batter.playerName)
                    acc.matchIds.add(matchKey)
                    val didBat = batter.balls > 0 || batter.runs > 0 || batter.isOut
                    if (didBat) {
                        acc.updateBestKnock(batter.runs, batter.balls, batter.isOut)
                        acc.battingInnings += 1
                    }
                    acc.totalRuns += batter.runs
                    acc.ballsFaced += batter.balls
                    acc.fours += batter.fours
                    acc.sixes += batter.sixes
                }

                innings.bowlers.forEach { bowler ->
                    if (bowler.playerName.isBlank() && bowler.playerId.isBlank()) return@forEach
                    val acc = getOrCreate(bowler.playerId, bowler.playerName)
                    acc.matchIds.add(matchKey)
                    acc.totalWickets += bowler.wickets
                    acc.legalBallsBowled += bowler.legalBalls
                    acc.runsConceded += bowler.runsConceded
                    acc.updateBestBowlingSpell(bowler.wickets, bowler.runsConceded, bowler.legalBalls)
                }
            }
        }

        // 3. Fold in pre-populated Player.stats only if no match-derived innings were counted for that player
        dbPlayers.forEach { p ->
            if (p.isArchived || p.fullName.isBlank()) return@forEach
            val acc = getOrCreate(p.id, p.fullName)
            val hasMatchRecords = acc.totalRuns > 0 || acc.totalWickets > 0 || acc.legalBallsBowled > 0 || acc.battingInnings > 0
            val s = p.stats
            if (!hasMatchRecords && (s.runs > 0 || s.wickets > 0 || s.matches > 0 || s.oversBowled > 0.0)) {
                acc.extraMatches = maxOf(s.matches, if (s.innings > 0) s.innings else 1)
                acc.battingInnings = maxOf(s.innings, if (s.runs > 0 || s.ballsFaced > 0) 1 else 0)
                acc.totalRuns = s.runs
                acc.ballsFaced = s.ballsFaced
                acc.fours = s.fours
                acc.sixes = s.sixes
                acc.highestScore = if (s.highestScore > 0) s.highestScore else s.runs
                acc.totalWickets = s.wickets
                val wholeOvers = s.oversBowled.toInt()
                val remBalls = ((s.oversBowled - wholeOvers) * 10.0).roundToInt().coerceIn(0, 5)
                acc.legalBallsBowled = (wholeOvers * 6) + remBalls
                acc.runsConceded = s.runsConceded
                if (s.bestBowlingWickets > 0) {
                    acc.bestSpellWickets = s.bestBowlingWickets
                    acc.bestSpellRuns = s.bestBowlingRuns
                    acc.bestSpellBalls = if (acc.extraMatches <= 1) acc.legalBallsBowled else 0
                } else if (s.wickets > 0 || acc.legalBallsBowled > 0) {
                    acc.bestSpellWickets = s.wickets
                    acc.bestSpellRuns = s.runsConceded
                    acc.bestSpellBalls = acc.legalBallsBowled
                }
            }
        }

        return accumulators.values
            .map { it.toSummary() }
            .filter { it.totalRuns > 0 || it.ballsFaced > 0 || it.totalWickets > 0 || it.legalBallsBowled > 0 }
            .filterNot { isExcludedSummary(it, excludedKeys) }
            .sortedWith(
                compareByDescending<FeaturedPlayerSummary> { it.impactScore }
                    .thenByDescending { it.totalRuns }
                    .thenByDescending { it.totalWickets }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )
            .take(limit)
    }

    /**
     * Computes full career stats (matches, runs, wickets, SR, ARPM, NRR, best spells)
     * for a specific app user across all matches and player profiles.
     */
    fun computeUserCareerStats(
        user: com.example.data.model.User,
        matches: List<Match>,
        dbPlayers: List<Player>
    ): FeaturedPlayerSummary {
        val candidateIds = setOf(user.uid.trim(), user.linkedPlayerId.trim())
            .filter { it.isNotBlank() }
            .toSet()
        val normUserName = user.displayName.trim().lowercase(Locale.ROOT)
        val isPlaceholder = normUserName.isBlank() ||
            normUserName == "member" ||
            normUserName == "guest" ||
            normUserName.startsWith("user ")

        fun matchesUser(playerId: String?, playerName: String?): Boolean {
            val cleanId = playerId?.trim().orEmpty()
            if (cleanId.isNotBlank() && cleanId in candidateIds) return true
            if (!isPlaceholder) {
                val normName = playerName?.trim()?.lowercase(Locale.ROOT).orEmpty()
                if (normName.isNotBlank() && normName == normUserName) return true
            }
            return false
        }

        val acc = MutableAccumulator(
            id = user.effectivePlayerId.ifBlank { user.uid },
            name = user.displayName.ifBlank { "CricUp Member" },
            role = user.primaryRole.ifBlank { PlayerRoles.ALL_ROUNDER },
            photoUrl = user.photoUrl
        )

        val linkedDbPlayers = dbPlayers.filter { !it.isArchived && matchesUser(it.id, it.fullName) }
        linkedDbPlayers.forEach { p ->
            if (acc.photoUrl.isNullOrBlank() && !p.photoUrl.isNullOrBlank()) acc.photoUrl = p.photoUrl
            if (p.primaryRole.isNotBlank() && acc.role.isBlank()) acc.role = p.primaryRole
        }

        matches.forEach { match ->
            val matchKey = match.id.ifBlank { "${match.title}_${match.matchDate}" }
            (match.playingXI_A + match.playingXI_B).forEach { snap ->
                if (matchesUser(snap.id, snap.name)) {
                    acc.matchIds.add(matchKey)
                }
            }
            listOfNotNull(match.firstInnings, match.secondInnings).forEach { innings ->
                innings.batters.forEach { batter ->
                    if (matchesUser(batter.playerId, batter.playerName)) {
                        acc.matchIds.add(matchKey)
                        val didBat = batter.balls > 0 || batter.runs > 0 || batter.isOut
                        if (didBat) {
                            acc.updateBestKnock(batter.runs, batter.balls, batter.isOut)
                            acc.battingInnings += 1
                        }
                        acc.totalRuns += batter.runs
                        acc.ballsFaced += batter.balls
                        acc.fours += batter.fours
                        acc.sixes += batter.sixes
                    }
                }
                innings.bowlers.forEach { bowler ->
                    if (matchesUser(bowler.playerId, bowler.playerName)) {
                        acc.matchIds.add(matchKey)
                        acc.totalWickets += bowler.wickets
                        acc.legalBallsBowled += bowler.legalBalls
                        acc.runsConceded += bowler.runsConceded
                        acc.updateBestBowlingSpell(bowler.wickets, bowler.runsConceded, bowler.legalBalls)
                    }
                }
            }
        }

        val hasMatchRecords = acc.totalRuns > 0 || acc.totalWickets > 0 || acc.legalBallsBowled > 0 || acc.battingInnings > 0
        if (!hasMatchRecords) {
            linkedDbPlayers.firstOrNull { s ->
                s.stats.runs > 0 || s.stats.wickets > 0 || s.stats.matches > 0 || s.stats.oversBowled > 0.0
            }?.let { p ->
                val s = p.stats
                acc.extraMatches = maxOf(s.matches, if (s.innings > 0) s.innings else 1)
                acc.battingInnings = maxOf(s.innings, if (s.runs > 0 || s.ballsFaced > 0) 1 else 0)
                acc.totalRuns = s.runs
                acc.ballsFaced = s.ballsFaced
                acc.fours = s.fours
                acc.sixes = s.sixes
                acc.highestScore = if (s.highestScore > 0) s.highestScore else s.runs
                acc.totalWickets = s.wickets
                val wholeOvers = s.oversBowled.toInt()
                val remBalls = ((s.oversBowled - wholeOvers) * 10.0).roundToInt().coerceIn(0, 5)
                acc.legalBallsBowled = (wholeOvers * 6) + remBalls
                acc.runsConceded = s.runsConceded
                if (s.bestBowlingWickets > 0) {
                    acc.bestSpellWickets = s.bestBowlingWickets
                    acc.bestSpellRuns = s.bestBowlingRuns
                    acc.bestSpellBalls = if (acc.extraMatches <= 1) acc.legalBallsBowled else 0
                }
            }
        }

        return acc.toSummary()
    }
}
