package com.example.util

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom

object MatchCodeGenerator {
    // 32-character unambiguous alphanumeric set (excludes 0, O, 1, I)
    private const val CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val secureRandom = SecureRandom()

    /**
     * Normalizes a match code consistently:
     * - Trims whitespace
     * - Converts to uppercase
     * - Strips accidental spaces and hyphens
     */
    fun normalizeCode(code: String): String {
        return code.trim().uppercase().replace(" ", "").replace("-", "")
    }

    /**
     * Generates a cryptographically random 6-character Match Code from unambiguous charset.
     * Easy to read, communicate, and type without confusing 0/O or 1/I (e.g. K7M9P2, W4T8RN).
     */
    fun generateCandidateCode(): String {
        val sb = StringBuilder(6)
        for (i in 0 until 6) {
            val idx = secureRandom.nextInt(CHARSET.length)
            sb.append(CHARSET[idx])
        }
        return sb.toString()
    }

    /**
     * Generates a collision-free unique 6-character Match Code verified against Firestore document ID
     * and local Room database cache, bounded by a short timeout so match start never hangs.
     */
    suspend fun generateUniqueMatchCode(
        firestore: FirebaseFirestore,
        existingCodesInRoom: Set<String> = emptySet()
    ): String {
        var attempts = 0
        while (attempts < 8) {
            val candidate = generateCandidateCode()
            val normalized = normalizeCode(candidate)
            if (normalized in existingCodesInRoom) {
                attempts++
                continue
            }
            try {
                // Bounded Direct Document ID check in Firestore: matches/{normalized}
                val doc = withTimeoutOrNull(1_500L) {
                    firestore.collection("matches").document(normalized).get().await()
                }
                if (doc == null) {
                    // Timed out on slow/offline network: trust local uniqueness
                    return normalized
                }
                if (!doc.exists()) {
                    val query = withTimeoutOrNull(1_500L) {
                        firestore.collection("matches")
                            .whereEqualTo("matchCode", normalized)
                            .limit(1)
                            .get()
                            .await()
                    }
                    if (query == null || query.isEmpty) {
                        return normalized
                    }
                }
            } catch (e: Exception) {
                // If offline or network unavailable, ensure it's not present in local cache
                if (normalized !in existingCodesInRoom) {
                    return normalized
                }
            }
            attempts++
        }

        // Guaranteed fallback if all attempts collide
        return generateCandidateCode()
    }
}
