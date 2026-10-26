package com.example.studentcodex.di

import android.app.Application
import com.example.studentcodex.BuildConfig
import com.example.studentcodex.data.auth.AuthRepository
import com.example.studentcodex.data.auth.ClassroomAuthorizer
import com.example.studentcodex.data.auth.CredentialManagerGoogleAuth
import com.example.studentcodex.data.auth.GoogleAuthManager
import com.example.studentcodex.data.auth.PlayServicesClassroomAuthorizer
import com.example.studentcodex.data.auth.SupabaseAuthRepository
import com.example.studentcodex.data.repo.ActivityRepository
import com.example.studentcodex.data.repo.ClassroomAutoSyncStore
import com.example.studentcodex.data.repo.ClassroomAutoSyncer
import com.example.studentcodex.data.repo.ClassroomConnectionRepository
import com.example.studentcodex.data.repo.ClassroomSyncRepository
import com.example.studentcodex.data.repo.RusshwolfClassroomAutoSyncStore
import com.example.studentcodex.data.repo.CommunityRepository
import com.example.studentcodex.data.repo.ProfileRepository
import com.example.studentcodex.data.school.SchoolDirectoryRepository
import com.example.studentcodex.data.school.SupabaseSchoolDirectoryRepository
import com.example.studentcodex.data.supabase.AppContextHolder
import com.example.studentcodex.data.supabase.ContentResolverImageSource
import com.example.studentcodex.data.supabase.createSupabaseClient
import com.example.studentcodex.data.supabase.SupabaseActivityRepository
import com.example.studentcodex.data.supabase.SupabaseClassroomConnectionRepository
import com.example.studentcodex.data.supabase.SupabaseClassroomSyncRepository
import com.example.studentcodex.data.supabase.SupabaseCommunityRepository
import com.example.studentcodex.data.supabase.SupabaseProfileRepository
import com.example.studentcodex.domain.guidance.DemoGuidanceEngine
import com.example.studentcodex.domain.guidance.GuidanceEngine
import io.github.jan.supabase.SupabaseClient

/**
 * Manual dependency container (no DI framework for the MVP - see the build
 * plan's "no dependency sprawl" rule). Repositories are Supabase-backed
 * behind their interfaces; tests inject fakes. Nothing else changes when
 * implementations evolve.
 */
class AppContainer(private val application: Application) {

    init {
        // Image uploads read picked photos through the ContentResolver.
        AppContextHolder.attach(application)
    }

    /**
     * The app's single Supabase client, built once from the build-time .env
     * configuration (URL + publishable key only - never a secret key).
     * Lazy so nothing is initialized before first use.
     *
     * UI never touches this directly: Supabase-backed repositories are the
     * only intended consumers, keeping the client swappable behind the
     * repository boundary (docs/ADR-001-supabase-first.md).
     */
    val supabaseClient: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabasePublishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
        )
    }

    /** Real email/password authentication against the hosted project. */
    val authRepository: AuthRepository by lazy { SupabaseAuthRepository(supabaseClient) }

    /**
     * School registry (programs, campuses, year levels, year sections) for
     * the sign-up dropdowns. Anon-readable by RLS design: the form runs
     * before any account exists. Cached in memory — reference data.
     */
    val schoolDirectoryRepository: SchoolDirectoryRepository by lazy {
        SupabaseSchoolDirectoryRepository(supabaseClient)
    }

    /**
     * Google Sign-In via Android Credential Manager. Optional: until a web
     * client ID is provided (GOOGLE_SERVER_CLIENT_ID in .env), the manager
     * reports not-configured and the auth gate shows a setup notice instead
     * of silently failing. Only the PUBLIC client ID is ever embedded.
     */
    val googleAuthManager: GoogleAuthManager by lazy {
        CredentialManagerGoogleAuth(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
    }

    /**
     * Activity Beacon (Phases 4+5): reads/writes the hosted database through
     * RLS. Manual activities are personal + student-reported; reports file
     * as pending evidence for teacher triage.
     */
    val activityRepository: ActivityRepository by lazy {
        SupabaseActivityRepository(supabaseClient)
    }

    /**
     * Community feed (Phase 9): invoker-safe feed views for reads; real
     * posts/comments/likes; image uploads to the private bucket.
     */
    val communityRepository: CommunityRepository by lazy {
        SupabaseCommunityRepository(supabaseClient, ContentResolverImageSource())
    }

    /** Profile read from the signed-in student's row + joined labels. */
    val profileRepository: ProfileRepository by lazy {
        SupabaseProfileRepository(supabaseClient)
    }

    /**
     * Google Classroom connection STATE. Reads the google_connections
     * registry row the classroom-sync Edge Function maintains.
     */
    val classroomConnectionRepository: ClassroomConnectionRepository by lazy {
        SupabaseClassroomConnectionRepository(supabaseClient)
    }

    /**
     * Google Classroom SYNC (Phase 6): runs the classroom-sync Edge
     * Function import with a Google access token the Authorization API
     * consent sheet just minted. Tokens are transit-only.
     */
    val classroomSyncRepository: ClassroomSyncRepository by lazy {
        SupabaseClassroomSyncRepository(supabaseClient)
    }

    /** The on-device Google consent sheet for Classroom read scopes. */
    val classroomAuthorizer: ClassroomAuthorizer by lazy { PlayServicesClassroomAuthorizer() }

    /**
     * Auto-sync preference (SharedPreferences via multiplatform-settings):
     * whether background Classroom imports are on.
     */
    val classroomAutoSyncStore: ClassroomAutoSyncStore by lazy {
        RusshwolfClassroomAutoSyncStore(com.russhwolf.settings.Settings())
    }

    /**
     * Background Classroom sync: silently re-imports new Google Classroom
     * work on app open when the student turned the auto-sync toggle on.
     */
    val classroomAutoSyncer: ClassroomAutoSyncer by lazy {
        ClassroomAutoSyncer(
            store = classroomAutoSyncStore,
            connectionRepository = classroomConnectionRepository,
            syncRepository = classroomSyncRepository,
            authorizer = classroomAuthorizer,
        )
    }

    val guidanceEngine: GuidanceEngine = DemoGuidanceEngine()
}
