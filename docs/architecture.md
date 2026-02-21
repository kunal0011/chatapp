# Architecture Overview

## Backend
- Express REST APIs for auth, contacts, conversations, and history.
- Socket.IO namespace for realtime chat events.
- Prisma ORM with PostgreSQL.
- JWT access + refresh token rotation model.
- Layering: routes -> controllers -> services -> data access.

## Android
- Clean-ish layering: UI/ViewModel -> Repository -> API/Socket/DataStore.
- Jetpack Compose screens: Auth, Contacts, Chat.
- Hilt dependency injection and Navigation Compose.

## Security baseline
- Password hashing with bcrypt.
- Signed JWT tokens with expiration.
- Input validation with Zod on backend.
- Helmet, CORS allowlist, basic rate limiting.
- Route-level auth guards.

## Realtime flow
1. Client authenticates via REST.
2. Client connects Socket.IO with access token.
3. Client joins conversation room.
4. Client sends message event.
5. Server persists message and broadcasts to room.
