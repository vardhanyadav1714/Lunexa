# Lunexa Cloudflare Backend Deployment

The backend is now implemented as a Cloudflare Worker in:

```text
backend/
```

## Deployment Architecture

```text
Android App
  -> Cloudflare Worker: lunexa-api
    -> Neon PostgreSQL
```

## Required Accounts

```text
Cloudflare account
Neon account
```

## Required Secrets

These must be configured in Cloudflare Workers:

```text
DATABASE_URL
JWT_ACCESS_SECRET
JWT_REFRESH_SECRET
```

`DATABASE_URL` comes from Neon and should look like:

```text
postgresql://USER:PASSWORD@HOST.neon.tech/DBNAME?sslmode=require
```

The JWT secrets should be different random strings with at least 32 characters each.

## One-Time Database Setup

Run this SQL in the Neon SQL Editor:

```text
backend/db/schema.sql
```

## Local Backend Commands

From `backend/`:

```bash
npm install
npm run typecheck
npm run deploy -- --dry-run
```

## Cloudflare Deploy Commands

From `backend/`:

```bash
npx wrangler login
npx wrangler secret put DATABASE_URL
npx wrangler secret put JWT_ACCESS_SECRET
npx wrangler secret put JWT_REFRESH_SECRET
npm run deploy
```

After deploy, the API base URL will be:

```text
https://lunexa-api.<your-cloudflare-subdomain>.workers.dev/api/v1
```

`workers_dev = true` is set in `backend/wrangler.toml` so the Worker is published on the default `workers.dev` URL.

## Current Verification

The backend currently passes:

```text
npm run typecheck
npm run deploy -- --dry-run
```

Actual live deployment still requires the Cloudflare login/session and Neon `DATABASE_URL`.
