package com.example.studentcodex.data.auth

import android.app.Activity
import android.app.PendingIntent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Outcome of asking Google for Classroom read authorization. */
sealed interface ClassroomAuthorizationOutcome {

    /** An access token was granted; it authorizes one sync run. */
    data class Token(val accessToken: String) : ClassroomAuthorizationOutcome

    /** The user closed the consent sheet; nothing to do. */
    data object Cancelled : ClassroomAuthorizationOutcome

    /** The sheet finished without a token (user declined the scopes). */
    data object Declined : ClassroomAuthorizationOutcome

    /**
     * Play services refused the request itself: GMS code 13
     * (DEVELOPER_ERROR). The app's package name + SHA-1 signing
     * fingerprint are not registered as an OAuth Android client in the
     * Google Cloud project that has the Classroom API enabled. Fix is
     * developer-side (Google Cloud Console), not on the device.
     */
    data class Rejected(val code: Int?) : ClassroomAuthorizationOutcome

    /** Something else failed; essage] carries diagnostics. */
    data class Error(val message: String?) : ClassroomAuthorizationOutcome

    /**
     * SILENT-ONLY authorization (auto-sync) found scopes that were never
     * granted (or were revoked): a consent sheet would be needed, which
     * automatic background sync must never show. The caller skips the run.
     */
    data object SilentUnavailable : ClassroomAuthorizationOutcome
}

/** The read-only Classroom scopes the sync needs (student view). */
object ClassroomScopes {
    val READONLY = listOf(
        "https://www.googleapis.com/auth/classroom.courses.readonly",
        "https://www.googleapis.com/auth/classroom.coursework.me.readonly",
        "https://www.googleapis.com/auth/classroom.announcements.readonly",
    )
}

/**
 * Boundary around the Google Authorization API so ViewModels stay
 * testable: unit tests substitute a fake, production uses
 * [PlayServicesClassroomAuthorizer].
 */
interface ClassroomAuthorizer {

    /**
     * Shows the Google consent sheet for the read-only Classroom scopes
     * and returns a short-lived access token. [activity] must be a live
     * activity so the sheet can show its UI; nullable keeps the flow
     * testable on the JVM.
     */
    suspend fun fetchAccessToken(activity: Activity?): ClassroomAuthorizationOutcome

    /**
     * SILENT-ONLY variant for automatic background sync: returns a token
     * ONLY when the account already granted the scopes — no consent sheet,
     * no UI, nothing interrupting the student. When interaction would be
     * needed (never granted / revoked / expired), the outcome is
     * [ClassroomAuthorizationOutcome.SilentUnavailable] and the caller
     * simply skips that run.
     */
    suspend fun fetchSilentAccessToken(activity: Activity?): ClassroomAuthorizationOutcome
}

/**
 * Google Authorization API (play-services-auth) — the consent sheet that
 * grants the read-only Classroom scopes and mints a short-lived access
 * token. The token is used for exactly one sync run and is never stored
 * (no refresh tokens, no client secret anywhere in the app): each sync
 * re-authorizes, which also means revoking in the Google account settings
 * takes effect immediately.
 *
 * Consent handshake detail: [AuthorizationClient.authorize] only returns
 * the token directly when the account has ALREADY granted the scopes.
 * Otherwise the Task succeeds with hasResolution() and a PendingIntent
 * pointing at Google's consent activity — the app must launch that
 * intent itself and parse the returned Intent with
 * [AuthorizationClient.getAuthorizationResultFromIntent]. Skipping the
 * launch makes every first-time connect look like "Declined" with no
 * sheet ever shown.
 */
class PlayServicesClassroomAuthorizer : ClassroomAuthorizer {

    private val flowMutex = Mutex()

    override suspend fun fetchAccessToken(activity: Activity?): ClassroomAuthorizationOutcome =
        authorize(activity, allowInteractive = true)

    override suspend fun fetchSilentAccessToken(activity: Activity?): ClassroomAuthorizationOutcome =
        authorize(activity, allowInteractive = false)

    /**
     * Shared authorization core. [allowInteractive] decides what happens
     * when Google wants the consent sheet: the manual connect flow LAUNCHES
     * it (first-time grant), the silent auto-sync path REFUSES to and
     * reports [ClassroomAuthorizationOutcome.SilentUnavailable] instead —
     * a background import must never put a Google dialog on screen.
     */
    private suspend fun authorize(activity: Activity?, allowInteractive: Boolean): ClassroomAuthorizationOutcome {
        val host = activity
            ?: return ClassroomAuthorizationOutcome.Error("no activity to attach the consent sheet to")
        if (host !is ComponentActivity) {
            return ClassroomAuthorizationOutcome.Error(
                "consent sheet needs a ComponentActivity to receive its result",
            )
        }
        return flowMutex.withLock {
            try {
                val request = AuthorizationRequest.builder()
                    .setRequestedScopes(ClassroomScopes.READONLY.map(::Scope))
                    .build()
                val client = Identity.getAuthorizationClient(host)
                val first = client.authorize(request).awaitResult()
                val token = first.accessToken
                val resolution = first.pendingIntent
                when {
                    !token.isNullOrBlank() -> ClassroomAuthorizationOutcome.Token(token)
                    first.hasResolution() && resolution != null && allowInteractive ->
                        awaitConsentSheet(host, client, resolution)
                    first.hasResolution() && resolution != null ->
                        ClassroomAuthorizationOutcome.SilentUnavailable
                    else -> ClassroomAuthorizationOutcome.Declined
                }
            } catch (e: ApiException) {
                Log.w(TAG, "Classroom authorization refused (code=${e.statusCode})", e)
                when (e.statusCode) {
                    CommonStatusCodes.CANCELED -> ClassroomAuthorizationOutcome.Cancelled
                    CommonStatusCodes.DEVELOPER_ERROR -> ClassroomAuthorizationOutcome.Rejected(e.statusCode)
                    else -> ClassroomAuthorizationOutcome.Error("Google error code ${e.statusCode}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Classroom authorization failed", e)
                ClassroomAuthorizationOutcome.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * Google needs interactive consent: the authorize Task resolved with
     * a PendingIntent to the consent activity. Launch it through the
     * activity's result registry (registrable at tap time — no
     * MainActivity changes needed) and await the sheet's outcome.
     */
    private suspend fun awaitConsentSheet(
        host: ComponentActivity,
        client: AuthorizationClient,
        pendingIntent: PendingIntent,
    ): ClassroomAuthorizationOutcome {
        val sheetResult = CompletableDeferred<ActivityResult?>()
        val launcher = host.activityResultRegistry.register(
            CONSENT_KEY,
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result -> sheetResult.complete(result) }
        return try {
            launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            val outcome = withTimeoutOrNull(CONSENT_TIMEOUT_MS) { sheetResult.await() }
            val data = outcome?.data
            when {
                outcome == null -> ClassroomAuthorizationOutcome.Cancelled
                data == null -> ClassroomAuthorizationOutcome.Cancelled
                else -> try {
                    val authorized = client.getAuthorizationResultFromIntent(data)
                    val token = authorized.accessToken
                    if (token.isNullOrBlank()) {
                        ClassroomAuthorizationOutcome.Declined
                    } else {
                        ClassroomAuthorizationOutcome.Token(token)
                    }
                } catch (e: ApiException) {
                    Log.w(TAG, "Classroom consent refused (code=${e.statusCode})", e)
                    when (e.statusCode) {
                        CommonStatusCodes.CANCELED -> ClassroomAuthorizationOutcome.Cancelled
                        CommonStatusCodes.DEVELOPER_ERROR ->
                            ClassroomAuthorizationOutcome.Rejected(e.statusCode)
                        else -> ClassroomAuthorizationOutcome.Error("Google error code ${e.statusCode}")
                    }
                }
            }
        } finally {
            launcher.unregister()
        }
    }

    private companion object {
        private const val TAG = "ClassroomAuth"
        private const val CONSENT_KEY = "studentcodex-classroom-consent"
        private const val CONSENT_TIMEOUT_MS = 300_000L
    }
}

/**
 * Awaits a GMS [Task] in a coroutine without the play-services coroutines
 * artifact: registers the three listeners and resumes the continuation.
 */
private suspend fun <T : Any> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        addOnFailureListener { e ->
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        addOnCanceledListener {
            if (continuation.isActive) continuation.cancel()
        }
    }
