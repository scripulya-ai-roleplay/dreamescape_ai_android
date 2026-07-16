# DreameScape AI (Android client)

The Android front-end for the **scripulya** roleplay-chat platform — a Jetpack
Compose app that lets users browse scenes and characters, start LLM-driven
roleplay chats, and manage their own content.

This repository contains **only the client**. The backend (FastAPI HTTP API,
internally named "Gemini Chat") and the LLM worker live in sibling repos
(`scripulya_ai`, `scripulya_agent`, `scripulya_deploy`). The client talks to the
HTTP API through a generated OpenAPI Kotlin client.

## Requirements

- Android Studio (Koala+/2024.x or newer) or a JDK 11+ / Android SDK command line.
- An Android emulator (or device) running **API 33+** (`minSdk = 33`, `targetSdk = 36`).
- The scripulya backend running and reachable from the emulator at `http://10.0.2.2:8000`.

## Build & run

```bash
# Debug APK
./gradlew assembleDebug

# Install on a connected emulator/device and launch
./gradlew installDebug
adb shell am start -n com.example.dreamescape_ai/.MainActivity

# Kotlin compile check (fastest feedback loop)
./gradlew compileDebugKotlin

# Unit / instrumented tests
./gradlew test
./gradlew connectedAndroidTest
```

Open and run it from Android Studio as usual — the launcher activity is `MainActivity`.

## Tech stack

| Area            | Choice                                                        |
|-----------------|---------------------------------------------------------------|
| UI              | Jetpack Compose (BOM `2024.09.00`), Material 3                |
| Language        | Kotlin `2.0.21`, JVM target 11                                |
| Build           | Gradle (Kotlin DSL) + version catalog, AGP `9.0.1`           |
| API client      | Generated OpenAPI client (`openapi-generator` 7.7.0)         |
| HTTP / JSON     | OkHttp `4.12.0`, Moshi `1.15.1`                              |
| Images          | Coil `2.7.0`                                                  |
| Markdown        | multiplatform-markdown-renderer-m3 `0.27.0` (chat messages)  |
| Min / target SDK | 33 / 36                                                       |

## Architecture

The app uses an **activity-per-screen** navigation model (Intents, not Compose
Navigation) on top of a small MVVM layer:

- **`*Activity.kt`** — a `ComponentActivity` per screen, hosting a Compose root.
  `MainActivity` is the launcher and hosts the discovery feed + bottom nav.
- **`*ViewModel.kt`** — each screen owns a `ViewModel` that exposes a single
  `StateFlow<UiState>`. Backend calls go through **injected lambda defaults**
  (e.g. `getSceneCall: (UUID) -> ApiResponseScene = { ScenesApi().…() }`), which
  keeps the ViewModels decoupled from the generated API and trivially testable
  with fakes.
- **`org.openapitools.client`** — the generated API client (`apis/`, `models/`,
  `infrastructure/`). Treat it as build output: never hand-edit (see below).
- **`model/`** — UI-facing domain models (`StoryItem`, `FeedSection`, `UserProfile`, …).
- **`ui/screens`**, **`ui/components`**, **`ui/theme`** — Compose screens, reusable
  components, and the Material theme.

### Backend integration

Wired in `DreamescapeApplication`:

- **Base URL** — `http://10.0.2.2:8000`. `10.0.2.2` is the emulator's alias for the
  host loopback, so the backend on your machine is reachable from the emulator.
  To run against a different host (e.g. a physical device), change
  `DreamescapeApplication.BACKEND_BASE_URL`.
- **Auth** — every backend endpoint requires an HTTP Bearer JWT. The backend uses
  symmetric **HS256** with a shared secret, so the client self-signs a token
  (`auth/JwtConfig.kt`, dev secret `dev-secret-change-me`, fixed subject UUID,
  1-hour TTL). `JwtAuthInterceptor` is installed on the shared OkHttp client and
  mints a fresh token per request.
- **Images** — Coil uses a **plain** OkHttp client (no JWT interceptor): media URLs
  are MinIO presigned/public URLs, and adding an `Authorization` header to a
  presigned URL makes MinIO reject it.

## Regenerating the API client

The client under `app/src/main/java/org/openapitools/client/` is generated from
`app/src/main/openapi.json`, which is a **downloaded artifact** (never hand-edited).
To bring it up to date with a running backend:

```bash
# 1. Pull the live spec
curl http://localhost:8000/openapi.json -o app/src/main/openapi.json

# 2. Regenerate with openapi-generator 7.7.0 (matches the committed client)
java -jar <openapi-generator-cli-7.7.0.jar> generate \
  -g kotlin -i app/src/main/openapi.json -o /tmp/gen \
  -c /tmp/gen_config.json
#   gen_config.json:
#   {"packageName":"org.openapitools.client","library":"jvm-okhttp4",
#    "serializationLibrary":"moshi","moshiCodeGen":false,"dateLibrary":"java8"}

# 3. Copy ONLY the new/changed files into app/src/main/java/org/openapitools/client/
#    Leave the existing ValidationErrorLocInner.kt (typealias form) untouched.
```

Regenerating with a newer CLI rewrites every file (different base-class shape,
`BASE_URL_KEY`, etc.) and breaks `DreamescapeApplication`'s `ApiClient.baseUrlKey`
reference — stick to 7.7.0 for a faithful minimal diff.

## Developer notes & caveats

- **`MediaUploader.kt`** — the generated `MediaApi.uploadMedia` is broken for binary
  uploads (the spec declares the file part with `contentMediaType`, so the generator
  emits a `String` form part). Use `MediaUploader`, which builds the multipart
  request manually on top of `ApiClient.defaultClient`.
- **Create endpoints require `owner_id`** matching the JWT subject, even though the
  OpenAPI spec lists it as optional; every create call sets `owner_id = JwtTokenProvider().userId`.
- **Chat creation ignores the client-sent `id`** — the backend mints its own and
  returns `{"result":{"id":"<uuid>"}}`. Always navigate using the server-returned id.
- **A chat's persona** (`user_character_id`, the character the user plays as) can be
  set at creation time by passing it in the `Chat` body; the
  `POST /chats/{id}/persona` endpoint is only for changing an existing chat's persona.
- The JWT secret and subject are **hardcoded for development only** — replace them
  before any non-dev distribution.
