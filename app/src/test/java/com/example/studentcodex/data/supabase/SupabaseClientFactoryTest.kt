package com.example.studentcodex.data.supabase

import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Foundation-level check for the Supabase integration: the factory builds one
 * client carrying exactly the supplied URL and publishable key, with the Auth
 * and Postgrest modules installed. Client construction involves no network
 * access. In-memory session storage is injected because the plain JVM test
 * environment has no default settings provider (production Android uses
 * SharedPreferences-backed storage).
 */
class SupabaseClientFactoryTest {

    private val url = "https://unit-test-project.supabase.co"
    private val key = "unit-test-publishable-key-0000000000000001"

    private fun client() = createSupabaseClient(url, key) {
        sessionManager = MemorySessionManager()
        codeVerifierCache = MemoryCodeVerifierCache()
        // The JVM has no Android ProcessLifecycleOwner: registering the
        // Auth plugin's lifecycle observer would leak an Android-framework
        // coroutine into the shared test JVM and crash unrelated suites.
        // Production keeps the default (enabled) for session auto-refresh.
        enableLifecycleCallbacks = false
    }

    @Test
    fun `client carries the supplied url and key`() {
        val client = client()
        // supabase-kt normalizes the URL by stripping the scheme
        // (SupabaseClientBuilder stores supabaseUrl.split("//").last()).
        assertEquals("unit-test-project.supabase.co", client.supabaseUrl)
        assertEquals(key, client.supabaseKey)
    }

    @Test
    fun `auth and postgrest modules are installed`() {
        val client = client()
        assertNotNull(client.auth)
        assertNotNull(client.postgrest)
    }
}

