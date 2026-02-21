# Backend

Node.js + TypeScript API and realtime server.

## Features
- Auth (register/login/refresh/logout)
- Contacts list
- Direct conversation create-or-get
- Message history with cursor pagination
- Socket.IO realtime message delivery

## Setup
1. `cp .env.example .env`
2. `npm install`
3. `docker compose up -d`
4. `npm run prisma:generate`
5. `npm run prisma:migrate`
6. `npm run prisma:seed`
7. `npm run dev`

## Important routes
- `GET /health`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/users/contacts`
- `POST /api/v1/conversations/direct`
- `GET /api/v1/conversations/:conversationId/messages`

## Socket auth
Pass `accessToken` during handshake:
- `auth: { token: "<JWT>" }`

## Quality commands
- `npm run lint`
- `npm run test`
- `npm run build`
