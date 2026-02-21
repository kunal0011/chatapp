# Production Practices Checklist

## Implemented in scaffold
- Strict TypeScript on backend.
- Input validation at route boundaries.
- Consistent API error handling.
- Password hashing and JWT auth/refresh flow.
- Route and socket authorization checks.
- Structured server logging.
- Security middleware (Helmet, CORS allowlist, rate limit).
- Conversation message persistence before broadcast.
- Layered Android architecture (UI -> repository -> data sources).
- Session persistence and login-state bootstrap.

## Must complete before production launch
- Secrets management with vault and rotation policy.
- TLS everywhere and secure cookie/session policy for web clients.
- Pen-test and abuse testing (spam/flood, brute-force, replay).
- End-to-end monitoring and alerting.
- Disaster recovery and database backup restore drills.
- Data retention and compliance policy implementation.
- CI/CD with gated migrations and blue/green or canary rollout.
