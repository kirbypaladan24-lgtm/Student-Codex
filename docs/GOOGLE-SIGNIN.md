# Google Sign-In setup (Student Codex)

The app ships with Google Sign-In fully wired: the auth gate's
"Continue with Google" button uses Android **Credential Manager** to obtain a
Google ID token, which Supabase Auth exchanges for a session
(`auth.signInWith(IDToken)`). The first sign-in creates the account - the
`on_auth_user_created` trigger fills the `profiles` row automatically - so
one button covers both signing in and signing up.

Until the one-time setup below is done, tapping the button shows a notice;
email/password sign-in is unaffected.

## What goes where (security model)

| Value | Where it lives | Why |
| --- | --- | --- |
| Google **Web client ID** | `GOOGLE_SERVER_CLIENT_ID` in `.env` -> BuildConfig | Public identifier; names the audience of the ID token. Safe to embed. |
| Google **Web client secret** | Supabase dashboard only | A secret - never in the app, never in git. |
| App **package name + SHA-1** fingerprint | Google Cloud Console (Android OAuth client) | Public by nature - it identifies the *build*, not a secret. |
| Supabase publishable key | `SUPABASE_PUBLISHABLE_KEY` in `.env` -> BuildConfig | Public by design; RLS restricts what it can read. |

## One-time setup

### 1. Google Cloud Console (console.cloud.google.com) — Web client

1. Create (or pick) a project, e.g. `StudentCodex`.
2. **APIs & Services -> OAuth consent screen**: choose *External*, app name
   `Student Codex`, add your email as contact. While the consent screen is in
   *Testing* status, also add your own Google account under *Test users*.
3. **APIs & Services -> Credentials -> Create credentials -> OAuth client ID**:
   - Application type: **Web application**
   - Authorized redirect URI:
     `https://wzhkfhijjwhteqkrlzfm.supabase.co/auth/v1/callback`
   - Create, then copy the **Client ID** (ends in `.apps.googleusercontent.com`)
     and the **Client Secret**.

### 2. Same project — register the Android app (REQUIRED)

> **This step is not optional.** Without it, Play services refuses the ID
> token request with GMS code 13 (`DEVELOPER_ERROR`) *before any account
> picker appears* — the app shows "Google rejected this app's sign-in
> request". A Web client alone is enough for Supabase, but NOT for Android's
> Credential Manager.

Still in **APIs & Services -> Credentials -> Create credentials -> OAuth
client ID**, create a second client:

- Application type: **Android**
- Package name: `com.example.studentcodex`
- SHA-1 certificate fingerprint: paste the fingerprint of the keystore the
  app is built with (how to get it below).

You do **not** need the Android client's own ID/secret for anything - its
job is to tell Google "this package name, signed with this key, belongs to
this project".

**Get your SHA-1 fingerprint** — from the StudentCodex root run:

```
gradlew signingReport
```

and read the `SHA1:` line of the `debug` variant. On this machine the debug
keystore's fingerprint is:

```
47:B1:1C:14:70:B1:B8:33:2B:88:96:F2:CA:C6:D2:28:C6:FB:09:93
```

Important:

- **Every machine has its own debug keystore.** A friend who builds the app
  on their own computer must add *their* debug SHA-1 as well (or install
  your built APK instead of building their own - your APK carries your
  fingerprint, which is the registered one).
- **Release builds** are signed with the release keystore: add that SHA-1
  too (same screen, or a second Android client) before shipping.
- The fingerprint is public information (anyone can extract it from a
  distributed APK); it is not a secret.

An equivalent alternative: create/link the project in **Firebase Console**
(Project settings -> Your apps -> Add Android app with the same package
name + SHA-1). Firebase writes the same registration into the same Google
Cloud project.

### 3. Supabase dashboard

Authentication -> Sign In / Providers -> **Google** -> enable, paste the
**Web** client ID and Client Secret from step 1 -> Save.

### 4. StudentCodex `.env`

Add the public **Web** client ID (NOT the secret, NOT the Android client's ID):

```
GOOGLE_SERVER_CLIENT_ID=1234-abc.apps.googleusercontent.com
```

Rebuild the app - Gradle reads `.env` at build time and exposes the value via
`BuildConfig.GOOGLE_SERVER_CLIENT_ID`. A missing or blank value never fails
the build; the Google button then shows the setup notice.

### 5. Run

The device/emulator needs two things: **Google Play services** (default on
current emulator images) and **a Google account signed in**:

- Physical device: normally already signed in.
- Emulator: open **Settings -> Google** (or Settings -> Accounts -> Add
  account) and sign in with any Google account *before* tapping the button.
  On a fresh emulator with no account the app shows "No Google account is
  signed in on this device yet" - that message means exactly what it says.

The account picker then lists the device's accounts and offers
"Use another account".

## Troubleshooting

Each failure now shows its own message (plus a diagnostic code in
parentheses where one exists):

- **Button shows the setup notice** -> `GOOGLE_SERVER_CLIENT_ID` missing/blank
  in `.env`, or the app was not rebuilt after editing it.
- **"No Google account is signed in on this device yet"** -> no account on
  the device. Add one in Settings (emulator: Settings -> Google). Not an app
  bug, nothing to change in code.
- **"Google rejected this app's sign-in request" (13)** -> GMS code 13,
  `DEVELOPER_ERROR`: the package name + SHA-1 of the *installed build* are
  not registered in the Google Cloud project of the web client ID. Re-check
  step 2, and remember: each machine's debug keystore has a different SHA-1.
  `adb logcat -s GoogleAuth` shows the underlying exception.
- **"Google sign-in did not work (Google error code N)"** -> other Play
  services failures; common codes: 7 network error, 10 client
  misconfiguration, 12500 internal/sign-in failure. Check
  `adb logcat -s GoogleAuth` for the full exception.
- **Supabase-side rejection** (remote error mentioning the provider) -> the
  Google provider is disabled in the dashboard, or the client ID there does
  not match the ID token's audience - re-check steps 3 and 4.
- Email/password accounts and Google accounts share the same `auth.users`
  table; signing in with Google creates a NEW account unless that email is
  already linked to a Google identity.

## Diagnosing from logcat

The `CredentialManagerGoogleAuth` logs every device-side failure under the
`GoogleAuth` tag:

```
adb logcat -s GoogleAuth
```

Tap the Google button and read the tagged line - it includes the exception
class, its Credential Manager type (GMS status code where applicable), and
the message.
