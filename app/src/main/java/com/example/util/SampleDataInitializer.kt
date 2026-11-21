package com.example.util

import android.content.Context
import com.example.di.AppContainer

object SampleDataInitializer {
    private const val PREFS_NAME = "cricup_prefs"
    private const val KEY_PURGED = "legacy_dummy_purged_v1"

    suspend fun seedInitialDataIfEmpty(container: AppContainer, context: Context? = null) {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs?.getBoolean(KEY_PURGED, false) == true) {
            return
        }

        // Purge any legacy dummy teams from previous runs
        val dummyTeamIds = listOf("team_dhaka_01", "team_ctg_02", "team_sylhet_03")
        for (teamId in dummyTeamIds) {
            try {
                container.teamRepository.deleteTeam(teamId)
            } catch (_: Exception) {}
        }

        // Purge any legacy dummy players from previous runs
        val dummyPlayerIds = listOf(
            "p_shakib_01",
            "p_tamim_02",
            "p_litton_03",
            "p_mustafiz_04",
            "p_taskin_05",
            "p_mehidy_06"
        )
        for (playerId in dummyPlayerIds) {
            try {
                container.playerRepository.deletePlayer(playerId)
            } catch (_: Exception) {}
        }

        // Clean up legacy dummy matches or tournaments if needed
        try {
            container.matchRepository.deleteMatch("match_recent_01")
        } catch (_: Exception) {}
        try {
            container.tournamentRepository.deleteTournament("tourn_csm_2026")
        } catch (_: Exception) {}

        prefs?.edit()?.putBoolean(KEY_PURGED, true)?.apply()
    }
}
