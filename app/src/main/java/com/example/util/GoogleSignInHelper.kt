package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.data.model.User
import com.example.data.repository.AuthRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.OAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.SecureRandom

object GoogleSignInHelper {
    private const val TAG = "GoogleSignInHelper"
    private val secureRandom = SecureRandom()

    internal tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    /**
     * Generates a cryptographically strong URL-safe nonce required by Google Identity Services
     * to prevent token replay and satisfy Play Services 24.40+ verification checks.
     */
    fun generateSecureNonce(byteLength: Int = 32): String {
        val bytes = ByteArray(byteLength.coerceAtLeast(16))
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    /**
     * Maps a One-Tap / Credential Manager cancellation into clear Bengali user guidance
     * instead of failing silently when Play Services aborts post-account-selection.
     */
    fun buildCancellationGuidance(errorMessage: CharSequence?, elapsedMs: Long): String {
        val detail = errorMessage?.toString()?.trim().orEmpty()
        return if (elapsedMs >= 900L || detail.isNotBlank()) {
            "One-Tap পপ-আপ থেকে লগইন সম্পন্ন হয়নি (সম্ভবত Firebase-এ com.somethingnewredoan.app এর SHA-1 বা OAuth কনসেন্ট স্ক্রিন যাচাই বাকি)। নিচের 'ব্রাউজার দিয়ে Gmail লগইন' বাটনে ট্যাপ করুন।"
        } else {
            "Google One-Tap পপ-আপ বাতিল হয়েছে—সহজে লগইন করতে নিচের 'ব্রাউজার দিয়ে Gmail লগইন' ব্যবহার করুন।"
        }
    }

    fun signInWithGoogle(
        context: Context,
        scope: CoroutineScope,
        authRepository: AuthRepository,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        val activity = context.findActivity()
        val uiContext: Context = if (activity != null) MutableContextWrapper(activity) else context

        val clientId = try {
            context.getString(R.string.default_web_client_id).trim()
        } catch (e: Exception) {
            Log.w(TAG, "default_web_client_id missing, falling back to browser OAuth", e)
            ""
        }

        if (clientId.isBlank()) {
            if (activity != null && authRepository.getFirebaseAuth() != null) {
                scope.launch {
                    attemptBrowserOAuthSignIn(
                        activity = activity,
                        authRepository = authRepository,
                        onSuccess = onSuccess,
                        onError = onError
                    )
                }
            } else {
                onError("Google Sign-In কনফিগারেশন পাওয়া যায়নি (default_web_client_id অনুপস্থিত)।")
            }
            return
        }

        val credentialManager = CredentialManager.create(uiContext)
        val nonce = generateSecureNonce()
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId)
            .setNonce(nonce)
            .build()
        val primaryRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        scope.launch {
            val startedAt = System.currentTimeMillis()
            try {
                // Step 0: Recover any completed pending browser OAuth session first
                val pendingUser = authRepository.consumePendingFirebaseAuthResult().getOrNull()
                if (pendingUser != null) {
                    onSuccess(pendingUser)
                    return@launch
                }

                // Tier 1: Explicit Sign-In with Google button flow
                val result = credentialManager.getCredential(uiContext, primaryRequest)
                handleGoogleCredentialResult(
                    credential = result.credential,
                    authRepository = authRepository,
                    onSuccess = onSuccess,
                    onError = onError
                )
            } catch (e: GetCredentialCancellationException) {
                val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
                Log.w(
                    TAG,
                    "Tier 1 Credential Manager cancelled after ${elapsed}ms: type=${e.type}, msg=${e.errorMessage}",
                    e
                )
                onError(buildCancellationGuidance(e.errorMessage, elapsed))
            } catch (e: NoCredentialException) {
                Log.w(TAG, "Tier 1 NoCredentialException, retrying GetGoogleIdOption: ${e.message}", e)
                retryWithGoogleIdOptionOrBrowser(
                    uiContext = uiContext,
                    activity = activity,
                    credentialManager = credentialManager,
                    clientId = clientId,
                    authRepository = authRepository,
                    onSuccess = onSuccess,
                    onError = onError
                )
            } catch (e: GetCredentialException) {
                Log.w(TAG, "Tier 1 GetCredentialException (${e.type}), retrying Tier 2/Browser: ${e.message}", e)
                retryWithGoogleIdOptionOrBrowser(
                    uiContext = uiContext,
                    activity = activity,
                    credentialManager = credentialManager,
                    clientId = clientId,
                    authRepository = authRepository,
                    onSuccess = onSuccess,
                    onError = onError
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected Credential Manager failure", e)
                if (activity != null && authRepository.getFirebaseAuth() != null) {
                    attemptBrowserOAuthSignIn(
                        activity = activity,
                        authRepository = authRepository,
                        onSuccess = onSuccess,
                        onError = onError
                    )
                } else {
                    onError(authRepository.mapAuthErrorMessage(e))
                }
            }
        }
    }

    private suspend fun retryWithGoogleIdOptionOrBrowser(
        uiContext: Context,
        activity: Activity?,
        credentialManager: CredentialManager,
        clientId: String,
        authRepository: AuthRepository,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        val tier2StartedAt = System.currentTimeMillis()
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .setServerClientId(clientId)
                .setNonce(generateSecureNonce())
                .build()
            val fallbackRequest = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(uiContext, fallbackRequest)
            handleGoogleCredentialResult(
                credential = result.credential,
                authRepository = authRepository,
                onSuccess = onSuccess,
                onError = onError
            )
        } catch (e: GetCredentialCancellationException) {
            val elapsed = (System.currentTimeMillis() - tier2StartedAt).coerceAtLeast(0L)
            Log.w(TAG, "Tier 2 cancelled after ${elapsed}ms: ${e.errorMessage}", e)
            onError(buildCancellationGuidance(e.errorMessage, elapsed))
        } catch (e: Exception) {
            Log.w(TAG, "Tier 2 failed (${e::class.java.simpleName}), launching Firebase Browser OAuth", e)
            if (activity != null && authRepository.getFirebaseAuth() != null) {
                attemptBrowserOAuthSignIn(
                    activity = activity,
                    authRepository = authRepository,
                    onSuccess = onSuccess,
                    onError = onError
                )
            } else {
                onError("এই ফোনে One-Tap লগইন চালু করা যায়নি। ইন্টারনেট ও Google Play Services চেক করুন।")
            }
        }
    }

    private suspend fun handleGoogleCredentialResult(
        credential: Credential,
        authRepository: AuthRepository,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            val res = authRepository.signInWithGoogleCredential(
                idToken = googleCred.idToken,
                displayName = googleCred.displayName,
                photoUrl = googleCred.profilePictureUri?.toString()
            )
            res.fold(
                onSuccess = { user -> onSuccess(user) },
                onFailure = { err ->
                    Log.e(TAG, "Firebase credential sign-in failed", err)
                    onError(err.localizedMessage ?: authRepository.mapAuthErrorMessage(err))
                }
            )
        } else {
            onError("অসমর্থিত Google ক্রেডেনশিয়াল পাওয়া গেছে। 'ব্রাউজার দিয়ে Gmail লগইন' চেষ্টা করুন।")
        }
    }

    /**
     * Explicit Firebase Federated Google OAuth flow via Chrome Custom Tab / Browser.
     * Bypasses Play Services One-Tap & Android client_type=1 SHA-1 blockers using the
     * Firebase Auth Web OAuth handler (`something-new-redoan.firebaseapp.com/__/auth/handler`).
     */
    fun signInWithGoogleViaBrowser(
        context: Context,
        scope: CoroutineScope,
        authRepository: AuthRepository,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        val activity = context.findActivity()
        if (activity == null) {
            onError("ব্রাউজার লগইন শুরু করতে বৈধ স্ক্রিন পাওয়া যায়নি।")
            return
        }
        scope.launch {
            attemptBrowserOAuthSignIn(
                activity = activity,
                authRepository = authRepository,
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }

    private suspend fun attemptBrowserOAuthSignIn(
        activity: Activity,
        authRepository: AuthRepository,
        onSuccess: (User) -> Unit,
        onError: (String) -> Unit
    ) {
        val firebaseAuth: FirebaseAuth? = authRepository.getFirebaseAuth()
        if (firebaseAuth == null) {
            onError("Firebase Authentication পাওয়া যায়নি।")
            return
        }
        try {
            // Check if a redirected sign-in result is already waiting
            val pendingRes = authRepository.consumePendingFirebaseAuthResult()
            val pendingUser = pendingRes.getOrNull()
            if (pendingUser != null) {
                onSuccess(pendingUser)
                return
            }

            val provider = OAuthProvider.newBuilder("google.com")
                .addCustomParameter("prompt", "select_account")
                .setScopes(listOf("email", "profile"))
                .build()

            val authResult = firebaseAuth
                .startActivityForSignInWithProvider(activity, provider)
                .await()

            val firebaseUser = authResult.user
            if (firebaseUser == null) {
                onError("Google অ্যাকাউন্ট তথ্য পাওয়া যায়নি।")
                return
            }

            authRepository.completeFirebaseUserLogin(firebaseUser).fold(
                onSuccess = { linked -> onSuccess(linked) },
                onFailure = { err ->
                    Log.e(TAG, "Profile linking after browser OAuth failed", err)
                    onError(authRepository.mapAuthErrorMessage(err))
                }
            )
        } catch (e: FirebaseAuthException) {
            Log.e(TAG, "Firebase browser OAuth failed: code=${e.errorCode}, msg=${e.message}", e)
            onError(authRepository.mapAuthErrorMessage(e))
        } catch (e: Exception) {
            Log.e(TAG, "Browser OAuth unexpected error: ${e.message}", e)
            onError(authRepository.mapAuthErrorMessage(e))
        }
    }

    fun signOutFromGoogle(
        context: Context,
        scope: CoroutineScope,
        authRepository: AuthRepository,
        onComplete: () -> Unit = {}
    ) {
        val credentialManager = CredentialManager.create(context)
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    authRepository.signOut()
                }
            }
            onComplete()
        }
    }
}
