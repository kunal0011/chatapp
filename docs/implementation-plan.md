# Detailed Implementation Plan

## Phase 1: Foundation (completed scaffold)
- Define monorepo layout (`backend/`, `android/`, `docs/`).
- Set strict linting/formatting/test defaults for backend and test skeleton for Android.
- Add environment templates, Docker Compose for dependencies, and architecture notes.

## Phase 2: Backend platform (completed scaffold)
- Runtime and security setup:
  - Express app bootstrap with Helmet, CORS allowlist, JSON limits, and rate limiting.
  - Structured logging (`pino`) and centralized error handling.
  - Env schema validation with fail-fast startup.
- Data and persistence:
  - Prisma schema for `User`, `Conversation`, `ConversationMember`, `Message`, `RefreshToken`.
  - Seed script for local development users.
- Auth:
  - Register/login with password hashing.
  - JWT access + refresh token issuance and refresh-token rotation.
  - Logout with token revocation.
- Product APIs:
  - Contacts endpoint listing all active users except self.
  - Direct-conversation create-or-get endpoint.
  - Message history endpoint with cursor pagination and membership authorization.
- Realtime:
  - Socket.IO auth via access token.
  - Room join by conversation with server-side access checks.
  - Message send event with persistence + room broadcast + ack/error events.

## Phase 3: Android platform (completed scaffold)
- App baseline:
  - Jetpack Compose app with Hilt DI, Navigation Compose, and Material 3 theme.
  - BuildConfig for API and socket endpoints.
- Data layer:
  - Retrofit interfaces for auth, contacts, and conversations.
  - DataStore-backed session persistence.
  - Socket.IO client wrapper for realtime events.
- Domain/repository layer:
  - Interfaces and concrete implementations for auth, contacts, and chat.
- UI flow:
  - Auth screen (login/register mode).
  - Contacts screen (list all users and create direct conversation).
  - Chat screen (history load + realtime incoming + send text).

## Phase 4: Production hardening tasks (next actions)
- Backend
  - Add integration tests against ephemeral PostgreSQL (Testcontainers or dockerized test pipeline).
  - Implement refresh-token fingerprinting/device metadata and session management endpoints.
  - Add conversation-level idempotency keys for send retries.
  - Add OpenAPI schema generation and contract tests.
  - Add observability stack (metrics, traces, request correlation IDs).
- Android
  - Add automatic token refresh interceptor with retry and forced logout fallback.
  - Encrypt session secrets at rest (EncryptedDataStore/Keystore-backed storage).
  - Add offline caching strategy for recent conversations/messages.
  - Add screenshot/UI tests and network-failure test scenarios.

## Deployment readiness gates
- Security review completed (auth, CORS, rate-limit, input validation).
- Performance baseline measured (P95 API latency, socket delivery latency).
- Reliability baseline defined (error budgets, retry policies, on-call playbook).
- CI pipeline enforces lint, tests, and migration checks before release.
