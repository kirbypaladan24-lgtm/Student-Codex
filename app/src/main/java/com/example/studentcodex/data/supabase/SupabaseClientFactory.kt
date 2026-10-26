package com.example.studentcodex.data.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.AuthConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Creates the app's single reusable Supabase client.
 *
 * Configuration comes from BuildConfig, which Gradle fills from the git-ignored
 * `.env` file at the project root at build time (see
 * docs/ADR-002-env-and-supabase-client.md). Only the publishable key is ever
 * embedded: authorization is enforced server-side by Row Level Security, never
 * by keeping the key secret.
 *
 * Only the modules required by the current implementation are installed:
 *  - [Auth]      — Supabase Auth, the planned authentication system;
 *  - [Postgrest] — Data API access for the Supabase-backed repositories;
 *  - [Storage]   — community image uploads to the private bucket;
 *  - [Functions] — Edge Function calls (Google Classroom sync).
 *
 * [configureAuth] optionally customizes Auth. Production code passes nothing:
 * the platform defaults store the session and the PKCE code verifier via
 * multiplatform-settings (SharedPreferences on Android). JVM unit tests inject
 * [io.github.jan.supabase.auth.MemorySessionManager] and
 * [io.github.jan.supabase.auth.MemoryCodeVerifierCache] because the plain JVM
 * has no default settings provider.
 *
 * This is a pure function with no Android dependencies so client construction
 * is unit-testable without a device. Callers must go through
 * [com.example.studentcodex.di.AppContainer]; screens and Compose components
 * never construct their own client.
 */
fun createSupabaseClient(
    supabaseUrl: String,
    supabasePublishableKey: String,
    configureAuth: AuthConfig.() -> Unit = {},
): SupabaseClient =
    createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabasePublishableKey,
    ) {
        install(Auth) {
            configureAuth()
        }
        install(Postgrest)
        install(Storage)
        install(Functions)
    }
