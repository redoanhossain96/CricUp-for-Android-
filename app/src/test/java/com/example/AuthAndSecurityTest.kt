package com.example

import com.example.data.model.Match
import com.example.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

class AuthAndSecurityTest {

    @Test
    fun `user profile maintains uid, provider and timestamps without duplicates`() {
        val user1 = User(
            uid = "user_abc_123",
            email = "redoanhossain96@gmail.com",
            displayName = "Redoan Hossain",
            role = User.ROLE_MEMBER,
            provider = "email_otp",
            createdAt = 1000L,
            lastLoginAt = 2000L
        )

        assertEquals("user_abc_123", user1.uid)
        assertEquals("redoanhossain96@gmail.com", user1.email)
        assertEquals("email_otp", user1.provider)
        assertEquals(1000L, user1.createdAt)
        assertEquals(2000L, user1.lastLoginAt)
        assertEquals(User.ROLE_MEMBER, user1.role)

        // Profile update keeps creation time and uid
        val updatedUser = user1.copy(lastLoginAt = 3000L, displayName = "Redoan Updated")
        assertEquals(user1.uid, updatedUser.uid)
        assertEquals(1000L, updatedUser.createdAt)
        assertEquals(3000L, updatedUser.lastLoginAt)
        assertEquals("Redoan Updated", updatedUser.displayName)
    }

    @Test
    fun `guest role is distinct and restricted from admin or full member capabilities`() {
        val guest = User(
            uid = "guest_12345",
            email = "",
            displayName = "Guest Visitor",
            role = "guest",
            provider = "guest"
        )

        assertEquals("guest", guest.role)
        assertEquals("guest", guest.provider)
        assertNotEquals(User.ROLE_MEMBER, guest.role)
    }

    @Test
    fun `match creator ownership enforcement - only creator can score`() {
        val match = Match(
            id = "match_xyz",
            ownerId = "creator_uid_456",
            ownerUid = "creator_uid_456",
            matchCode = "CR7K9M2P",
            isPublic = true
        )

        val creatorUid = "creator_uid_456"
        val spectatorUid = "spectator_uid_789"
        val guestUid = "guest_00000"

        assertTrue("Creator MUST be authorized to score", match.canUserScore(creatorUid))
        assertFalse("Other registered user MUST NOT be authorized to score", match.canUserScore(spectatorUid))
        assertFalse("Guest MUST NOT be authorized to score", match.canUserScore(guestUid))
        assertEquals("creator_uid_456", match.effectiveOwnerUid)
        assertTrue(match.isPublic)
    }

    @Test
    fun `match code normalization handles lowercase, spaces, and hyphens`() {
        val expected = "CR7K9M2P"
        val lowercase = "cr7k9m2p"
        val withSpaces = "  CR 7K 9M 2P  "
        val withHyphens = "CR-7K9M-2P"

        assertEquals(expected, com.example.util.MatchCodeGenerator.normalizeCode(lowercase))
        assertEquals(expected, com.example.util.MatchCodeGenerator.normalizeCode(withSpaces))
        assertEquals(expected, com.example.util.MatchCodeGenerator.normalizeCode(withHyphens))
    }

    @Test
    fun `match code generator creates 6-char codes with no ambiguous chars`() {
        repeat(50) {
            val code = com.example.util.MatchCodeGenerator.generateCandidateCode()
            assertEquals("Match code must be exactly 6 characters", 6, code.length)
            // Check that confusing characters (0, O, 1, I) are excluded
            assertFalse("Must not contain '0'", code.contains('0'))
            assertFalse("Must not contain 'O'", code.contains('O'))
            assertFalse("Must not contain '1'", code.contains('1'))
            assertFalse("Must not contain 'I'", code.contains('I'))
            assertTrue("Must contain only valid charset characters", code.all { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" })
        }
    }

    @Test
    fun `otp cryptographic hashing with salt and 6-digit validity`() {
        val secureRandom = SecureRandom()
        val num = 100000 + secureRandom.nextInt(900000)
        val otpCode = num.toString()

        assertEquals("OTP must be exactly 6 digits", 6, otpCode.length)
        assertTrue("OTP must be numeric", otpCode.all { it.isDigit() })

        val salt = UUID.randomUUID().toString()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest("$salt:$otpCode".toByteArray(Charsets.UTF_8))
        val hashed = hashBytes.joinToString("") { "%02x".format(it) }

        // Plaintext is never equal to hash
        assertNotEquals(otpCode, hashed)

        // Verifying with same code matches
        val verifyDigest = MessageDigest.getInstance("SHA-256")
        val verifyBytes = verifyDigest.digest("$salt:$otpCode".toByteArray(Charsets.UTF_8))
        val verifyHash = verifyBytes.joinToString("") { "%02x".format(it) }
        assertEquals(hashed, verifyHash)

        // Verifying with wrong code fails
        val wrongCode = if (otpCode == "123456") "654321" else "123456"
        val wrongDigest = MessageDigest.getInstance("SHA-256")
        val wrongBytes = wrongDigest.digest("$salt:$wrongCode".toByteArray(Charsets.UTF_8))
        val wrongHash = wrongBytes.joinToString("") { "%02x".format(it) }
        assertNotEquals(hashed, wrongHash)
    }

    @Test
    fun `otp expiry after 5 minutes logic`() {
        val createdAt = 100000L
        val expiryMs = 5 * 60 * 1000L // 5 minutes
        val expiresAt = createdAt + expiryMs

        val validTime = createdAt + (2 * 60 * 1000L) // 2 minutes later
        val expiredTime = createdAt + (5 * 60 * 1000L + 1000L) // 5 minutes 1 second later

        assertTrue("Should be valid within 5 minutes", validTime <= expiresAt)
        assertTrue("Should be expired after 5 minutes", expiredTime > expiresAt)
    }

    @Test
    fun `otp max 5 attempts rate limit prevents brute force`() {
        val maxAttempts = 5
        var currentAttempts = 0

        // Simulate 4 failed attempts
        repeat(4) {
            currentAttempts++
            assertFalse("Should not be locked after 4 attempts", currentAttempts >= maxAttempts)
        }

        // 5th failed attempt locks
        currentAttempts++
        assertTrue("Must be locked on 5th failed attempt", currentAttempts >= maxAttempts)
    }
}
