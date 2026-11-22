# Explainly — Architecture

Explainly turns a YouTube video into a structured learning experience: overview, topic-by-topic
explanations, beginner and technical modes, SVG diagrams, a grounded AI tutor, notes, quizzes,
flashcards, bookmarks and learning history — on web and mobile.

## 1. System overview

```
 ┌──────────────┐     ┌──────────────┐
 │ React (Vite) │     │ Expo (RN)    │      Clients never hold provider secrets.
 └──────┬───────┘     └──────┬───────┘
        │  HTTPS / JSON, Bearer JWT
        ▼                    ▼
 ┌──────────────────────────────────────────────────────────────┐
 │ Spring Boot API (Java 21)                                    │
 │  security: JWT filter, rate limiter, CORS, validation        │
 │  auth ─ user ─ youtube ─ transcript ─ video ─ ai             │
 │  explanation ─ illustration ─ chat ─ notes ─ quiz            │
 │  flashcards ─ bookmarks ─ history ─ admin ─ common           │
 │                                                              │
 │  AnalysisPipeline (@Async executor, DB-persisted status)     │
 └───────┬───────────────┬──────────────────┬───────────────────┘
         │               │                  │
   PostgreSQL     YouTube Data API     LLM providers (Anthropic, OpenAI,
   (Flyway)       Google OAuth/JWKS    any OpenAI-compatible endpoint),
                  oEmbed               OpenAI Images / Whisper (optional)
```

## 2. Processing pipeline

`POST /api/videos/analyze { url }`

1. **Validate URL** — strict host allow-list (`youtube.com`, `m.`, `www.`, `music.`, `youtu.be`,
   `youtube-nocookie.com`) and an 11-char ID regex. The server never fetches a user-supplied URL;
   outbound calls are built from the validated video ID only → SSRF is impossible by construction.
2. **Metadata** — YouTube Data API `videos.list` (if `YOUTUBE_API_KEY` set), otherwise official oEmbed.
3. **Reuse** — if a completed analysis of this video exists from an official caption source,
   the user is attached to it (no re-processing). If the user already has an analysis for it, it is returned.
4. **Async job** (status persisted on `video_analyses`):
   `PENDING → FETCHING_TRANSCRIPT → PROCESSING → GENERATING_EXPLANATION → GENERATING_ILLUSTRATIONS → COMPLETED | FAILED`
   plus `AWAITING_TRANSCRIPT` when no permitted transcript source is available. The UI then
   explains the limitation and offers paste / upload / audio transcription.
5. **Transcript providers** (`TranscriptProvider` chain, ordered):
   - `YouTubeCaptionsProvider` — `captions.list` + `captions.download` with the user's OAuth token
     (YouTube only allows this for videos the account can manage).
   - Manual: pasted text, uploaded `.txt/.srt/.vtt` (parsed into timed segments).
   - `AudioTranscriptionService` — user-uploaded audio → OpenAI-compatible Whisper API.
6. **AI generation** — transcript is chunked to fit the model; long videos are summarised chunk-wise,
   then one structured JSON generation produces overview, chapters (with timestamps), per-topic
   simple/detailed explanations, examples, use cases, key points, beginner & technical modes,
   concepts, 1-min/5-min/detailed summaries and revision notes. A second call produces diagram specs.
7. **Illustrations** — diagram specs (`flowchart | architecture | process | mindmap | comparison |
   steps | concept`) are validated and rendered to SVG on the server by `SvgDiagramRenderer`
   (escaped text, no scripts). Optional AI image generation for `concept` illustrations.
8. Jobs left in a non-terminal state are re-queued on startup.

Generated content is cached per (video, transcript hash): identical transcripts are never re-processed.

## 3. Module layout (backend `com.explainly`)

| module | responsibility |
|---|---|
| `auth` | register/login, guest sessions, Google ID-token sign-in, JWT + rotating refresh tokens |
| `user` | `users` entity, profile, roles (`GUEST`, `USER`, `ADMIN`) |
| `youtube` | URL validation, metadata client, OAuth connect/disconnect/revoke, encrypted token storage |
| `transcript` | provider chain, SRT/VTT/plain parsing, uploads, audio transcription |
| `video` | videos + analyses, submission, status, pipeline orchestration |
| `ai` | `LlmClient` abstraction (Anthropic, OpenAI-compatible), prompts, JSON extraction, chunking |
| `explanation` | chapters + explanation persistence and DTOs |
| `illustration` | diagram spec model, SVG renderer, AI image generation |
| `chat` | grounded Q&A with transcript retrieval and timestamp citations |
| `notes` | per-user editable notes, PDF export |
| `quiz` | quiz generation, grading, attempts/score tracking |
| `flashcards` | flashcard generation |
| `bookmarks` | concept / timestamp bookmarks |
| `history` | learning history + progress |
| `common` | errors, rate limiting, crypto, config, pagination |

## 4. Data model

`users`, `refresh_tokens`, `connected_accounts`, `videos`, `transcripts`, `video_analyses`,
`video_chapters`, `explanations`, `illustrations`, `notes`, `quizzes`, `quiz_questions`,
`quiz_attempts`, `flashcards`, `bookmarks`, `learning_history`, `chat_messages`.
See `backend/src/main/resources/db/migration`. Structured AI output is stored as JSON text so that
the schema runs on PostgreSQL in production and H2 (PostgreSQL mode) in tests.

## 5. Security

- Stateless JWT (HS256, 30 min) + opaque refresh tokens (SHA-256 hashed, rotated, 30 days).
- Passwords: BCrypt. Google sign-in: ID token verified against Google JWKS, `iss` and `aud` checked.
- YouTube OAuth: authorization-code flow with signed `state`, minimum scope
  `youtube.force-ssl` (required by `captions.download`), tokens AES-256-GCM encrypted at rest,
  disconnect revokes at Google.
- Rate limiting (token buckets per user/IP, stricter for analyze/chat/AI generation).
- Upload limits and type allow-lists; centralized `ProblemDetail` errors; no stack traces leak.
- All secrets come from environment variables; the clients never see provider keys.
