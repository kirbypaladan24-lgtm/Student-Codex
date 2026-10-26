import java.io.File
import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ============================================================================
// Build-time environment configuration (.env -> BuildConfig)
// ----------------------------------------------------------------------------
// Android apps do not read .env files at runtime. Gradle loads the git-ignored
// .env at the PROJECT ROOT during configuration and exposes only the public
// client configuration (Supabase URL + publishable key) through BuildConfig.
//
// Rules enforced here (see docs/ADR-002-env-and-supabase-client.md):
//   * a missing .env or missing variable fails the build with an actionable
//     message that never echoes values;
//   * values are escaped before being embedded as string literals;
//   * privileged keys (sb_secret_... / service_role) are rejected outright:
//     only the publishable key may ship inside the client.
// ============================================================================

val envFile: File = rootProject.file(".env")
val envProperties = Properties().apply {
    if (envFile.exists()) {
        envFile.inputStream().use { load(it) }
    }
}

fun envValue(name: String): String {
    if (!envFile.exists()) {
        throw GradleException(
            "Student Codex needs a .env file at the project root.\n" +
                "Create '${envFile.path}' containing:\n" +
                "    SUPABASE_URL=https://<project-ref>.supabase.co\n" +
                "    SUPABASE_PUBLISHABLE_KEY=<publishable key>\n" +
                "Copy both values from the Supabase dashboard (Project Settings -> API).\n" +
                "The file is git-ignored; never put secret keys in it."
        )
    }
    val value = envProperties.getProperty(name)?.trim()
    if (value.isNullOrEmpty()) {
        throw GradleException(
            "Missing required .env variable: $name\n" +
                "Add '$name=<value>' to ${envFile.path} " +
                "(Supabase dashboard: Project Settings -> API)."
        )
    }
    return value
}

/** Escapes a raw value into a valid BuildConfig string literal. */
fun buildConfigStringLiteral(name: String, value: String): String {
    if (value.length > 4096 || value.any { it == '$' || it.code < 0x20 || it.code == 0x7f }) {
        throw GradleException(
            ".env variable $name contains characters that cannot be embedded safely " +
                "into a BuildConfig string literal (control characters or '$'). " +
                "Fix the value in ${envFile.path}."
        )
    }
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

/**
 * Optional .env variable: empty string when absent (never fails the build).
 * Used for configuration with a graceful in-app fallback - e.g. the Google
 * OAuth web client ID, whose absence simply points the Google Sign-In
 * button at a setup notice instead.
 */
fun envValueOptional(name: String): String = envProperties.getProperty(name)?.trim() ?: ""

val supabaseUrl: String = envValue("SUPABASE_URL")
val supabasePublishableKey: String = envValue("SUPABASE_PUBLISHABLE_KEY")

// Google Sign-In: the OAuth 2.0 "Web application" client ID from Google
// Cloud Console (APIs & Services > Credentials). A PUBLIC client
// identifier, not a secret - the only Google value allowed in the app.
// Empty means "Google Sign-In not configured yet": the auth gate keeps
// email sign-in working and shows a setup notice on the Google button.
val googleServerClientId: String = envValueOptional("GOOGLE_SERVER_CLIENT_ID")
if (googleServerClientId.isNotEmpty() && !googleServerClientId.endsWith("apps.googleusercontent.com")) {
    throw GradleException(
        "GOOGLE_SERVER_CLIENT_ID in .env does not look like a Google OAuth client ID. " +
            "Expected the Web application client ID ending in 'apps.googleusercontent.com' " +
            "(Google Cloud Console > APIs & Services > Credentials)."
    )
}

// Fail fast on obviously wrong or privileged values - without printing them.
if (!supabaseUrl.startsWith("https://")) {
    throw GradleException(
        "SUPABASE_URL in .env must be an https:// Supabase project URL " +
            "(Supabase dashboard: Project Settings -> API)."
    )
}
run {
    val key = supabasePublishableKey
    if (key.startsWith("sb_secret_")) {
        throw GradleException(
            "SUPABASE_PUBLISHABLE_KEY in .env is a SECRET key. Secret/service-role keys " +
                "must NEVER be embedded in the Android app. Use the publishable key " +
                "(Supabase dashboard: Project Settings -> API)."
        )
    }
    if (key.startsWith("sb_")) return@run // current publishable key format
    if (key.startsWith("eyJ")) {
        // Legacy JWT-style key: decode the payload and refuse service_role keys.
        val payloadJson = try {
            val part = key.split(".")[1].trimEnd('=')
            String(Base64.getUrlDecoder().decode(part))
        } catch (e: Exception) {
            throw GradleException(
                "SUPABASE_PUBLISHABLE_KEY in .env is not a valid key token. " +
                    "Copy the publishable key from the Supabase dashboard " +
                    "(Project Settings -> API)."
            )
        }
        if (payloadJson.contains("service_role")) {
            throw GradleException(
                "SUPABASE_PUBLISHABLE_KEY in .env carries the service_role privilege. " +
                    "The Android app must only embed the publishable (anon) key."
            )
        }
    } else {
        throw GradleException(
            "SUPABASE_PUBLISHABLE_KEY in .env has an unrecognized format. " +
                "Copy the publishable key from the Supabase dashboard " +
                "(Project Settings -> API)."
        )
    }
}

android {
    namespace = "com.example.studentcodex"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.studentcodex"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Public client configuration only - see the .env rules above.
        buildConfigField(
            "String",
            "SUPABASE_URL",
            buildConfigStringLiteral("SUPABASE_URL", supabaseUrl)
        )
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            buildConfigStringLiteral("SUPABASE_PUBLISHABLE_KEY", supabasePublishableKey)
        )
        // Optional Google Sign-In (empty = not configured yet). Public web
        // client ID only - client secrets never enter the app.
        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            buildConfigStringLiteral("GOOGLE_SERVER_CLIENT_ID", googleServerClientId)
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    // Supabase (community-maintained Kotlin client), pinned via the BOM:
    //   auth-kt      - Supabase Auth (the planned authentication system)
    //   postgrest-kt - Data API access (authorization enforced by RLS)
    //   ktor-client-okhttp - HTTP engine for Android (version aligned with
    //                        the supabase-kt release; see ADR-002)
    //   multiplatform-settings-no-arg - Auth session persistence on Android
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth.kt)
    implementation(libs.supabase.postgrest.kt)
    // storage-kt - Supabase Storage (community image uploads); same BOM pin
    // as auth/postgrest so the versions stay aligned (ADR-002).
    implementation(libs.supabase.storage.kt)
    // functions-kt - Supabase Edge Functions (Google Classroom sync); same
    // BOM pin. The session JWT travels in the Authorization header
    // automatically; the Google access token rides in the request body.
    implementation(libs.supabase.functions.kt)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.multiplatform.settings.no.arg)
    implementation(libs.kotlinx.serialization.json)

    // Google Sign-In via Android Credential Manager: fetches a Google ID
    // token on-device which Supabase Auth exchanges for a session
    // (auth.signInWith(IDToken)). credentials-play-services bridges devices
    // that have Google Play services; the only embedded Google value is the
    // public web client ID (GOOGLE_SERVER_CLIENT_ID in .env, empty until
    // configured).
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    // Google Authorization API: the consent sheet that grants the
    // read-only Classroom scopes and returns a short-lived access token
    // (transit-only - see data/auth/ClassroomAuthorization.kt).
    implementation(libs.play.services.auth)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

