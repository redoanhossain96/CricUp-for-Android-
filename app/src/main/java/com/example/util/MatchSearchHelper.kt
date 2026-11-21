package com.example.util

import com.example.data.model.Match
import java.util.Locale

object MatchSearchHelper {
    /**
     * Matches a [Match] against user input by:
     * - Unique match code (normalized substring or exact match)
     * - Tournament name (case-insensitive substring)
     * - Team A or Team B full name / short name (case-insensitive substring)
     * - Match title
     */
    fun matchesLiveSearchQuery(match: Match, rawQuery: String): Boolean {
        val trimmed = rawQuery.trim()
        if (trimmed.isBlank()) return true

        val lowerQuery = trimmed.lowercase(Locale.ROOT)
        val normalizedQuery = MatchCodeGenerator.normalizeCode(trimmed)

        if (normalizedQuery.isNotBlank()) {
            val codeCandidates = listOf(match.matchCode, match.code, match.id)
                .map { MatchCodeGenerator.normalizeCode(it) }
                .filter { it.isNotBlank() }
            if (codeCandidates.any { it == normalizedQuery || it.contains(normalizedQuery) }) {
                return true
            }
        }

        val textCandidates = listOfNotNull(
            match.tournamentName,
            match.teamA.name,
            match.teamA.shortName,
            match.teamB.name,
            match.teamB.shortName,
            match.title,
            match.matchCode,
            match.code
        )
        return textCandidates.any { candidate ->
            candidate.isNotBlank() && candidate.lowercase(Locale.ROOT).contains(lowerQuery)
        }
    }
}
