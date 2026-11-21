package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.data.model.Match

object MatchShareHelper {
    /**
     * Copies the unique Match Code to the Android system clipboard.
     */
    fun copyMatchCode(context: Context, matchCode: String) {
        val cleanCode = MatchCodeGenerator.normalizeCode(matchCode)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            val clip = ClipData.newPlainText("CricUp Match Code", cleanCode)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Match Code '$cleanCode' copied to clipboard!", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Shares the match information and unique Match Code via the Android native share sheet.
     */
    fun shareMatch(context: Context, match: Match) {
        val cleanCode = MatchCodeGenerator.normalizeCode(match.matchCode)
        val scoreSummary = match.activeInnings?.let { inn ->
            "${inn.totalRuns}/${inn.wickets} (${inn.oversFormatted} ov)"
        } ?: "Upcoming / Starting soon"

        val shareText = """
Watch this cricket match live on CricUp.

Match: ${match.title}
Teams: ${match.teamA.name} vs ${match.teamB.name}
Score: $scoreSummary
Match Code: $cleanCode

Open CricUp and search using this Match Code to watch the live score.
        """.trimIndent()

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_SUBJECT, "Watch Live on CricUp: ${match.title} (Code: $cleanCode)")
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, "Share Match Code ($cleanCode)")
        context.startActivity(chooser)
    }
}
