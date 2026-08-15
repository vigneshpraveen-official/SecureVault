# SecureVault

A full-stack password vault & credential management system, built for the Infosys Springboard Java Full Stack virtual internship.

SecureVault lets a user register, authenticate with JWT + MFA (TOTP), store credentials encrypted at rest with AES-256-GCM, generate and analyse password strength, share credentials with scoped permissions, and monitor account activity through audit logs, device tracking, and a security dashboard.

## Tech stack

**Backend**
- Java 21, Spring Boot 3.5.x (Spring Web, Security, Data JPA, Validation, Actuator, Data Redis)
- PostgreSQL 16, Flyway migrations
- Redis (session/token caching)
- JWT (jjwt) authentication with refresh tokens, TOTP-based MFA
- AES-256-GCM field-level encryption for stored credentials
- Maven, Spotless (Google Java Format, AOSP style)

**Frontend**
- React 18 + Vite
- Redux Toolkit, React Router
- Tailwind CSS
- Vitest + React Testing Library

**Infrastructure**
- Docker Compose for local Postgres/Redis/MailHog
- Deployment target: Render (backend + frontend), Neon (Postgres), Upstash (Redis)

## Core features

- Email/password registration and login with JWT access + refresh tokens
- Multi-factor authentication (TOTP) with backup codes
- Encrypted credential vault: create, read, update, soft-delete, password history
- Password strength analysis and a configurable password generator
- Vault health scoring (weak/reused/stale password detection)
- Credential sharing with scoped, revocable permissions
- Device tracking, login-attempt monitoring, and security alerts
- Audit trail for all sensitive actions
- Notifications (password expiry, sharing, security events)
- Admin console: user management, platform stats, audit log review
- Dashboard with activity summaries and top items to fix

## Project status

Built in phases against a defined roadmap (auth/vault core, API hardening, password intelligence, data integrity & performance, sharing/sessions/platform hardening, React frontend, testing & quality). Backend and frontend test suites are green; containerization and cloud deployment are in progress.

## License

MIT — see [LICENSE](LICENSE).
