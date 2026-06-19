# Lunexa Backend

Cloudflare Workers + Hono + Neon PostgreSQL backend for Lunexa.

## What Is Implemented

```text
GET  /
GET  /health

POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/verification/start

GET  /api/v1/accounts
POST /api/v1/accounts

GET  /api/v1/categories
POST /api/v1/categories

GET    /api/v1/transactions
POST   /api/v1/transactions
PATCH  /api/v1/transactions/:transactionId
DELETE /api/v1/transactions/:transactionId

GET    /api/v1/budgets
POST   /api/v1/budgets
GET    /api/v1/budgets/:budgetId
PATCH  /api/v1/budgets/:budgetId
DELETE /api/v1/budgets/:budgetId

GET /api/v1/analytics/monthly-summary
GET /api/v1/analytics/category-summary
```

## Local Setup

Install dependencies:

```bash
npm install
```

Create local environment variables:

```bash
cp .dev.vars.example .dev.vars
```

Fill these values:

```text
DATABASE_URL
JWT_ACCESS_SECRET
JWT_REFRESH_SECRET
RESEND_API_KEY
VERIFICATION_EMAIL_FROM
```

Run the PostgreSQL schema on Neon:

```text
backend/db/schema.sql
```

You can paste it into the Neon SQL Editor, or run it with `psql` if installed.

Apply the email-verification migration before deploying the signup changes:

```bash
npm run migrate:email-verification
```

For local development only, set `EMAIL_VERIFICATION_MODE="dev"` in `.dev.vars`.
The worker logs and returns the one-time signup code in that mode. Do not use
dev mode in production. In production, set `RESEND_API_KEY` and
`VERIFICATION_EMAIL_FROM`; signup will not create a user until the email code is
verified.

Start local Worker:

```bash
npm run dev
```

## Cloudflare Deploy

Login to Cloudflare:

```bash
npx wrangler login
```

Set production secrets:

```bash
npx wrangler secret put DATABASE_URL
npx wrangler secret put JWT_ACCESS_SECRET
npx wrangler secret put JWT_REFRESH_SECRET
npx wrangler secret put RESEND_API_KEY
npx wrangler secret put VERIFICATION_EMAIL_FROM
```

Deploy:

```bash
npm run deploy
```

The deployed URL will look like:

```text
https://lunexa-api.<your-cloudflare-subdomain>.workers.dev
```

Use this as the Android API base URL:

```text
https://lunexa-api.<your-cloudflare-subdomain>.workers.dev/api/v1
```

## Notes

Passwords are hashed with PBKDF2-SHA256 using Web Crypto, which is compatible with Cloudflare Workers.

Refresh tokens are signed JWTs. For a deeper production system, add a `refresh_tokens` table to support server-side revocation and device-specific sessions.

Money values are returned as strings to avoid floating-point precision issues in TypeScript and Android.
