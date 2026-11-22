# Explainly — understand any YouTube video

Explainly turns a YouTube video into a beginner-friendly learning experience. It produces a simple overview, topic-by-topic explanations with real-world examples, "explain like I'm a beginner" and technical deep-dive modes, key concepts, a clickable timeline, SVG diagrams, 1-minute, 5-minute and detailed summaries, an AI tutor grounded in the video, editable notes with PDF export, scored quizzes, flashcards, bookmarks and learning history. It runs on the web and on mobile.

| Part | Stack | Folder |
|---|---|---|
| API | Java 21, Spring Boot 3.5, Spring Security (JWT + Google OAuth), Spring Data JPA, Flyway, PostgreSQL | `backend/` |
| Web | React 19, TypeScript, Vite, Tailwind CSS 4, Headless UI, TanStack Query, Axios, React Router | `web/` |
| Mobile | React Native 0.88 + Expo SDK 58, expo-router, SecureStore, expo-auth-session | `mobile/` |
| AI | Claude (official `anthropic-java` SDK, default `claude-opus-5-5`) or any OpenAI-compatible provider, behind one `LlmClient` interface | `backend/.../ai` |

The architecture, processing pipeline and data model are described in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). The full REST API is in [`docs/openapi.json`](docs/openapi.json), and Swagger UI is served at `/swagger-ui.html` while the backend runs.

---

## Quick start (Docker)

```bash
cd explainly
cp .env.example .env
# Required: set JWT_SECRET, TOKEN_ENCRYPTION_KEY and an AI key (ANTHROPIC_API_KEY or OPENAI_API_KEY)
#   openssl rand -base64 48   # JWT_SECRET
#   openssl rand -base64 32   # TOKEN_ENCRYPTION_KEY
docker compose up --build
```

- Web app: http://localhost:8088
- API and Swagger UI: http://localhost:8080/swagger-ui.html

## Local development

**Requirements:** JDK 21, Maven 3.9 or later, Node 22, and PostgreSQL 16 (or `docker compose up postgres`).

```bash
# 1. Database
createuser -P explainly        # password: explainly
createdb -O explainly explainly

# 2. Backend (http://localhost:8080). Flyway creates the schema on startup.
cd backend
export ANTHROPIC_API_KEY=...   # or AI_PROVIDER=openai OPENAI_API_KEY=...
mvn spring-boot:run

# 3. Web (http://localhost:5173). /api is proxied to :8080.
cd ../web && npm install && npm run dev

# 4. Mobile
cd ../mobile && npm install
cp .env.example .env           # set EXPO_PUBLIC_API_BASE_URL to an address the device can reach
npx expo start                 # press a / i, or scan the QR code
```

> **No AI key yet?** `mvn spring-boot:test-run` in `backend/` starts the real application with the deterministic **test** LLM from the test sources. You can click through the whole UI with it. It is never packaged into the production jar.

## Tests

```bash
cd backend && mvn test           # 58 unit + integration tests (H2 in PostgreSQL mode, scripted LLM)
cd web && npm test && npm run build
cd mobile && npm run typecheck
```

The backend integration tests cover the full learning journey (submit, transcript upload, analysis, chat with citations, notes and PDF, quiz grading, flashcards, bookmarks, progress). They also cover refresh-token rotation with reuse detection, guest upgrade, access isolation between users, caching of identical transcripts, SSRF and URL validation, and upload validation.

---

## Configuration

All configuration comes from environment variables. See [`.env.example`](.env.example). The client apps never see provider keys.

| Variable | Purpose |
|---|---|
| `JWT_SECRET` | HMAC key for access tokens (32 characters or more). **Required in production.** |
| `TOKEN_ENCRYPTION_KEY` | Base64 of 32 bytes. Used to encrypt YouTube OAuth tokens with AES-256-GCM. **Required in production.** |
| `AI_PROVIDER` | `anthropic` (default) or `openai`. If the chosen provider has no key, Explainly uses whichever provider is configured. |
| `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL`, `ANTHROPIC_EFFORT` | Claude settings. Defaults are `claude-opus-5-5` and effort `medium`. Server-side refusal fallbacks are on (`ANTHROPIC_SERVER_FALLBACKS=true`). |
| `OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_MODEL` | OpenAI, or any OpenAI-compatible endpoint (Ollama, Groq, vLLM and so on) |
| `AI_IMAGES_ENABLED` | Optional AI illustrations through OpenAI Images |
| `AUDIO_TRANSCRIPTION_ENABLED` | Optional Whisper-compatible transcription of audio that users upload |
| `YOUTUBE_API_KEY` | Optional. YouTube Data API v3 metadata (duration, description). Without it, Explainly uses YouTube's public oEmbed. |
| `GOOGLE_CLIENT_IDS` | Enables "Sign in with Google". List the web client ID first. |
| `GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_SECRET`, `GOOGLE_OAUTH_REDIRECT_URI` | Enable "Connect YouTube" |
| `CORS_ALLOWED_ORIGINS`, `APP_WEB_URL`, `OAUTH_ALLOWED_REDIRECT_PREFIXES` | Deployment URLs |
| `RATE_LIMIT_*`, `LIMIT_*` | Rate limits and daily quotas (guests get 3 new videos per day by default) |

Optional features turn themselves off when they aren't configured. `GET /api/auth/config` tells the clients what is available, so they never show a button that can't work.

### Setting up Google sign-in and YouTube connect

1. In the [Google Cloud Console](https://console.cloud.google.com/), create a project and configure the **OAuth consent screen**.
2. Enable **YouTube Data API v3**. Optionally create an **API key** for `YOUTUBE_API_KEY`.
3. Create a **Web application** OAuth client:
   - Authorized JavaScript origins: your web URL, for example `http://localhost:5173`.
   - Authorized redirect URI: `http://localhost:8080/api/youtube/oauth/callback`.
   - Use its ID as the first entry of `GOOGLE_CLIENT_IDS` and as `GOOGLE_OAUTH_CLIENT_ID`. Put its secret in `GOOGLE_OAUTH_CLIENT_SECRET`.
4. For mobile, create **iOS** and **Android** OAuth clients (bundle and package ID `com.explainly.app`). Add their IDs to `GOOGLE_CLIENT_IDS` and to `mobile/.env` (`EXPO_PUBLIC_GOOGLE_*`). Google sign-in on a phone needs a development build (`npx expo run:android` / `run:ios`) because Expo Go can't use custom OAuth redirects.

Explainly never asks for or stores Google passwords. Sign-in verifies Google ID tokens against Google's signing keys. YouTube connect requests only `youtube.force-ssl`, the minimum scope that `captions.download` accepts. Tokens are encrypted at rest, and **Disconnect** revokes the grant at Google.

## How transcripts are obtained (and limits)

The YouTube Data API **does not give out the transcript of every video**. `captions.download` only works for videos that the connected account can manage. Explainly doesn't scrape YouTube or get around its access rules, CAPTCHAs, DRM or copyright protections. For a video it can't fetch captions for, the analysis pauses with a clear explanation (status `AWAITING_TRANSCRIPT`), and the learner can do one of the following:

- paste the transcript (YouTube's "Show transcript" text, with timestamps, is parsed automatically)
- upload a `.srt`, `.vtt` or `.txt` file
- upload audio they have the rights to, which is transcribed when `AUDIO_TRANSCRIPTION_ENABLED` is on

Other providers can be added by implementing `TranscriptProvider`.

## Security notes

- Stateless JWT access tokens (30 minutes) and rotating, hashed refresh tokens. Reusing a refresh token revokes the whole session family.
- Roles: `GUEST`, `USER`, `ADMIN` (`/api/admin/**`). Every analysis is scoped to the user's library, and other users get 404.
- SSRF-safe by design: user URLs are only parsed against an allow-list of YouTube hosts. Outbound calls are built from the validated 11-character video ID.
- Diagrams are rendered on the server from validated specs. All text is XML-escaped, and the web app displays them as `<img>`. Model output is rendered as Markdown without raw HTML.
- Upload size and type allow-lists, UTF-8 validation, per-user and per-IP token-bucket rate limits, centralized RFC 7807 errors, CORS allow-list, and CSP and security headers.

## Deployment checklist

- Set `JWT_SECRET` and `TOKEN_ENCRYPTION_KEY`, and keep `TOKEN_ENCRYPTION_KEY` stable (changing it disconnects YouTube accounts).
- Put the API behind HTTPS. The web container's nginx proxies `/api` and sets security headers.
- Set `springdoc.api-docs.enabled=false` if the API docs shouldn't be public.
- The rate limiter keeps its state in memory. If you run more than one backend instance, move the buckets to Redis. The analysis pipeline already survives restarts because its state is in the database.
- Mobile releases: `npx eas-cli@latest build` with `EXPO_PUBLIC_API_BASE_URL` pointing at your HTTPS API.

## Demo video and screenshots

`scripts/demo-capture` records a demo video (MP4) and screenshots of a running instance, for example for LinkedIn, a portfolio or this README:

```bash
cd scripts/demo-capture
npm install && npx playwright install chromium
APP_URL=http://localhost:8088 VIDEO_URL="https://www.youtube.com/watch?v=..." TRANSCRIPT_FILE=./transcript.txt node capture.mjs
```

It analyzes the video first (not recorded), then records a clean walkthrough with no waiting. Output goes to `output/`. Install `ffmpeg` to get MP4 automatically.
