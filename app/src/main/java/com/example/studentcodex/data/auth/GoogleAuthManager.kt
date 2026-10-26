package com.example.studentcodex.data.auth

import android.app.Activity
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** Outcome of asking the device for a Google ID token. */
sealed interface GoogleAuthOutcome {

    /** An ID token was obtained and can be exchanged with Supabase Auth. */
    data class Token(val idToken: String) : GoogleAuthOutcome

    /** No web client ID was provided at build time (GOOGLE_SERVER_CLIENT_ID). */
    data object NotConfigured : GoogleAuthOutcome

    /** The user closed the account picker; nothing to do. */
    data object Cancelled : GoogleAuthOutcome

    /**
     * No Google account is signed in on the device. Typical on a freshly
     * created emulator that never had an account added. The fix is adding
     * an account in the device's Settings - nothing is wrong with the app.
     */
    data object NoAccount : GoogleAuthOutcome

    /**
     * Google Play services refused the request itself: GMS code 13
     * (DEVELOPER_ERROR). The app's package name + SHA-1 signing
     * fingerprint are not registered in the Google Cloud project that owns
     * the web client ID. Fix: docs/GOOGLE-SIGNIN.md, "Register the Android
     * app" - a developer-side step, not a device problem.
     */
    data class Rejected(val code: String?) : GoogleAuthOutcome

    /** Something else failed on the device; [message] carries diagnostics. */
    data class Error(val message: String?) : GoogleAuthOutcome
}

/**
 * Boundary around Android Credential Manager so ViewModels stay testable:
 * unit tests substitute a fake, production uses [CredentialManagerGoogleAuth].
 */
interface GoogleAuthManager {

    /** True when a web client ID was provided at build time. */
    val isConfigured: Boolean

    /**
     * Opens the system account picker and returns the chosen account's ID
     * token. [activity] must be a live activity so the picker can show its
     * UI; the nullable type is the seam that keeps the flow testable on the
     * JVM (the real implementation treats null as an error).
     */
    suspend fun fetchIdToken(activity: Activity?): GoogleAuthOutcome
}

/**
 * Google Sign-In via Android Credential Manager (successor of the old
 * GoogleSignInClient). The ID token's audience is the *Web application*
 * OAuth client ID from Google Cloud Console - the same client ID the
 * Supabase dashboard's Google provider is configured with. Supabase
 * verifies the token and issues its own session; the first sign-in creates
 * the account and the profiles trigger fills the profile row.
 *
 * For Play services to hand out such a token, the calling app must be
 * known to the SAME Google Cloud project: package name + SHA-1 signing
 * fingerprint registered (an OAuth *Android* client or a linked Firebase
 * app). Without that registration the request fails with GMS code 13
 * before any account picker appears.
 */
class CredentialManagerGoogleAuth(
    private val serverClientId: String,
) : GoogleAuthManager {

    override val isConfigured: Boolean get() = serverClientId.isNotBlank()

    override suspend fun fetchIdToken(activity: Activity?): GoogleAuthOutcome {
        if (!isConfigured) return GoogleAuthOutcome.NotConfigured
        val host = activity ?: return GoogleAuthOutcome.Error("no activity to attach the account picker to")
        return try {
            val credentialManager = CredentialManager.create(host)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setServerClientId(serverClientId)
                // Offer every Google account on the device, not just
                // previously authorized ones - first-time users included.
                .setFilterByAuthorizedAccounts(false)
                .build()
            val request = GetCredentialRequest(listOf(googleIdOption))
            val response = credentialManager.getCredential(host, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                try {
                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    GoogleAuthOutcome.Token(googleCredential.idToken)
                } catch (e: GoogleIdTokenParsingException) {
                    Log.e(TAG, "Google ID token could not be parsed", e)
                    GoogleAuthOutcome.Error("google id token parsing failed")
                }
            } else {
                Log.e(TAG, "Unexpected credential type: ${credential?.type}")
                GoogleAuthOutcome.Error("unexpected credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleAuthOutcome.Cancelled
        } catch (e: NoCredentialException) {
            // No Google account signed in on the device (fresh emulator).
            Log.w(TAG, "No Google account on this device - add one in Settings, then retry")
            GoogleAuthOutcome.NoAccount
        } catch (e: GetCredentialException) {
            // Failures coming back from Play services carry a numeric type
            // such as "13: ..." - CommonStatusCodes.DEVELOPER_ERROR. That
            // one means the app's package/SHA-1 registration is missing or
            // wrong in the Google Cloud project (developer-side fix).
            Log.e(TAG, "Credential Manager failed (type=${e.type})", e)
            val gmsCode = e.type.substringBefore(':').trim()
            if (gmsCode == "13") {
                GoogleAuthOutcome.Rejected(gmsCode)
            } else {
                val note = when {
                    gmsCode.toIntOrNull() != null -> "Google error code $gmsCode"
                    e.type.isNotBlank() -> e.type
                    else -> null
                }
                GoogleAuthOutcome.Error(note ?: e.message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google ID token request failed", e)
            GoogleAuthOutcome.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    private companion object {
        private const val TAG = "GoogleAuth"
    }
}
