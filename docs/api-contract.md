# API Contract (v1)

Base path: `/api/v1`

## Auth
- `POST /auth/register`
  - Body: `{ "phone": "+15550000001", "displayName": "Alice", "password": "Password123" }`
  - Returns: `{ user, accessToken, refreshToken }`
- `POST /auth/login`
  - Body: `{ "phone": "+15550000001", "password": "Password123" }`
  - Returns: `{ user, accessToken, refreshToken }`
- `POST /auth/refresh`
  - Body: `{ "refreshToken": "..." }`
  - Returns: `{ user, accessToken, refreshToken }`
- `POST /auth/logout`
  - Body: `{ "refreshToken": "..." }`
  - Returns: `204`

## Users
- `GET /users/contacts`
  - Auth required
  - Returns: `{ contacts: User[] }`

## Conversations
- `POST /conversations/direct`
  - Auth required
  - Body: `{ "otherUserId": "cuid" }`
  - Returns: `{ conversation: Conversation }`

## Messages
- `GET /conversations/:conversationId/messages?cursor=<id>&limit=30`
  - Auth required + conversation membership
  - Returns: `{ messages: Message[], nextCursor: string | null }`

## Realtime events
- Client -> Server
  - `conversation:join` -> payload `conversationId`
  - `message:send` -> payload `{ conversationId, content, clientTempId }`
- Server -> Client
  - `conversation:joined` -> `{ conversationId }`
  - `conversation:error` -> `{ conversationId, message }`
  - `message:new` -> `{ message }`
  - `message:ack` -> `{ clientTempId, messageId, createdAt }`
  - `message:error` -> `{ clientTempId, message }`
