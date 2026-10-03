# ARIA Voice Assistant — Base44 Dev Environment

## What this is

A native Android app (Kotlin + Jetpack Compose) that builds to an APK. It is **not** a web app — the Base44 preview serves a build-status page on port 3000 from which you download the compiled debug APK.

## Architecture

- **Language:** Kotlin, Jetpack Compose UI
- **Build:** Gradle 9.3.1 (no wrapper in repo), AGP 9.1.1, JDK 17
- **SDK:** compileSdk 36 (minorApiLevel 1), minSdk 24, targetSdk 36
- **Key deps:** Firebase AI (Gemini), Room, Retrofit/OkHttp, Moshi, reCAPTCHA AppCheck
- **Package:** `com.aistudio.ariavoice.assistant`

## Docker setup

`docker-compose.base44.yml` builds a JDK 17 image with the Android SDK (platform 36, build-tools 36.0.0) and Gradle 9.3.1. The source is bind-mounted at `/app`. A Python server (`scripts/base44_server.py`) runs the Gradle build in a background thread and serves a status page on port 3000.

- **Build command:** `gradle :app:assembleDebug --no-daemon --stacktrace`
- **APK output:** `app/build/outputs/apk/debug/app-debug.apk`
- **Gradle cache:** persisted in a Docker volume (`gradle-cache`)

## Secrets

- `GEMINI_API_KEY` — Google Gemini API key. The build works with the placeholder (`MY_GEMINI_API_KEY` from `.env.example`); the app falls back to a local response when the key is missing or placeholder. Provide a real key via the Base44 Secrets panel for AI features.
- The secrets Gradle plugin reads `.env` (created at runtime from the env var) then falls back to `.env.example`.
- `google-services.json` is not present; the google-services plugin is set to `WARN` and `googleServices.missing.passthrough=true` in `gradle.properties`.

## Runtime files (gitignored, created at build time)

- `local.properties` — `sdk.dir` pointing to the Android SDK in the container
- `debug.keystore` — auto-generated debug signing key
- `.env` — created from the `GEMINI_API_KEY` env var for the secrets plugin

## Verifying the build

```bash
docker compose -f docker-compose.base44.yml up -d --build
curl -s http://localhost:3000/status   # → {"status":"success","apkSize":...}
```

The status page at `http://localhost:3000` shows build progress, logs, and an APK download link.
