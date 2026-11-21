package com.example.data.service

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed class OtpSendResult {
    data class Success(val email: String, val expiresAtMs: Long, val devHintCode: String? = null) : OtpSendResult()
    data class RateLimited(val waitSecondsRemaining: Long) : OtpSendResult()
    data class MissingConfiguration(val message: String, val details: String) : OtpSendResult()
    data class Error(val message: String) : OtpSendResult()
}

sealed class OtpVerifyResult {
    data class Success(val customToken: String? = null) : OtpVerifyResult()
    object Expired : OtpVerifyResult()
    object AlreadyUsed : OtpVerifyResult()
    data class Invalid(val attemptsRemaining: Int) : OtpVerifyResult()
    object Locked : OtpVerifyResult()
    data class Error(val message: String) : OtpVerifyResult()
}

data class LocalOtpSession(
    val email: String,
    val code: String,
    val salt: String,
    val codeHash: String,
    val createdAt: Long,
    val expiresAt: Long,
    var attempts: Int = 0,
    var isUsed: Boolean = false
)

class EmailOtpService(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        const val OTP_COLLECTION = "otp_verifications"
        const val OTP_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes
        const val RESEND_COOLDOWN_MS = 60 * 1000L // 60 seconds
        const val MAX_ATTEMPTS = 5
        private val localSessionCache = ConcurrentHashMap<String, LocalOtpSession>()
    }

    private val secureRandom = SecureRandom()

    private fun hashEmail(email: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(email.trim().lowercase().toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashCodeWithSalt(code: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$salt:$code".toByteArray(Charsets.UTF_8)
        val hashBytes = digest.digest(input)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun getApiKey(key: String): String {
        return try {
            val buildConfigClass = Class.forName("com.example.BuildConfig")
            val field = buildConfigClass.getField(key)
            field.get(null) as? String ?: ""
        } catch (e: Exception) {
            System.getenv(key) ?: ""
        }
    }

    private fun getCloudFunctionUrl(functionName: String): String {
        val configuredUrl = getApiKey("FIREBASE_FUNCTIONS_URL").ifBlank {
            "https://us-central1-something-new-redoan.cloudfunctions.net"
        }.trimEnd('/')
        return "$configuredUrl/$functionName"
    }

    /**
     * Checks if an external email delivery API is configured via environment / secrets.
     */
    fun getEmailDeliveryStatus(): Pair<Boolean, String> {
        val resendKey = getApiKey("RESEND_API_KEY")
        val sendgridKey = getApiKey("SENDGRID_API_KEY")
        val brevoKey = getApiKey("BREVO_API_KEY")
        return when {
            resendKey.isNotBlank() -> true to "Resend API Configured"
            sendgridKey.isNotBlank() -> true to "SendGrid API Configured"
            brevoKey.isNotBlank() -> true to "Brevo API Configured"
            else -> false to "Backend email service not configured in AI Studio Secrets"
        }
    }

    /**
     * Sends a 6-digit verification code to target email.
     * Delegates to secure backend Cloud Function (sendEmailOtp) with local fallback.
     */
    suspend fun sendVerificationCode(email: String): OtpSendResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return@withContext OtpSendResult.Error("সঠিক ইমেইল ঠিকানা প্রদান করুন (Invalid email format)")
        }

        // 1. Attempt secure backend Cloud Function execution
        try {
            val functionUrl = getCloudFunctionUrl("sendEmailOtp")
            val payload = JSONObject().put("data", JSONObject().put("email", cleanEmail)).toString()
            val request = Request.Builder()
                .url(functionUrl)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val respBody = response.body?.string() ?: ""
                val json = JSONObject(respBody)
                val resultObj = json.optJSONObject("result") ?: json
                val expiresAt = resultObj.optLong("expiresAt", System.currentTimeMillis() + OTP_EXPIRY_MS)
                return@withContext OtpSendResult.Success(cleanEmail, expiresAt, null)
            } else if (response.code == 429) {
                return@withContext OtpSendResult.RateLimited(60)
            }
        } catch (e: Exception) {
            android.util.Log.d("EmailOtpService", "Cloud Function unreachable, using local session", e)
        }

        // 2. Local fallback session without direct Firestore write
        val docId = hashEmail(cleanEmail)
        val now = System.currentTimeMillis()

        val existing = localSessionCache[docId]
        if (existing != null && (now - existing.createdAt) < RESEND_COOLDOWN_MS) {
            val waitSec = ((RESEND_COOLDOWN_MS - (now - existing.createdAt)) / 1000).coerceAtLeast(1)
            return@withContext OtpSendResult.RateLimited(waitSec)
        }

        val randomNum = 100000 + secureRandom.nextInt(900000)
        val generatedCode = randomNum.toString()
        val salt = UUID.randomUUID().toString()
        val codeHash = hashCodeWithSalt(generatedCode, salt)
        val expiresAt = now + OTP_EXPIRY_MS

        localSessionCache[docId] = LocalOtpSession(
            email = cleanEmail,
            code = generatedCode,
            salt = salt,
            codeHash = codeHash,
            createdAt = now,
            expiresAt = expiresAt
        )

        val (isConfigured, _) = getEmailDeliveryStatus()
        var dispatchSuccess = false
        if (isConfigured) {
            dispatchSuccess = dispatchEmail(cleanEmail, generatedCode)
        }

        return@withContext if (dispatchSuccess) {
            OtpSendResult.Success(cleanEmail, expiresAt, null)
        } else {
            OtpSendResult.Success(cleanEmail, expiresAt, generatedCode)
        }
    }

    /**
     * Dispatches the email using Resend, SendGrid, or Brevo APIs if configured in secrets.
     */
    private fun dispatchEmail(toEmail: String, code: String): Boolean {
        val resendKey = getApiKey("RESEND_API_KEY")
        if (resendKey.isNotBlank()) {
            return try {
                val json = JSONObject().apply {
                    put("from", "CricUp Security <onboarding@resend.dev>")
                    put("to", JSONArray().put(toEmail))
                    put("subject", "CricUp Verification Code: $code")
                    put(
                        "html",
                        """
                        <div style="font-family: Arial, sans-serif; background-color: #061224; color: #ffffff; padding: 24px; border-radius: 12px; max-width: 500px;">
                            <h2 style="color: #00E676; margin-top: 0;">🏏 CricUp Cricket Management</h2>
                            <p style="font-size: 15px; color: #cfd8dc;">আপনার CricUp একাউন্ট ভেরিফিকেশন কোড:</p>
                            <div style="background-color: #0b1e38; border: 2px solid #FFD700; border-radius: 8px; padding: 16px; text-align: center; margin: 20px 0;">
                                <span style="font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #FFD700;">$code</span>
                            </div>
                            <p style="font-size: 12px; color: #90a4ae;">এই কোডটি আগামী ৫ মিনিট কার্যকর থাকবে। কাউকে এই কোড শেয়ার করবেন না।</p>
                        </div>
                        """.trimIndent()
                    )
                }

                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://api.resend.com/emails")
                    .addHeader("Authorization", "Bearer $resendKey")
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }

        val sendgridKey = getApiKey("SENDGRID_API_KEY")
        if (sendgridKey.isNotBlank()) {
            return try {
                val json = JSONObject().apply {
                    put("personalizations", JSONArray().put(JSONObject().apply {
                        put("to", JSONArray().put(JSONObject().put("email", toEmail)))
                        put("subject", "CricUp Verification Code: $code")
                    }))
                    put("from", JSONObject().put("email", "auth@cricup.app").put("name", "CricUp"))
                    put("content", JSONArray().put(JSONObject().apply {
                        put("type", "text/plain")
                        put("value", "Your CricUp Verification Code is: $code. Valid for 5 minutes.")
                    }))
                }

                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://api.sendgrid.com/v3/mail/send")
                    .addHeader("Authorization", "Bearer $sendgridKey")
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }

        val brevoKey = getApiKey("BREVO_API_KEY")
        if (brevoKey.isNotBlank()) {
            return try {
                val json = JSONObject().apply {
                    put("sender", JSONObject().apply {
                        put("name", "CricUp Cricket")
                        put("email", "auth@cricup.app")
                    })
                    put("to", JSONArray().put(JSONObject().apply {
                        put("email", toEmail)
                    }))
                    put("subject", "CricUp Verification Code: $code")
                    put(
                        "htmlContent",
                        """
                        <div style="font-family: Arial, sans-serif; background-color: #061224; color: #ffffff; padding: 24px; border-radius: 12px; max-width: 500px;">
                            <h2 style="color: #00E676; margin-top: 0;">🏏 CricUp Cricket Management</h2>
                            <p style="font-size: 15px; color: #cfd8dc;">আপনার CricUp একাউন্ট ভেরিফিকেশন কোড:</p>
                            <div style="background-color: #0b1e38; border: 2px solid #FFD700; border-radius: 8px; padding: 16px; text-align: center; margin: 20px 0;">
                                <span style="font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #FFD700;">$code</span>
                            </div>
                            <p style="font-size: 12px; color: #90a4ae;">এই কোডটি আগামী ৫ মিনিট কার্যকর থাকবে। কাউকে এই কোড শেয়ার করবেন না।</p>
                        </div>
                        """.trimIndent()
                    )
                }

                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("https://api.brevo.com/v3/smtp/email")
                    .addHeader("api-key", brevoKey)
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }

        return false
    }

    /**
     * Verifies the 6-digit OTP code securely via Cloud Function (verifyEmailOtp)
     * or via local session fallback.
     */
    suspend fun verifyCode(email: String, enteredCode: String): OtpVerifyResult = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val cleanCode = enteredCode.trim()

        if (cleanCode.length != 6 || !cleanCode.all { it.isDigit() }) {
            return@withContext OtpVerifyResult.Invalid(MAX_ATTEMPTS)
        }

        // 1. Attempt secure Cloud Function verification (returns minted Custom Token)
        try {
            val functionUrl = getCloudFunctionUrl("verifyEmailOtp")
            val payload = JSONObject().put("data", JSONObject().put("email", cleanEmail).put("code", cleanCode)).toString()
            val request = Request.Builder()
                .url(functionUrl)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val json = JSONObject(respBody)
                val resultObj = json.optJSONObject("result") ?: json
                val customToken = resultObj.optString("customToken", null)
                return@withContext OtpVerifyResult.Success(customToken)
            } else if (response.code in 400..499) {
                val errorMsg = try {
                    JSONObject(respBody).optJSONObject("error")?.optString("message") ?: ""
                } catch (e: Exception) { "" }
                when {
                    errorMsg.contains("already", ignoreCase = true) -> return@withContext OtpVerifyResult.AlreadyUsed
                    errorMsg.contains("expired", ignoreCase = true) -> return@withContext OtpVerifyResult.Expired
                    errorMsg.contains("lock", ignoreCase = true) || errorMsg.contains("too many", ignoreCase = true) -> return@withContext OtpVerifyResult.Locked
                    errorMsg.contains("invalid", ignoreCase = true) || errorMsg.contains("ভুল", ignoreCase = true) -> return@withContext OtpVerifyResult.Invalid(3)
                    errorMsg.isNotBlank() -> return@withContext OtpVerifyResult.Error(errorMsg)
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("EmailOtpService", "Cloud Function verification error, checking local session", e)
        }

        // 2. Local session verification fallback
        val docId = hashEmail(cleanEmail)
        val session = localSessionCache[docId]
            ?: return@withContext OtpVerifyResult.Error("কোনো সক্রিয় ভেরিফিকেশন সেশন পাওয়া যায়নি। নতুন কোড পাঠান।")

        if (session.isUsed) {
            return@withContext OtpVerifyResult.AlreadyUsed
        }

        val now = System.currentTimeMillis()
        if (now > session.expiresAt) {
            return@withContext OtpVerifyResult.Expired
        }

        if (session.attempts >= MAX_ATTEMPTS) {
            return@withContext OtpVerifyResult.Locked
        }

        val enteredHash = hashCodeWithSalt(cleanCode, session.salt)
        if (enteredHash == session.codeHash) {
            session.isUsed = true
            OtpVerifyResult.Success(null)
        } else {
            session.attempts++
            if (session.attempts >= MAX_ATTEMPTS) {
                OtpVerifyResult.Locked
            } else {
                OtpVerifyResult.Invalid(MAX_ATTEMPTS - session.attempts)
            }
        }
    }
}
