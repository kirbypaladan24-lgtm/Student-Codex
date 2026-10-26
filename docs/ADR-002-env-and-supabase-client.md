# ADR-002: Build-time .env configuration and the supabase-kt client

Status: Accepted. Date: Phase 3 (Supabase foundation). Deciders: engineering.

## Context

The Android app needs the Supabase project URL and publishable key, but Android
does not load `.env` files at runtime and no credential may live in source
control. An earlier draft used `local.properties`; the project now standardises
on a single root `.env` shared by all tooling as the developer's configuration
file, per the developer's instruction.

## Decision

1. **`.env` at the project root is the single configuration file.** It is
   git-ignored. Gradle (app module) parses it at configuration time and exposes
   exactly two `BuildConfig` fields: `SUPABASE_URL` and
   `SUPABASE_PUBLISHABLE_KEY`. Nothing else from `.env` is read by the Android
   build; the file is never packaged as an asset/resource and no second
   configuration file is created.
2. **Fail-fast validation without echoing values.** A missing file, a missing
   variable, a non-https URL, an unparseable key, or a privileged key
   (`sb_secret_...`, legacy service_role JWT — detected by decoding the token
   payload) aborts the build with a message that names only the variable and
   the fix, never the value.
3. **Escaping.** Values are escaped (`\` and `"`) before being embedded into
   the generated string literal; values containing control characters or `$`
   are rejected because they cannot be represented safely in generated code.
4. **One reusable client.** `data/supabase/SupabaseClientFactory.kt` builds the
   single `SupabaseClient` (modules: `Auth`, `Postgrest`) as a pure function
   with an optional `AuthConfig` customization block (used by JVM tests to
   inject in-memory session storage); `AppContainer` owns the client lazily.
   Screens and Compose components never construct clients — Supabase-backed
   repositories (Phase 4+) are the only consumers, per the ADR-001 boundary
   rule.
5. **Versions — verified against Maven Central metadata and real builds:**
   - The project's Kotlin 2.2.10 compiler reads Kotlin metadata up to 2.3.0
     (confirmed by the actual compiler diagnostic during verification).
   - `io.github.jan-tennert.supabase:bom:3.6.0` (+ `auth-kt`, `postgrest-kt`
     from the BOM) — the newest release compatible with Kotlin 2.2.10. Its
     modules are built with Kotlin 2.3.21 (metadata 2.3.0, within the
     one-minor tolerance). **3.7.0 and 3.8.0 were rejected**: their Gradle
     module metadata declares `kotlin-stdlib 2.4.0` (built with Kotlin 2.4.0);
     an attempted build with 3.8.0 failed with exactly that diagnostic
     ("actual metadata version is 2.4.0 ... compiler version 2.2.0 can read
     versions up to 2.3.0"), which established the boundary empirically.
   - `io.ktor:ktor-client-okhttp:3.4.3` — the exact Ktor version supabase-kt
     3.6.0 is built against (its module metadata declares ktor-client-core /
     ktor-client-content-negotiation / ktor-serialization-kotlinx-json 3.4.3);
     ktor 3.4.3 itself is built with Kotlin 2.3.0, also within tolerance.
   - `com.russhwolf:multiplatform-settings-no-arg:1.3.0` — Auth session
     persistence on Android (SharedPreferences-backed); the same version
     auth-kt 3.6.0 depends on, built with Kotlin 2.1.0.
   - minSdk 26 matches the supabase-kt minimum (no desugaring needed).
   - Final proof is the real Gradle build on this project: compile, unit tests
     (16/16), lint, and assembleDebug all ran green after the change, and the
     packaged APK was byte-scanned to confirm no privileged key material.
6. **Secrets stay server-side.** Only the URL and publishable key ever enter
   `BuildConfig`, source, assets, or the repo. `sb_secret_...`/service-role
   keys, database passwords, and Google OAuth client secrets/refresh tokens
   belong to Supabase dashboard secrets and Edge Function secrets only
   (Phases 5–6). If such a value ever appears in `.env`, it must never be
   referenced by the Android build.

## Alternatives considered

- **`local.properties`:** superseded — it is IDE/SDK-specific and invisible to
  non-Android tooling; `.env` is the project-wide convention.
- **Runtime `.env` loading:** rejected — Android has no `.env` convention;
  build-time BuildConfig is deterministic, reviewable, and cannot fail at
  runtime.
- **Bare Retrofit/OkHttp adapter against the Supabase HTTP API:** kept as the
  documented fallback (ADR-001) if supabase-kt compatibility breaks; not
  needed at 3.6.0.

## Consequences

- Every Gradle invocation requires a valid `.env`; its error message doubles
  as the onboarding instruction for that step.
- Moving past supabase BOM 3.6.0 requires a project Kotlin upgrade to a
  compiler that can read Kotlin 2.4.0 metadata, or a future supabase-kt
  release built with Kotlin <= 2.3. Re-check the pairing on every bump.
- Extending the client with more BuildConfig fields is a deliberate allow-list
  change; privileged values must never be added.
