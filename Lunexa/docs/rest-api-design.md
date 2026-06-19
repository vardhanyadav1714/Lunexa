# Lunexa REST API Design

Base path:

```text
/api/v1
```

All request and response bodies use JSON.

Protected endpoints require:

```http
Authorization: Bearer <access_token>
```

IDs are UUID strings. Timestamps are ISO-8601 strings. Monetary amounts are decimal strings, not floating-point numbers, to avoid precision loss in TypeScript and Android clients.

## Common Response Shapes

Success envelope:

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01JZ..."
  }
}
```

List envelope:

```json
{
  "data": [],
  "meta": {
    "requestId": "req_01JZ...",
    "pagination": {
      "limit": 50,
      "cursor": "2026-06-01T10:30:00.000Z_4f5b...",
      "nextCursor": "2026-05-29T09:15:00.000Z_91ab...",
      "hasMore": true
    }
  }
}
```

Error envelope:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request body is invalid.",
    "details": [
      {
        "field": "email",
        "message": "Email is required."
      }
    ],
    "requestId": "req_01JZ..."
  }
}
```

## Authentication

### Register

```http
POST /api/v1/auth/register
```

Request:

```json
{
  "fullName": "Aarav Sharma",
  "email": "aarav@example.com",
  "password": "StrongPass@123"
}
```

Response `201 Created`:

```json
{
  "data": {
    "user": {
      "id": "32d7ed2e-cad8-4351-8a35-76b76d244a0c",
      "fullName": "Aarav Sharma",
      "email": "aarav@example.com",
      "status": "ACTIVE",
      "createdAt": "2026-06-17T00:30:00.000Z"
    },
    "tokens": {
      "accessToken": "eyJhbGciOiJIUzI1NiIs...",
      "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
      "expiresIn": 900,
      "refreshExpiresIn": 2592000
    }
  },
  "meta": {
    "requestId": "req_01JZ..."
  }
}
```

Errors:

```text
400 VALIDATION_ERROR
409 EMAIL_ALREADY_EXISTS
429 RATE_LIMITED
500 INTERNAL_SERVER_ERROR
```

### Login

```http
POST /api/v1/auth/login
```

Request:

```json
{
  "email": "aarav@example.com",
  "password": "StrongPass@123"
}
```

Response `200 OK`:

```json
{
  "data": {
    "user": {
      "id": "32d7ed2e-cad8-4351-8a35-76b76d244a0c",
      "fullName": "Aarav Sharma",
      "email": "aarav@example.com",
      "status": "ACTIVE",
      "lastLoginAt": "2026-06-17T00:35:00.000Z"
    },
    "tokens": {
      "accessToken": "eyJhbGciOiJIUzI1NiIs...",
      "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
      "expiresIn": 900,
      "refreshExpiresIn": 2592000
    }
  },
  "meta": {
    "requestId": "req_01JZ..."
  }
}
```

Errors:

```text
400 VALIDATION_ERROR
401 INVALID_CREDENTIALS
403 USER_DISABLED
429 RATE_LIMITED
500 INTERNAL_SERVER_ERROR
```

### Refresh Token

```http
POST /api/v1/auth/refresh
```

Request:

```json
{
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

Response `200 OK`:

```json
{
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 900,
    "refreshExpiresIn": 2592000
  },
  "meta": {
    "requestId": "req_01JZ..."
  }
}
```

Errors:

```text
400 VALIDATION_ERROR
401 INVALID_REFRESH_TOKEN
401 REFRESH_TOKEN_EXPIRED
500 INTERNAL_SERVER_ERROR
```

## Transactions

Transaction object:

```json
{
  "id": "8de99b8d-5ce1-497f-9b97-bf35d9c86d45",
  "accountId": "d97e7c39-7977-4afc-9a50-567c173176c5",
  "transferAccountId": null,
  "categoryId": "14ad0d08-d6c2-4aa9-9718-4c59d8074d38",
  "type": "EXPENSE",
  "amount": "499.00",
  "currency": "INR",
  "note": "Dinner",
  "merchant": "Cafe Luna",
  "transactionDate": "2026-06-15",
  "postedAt": "2026-06-15T18:30:00.000Z",
  "metadata": {},
  "createdAt": "2026-06-17T00:40:00.000Z",
  "updatedAt": "2026-06-17T00:40:00.000Z",
  "version": 1
}
```

### Create Transaction

```http
POST /api/v1/transactions
```

Request:

```json
{
  "clientMutationId": "android-uuid-for-idempotency",
  "accountId": "d97e7c39-7977-4afc-9a50-567c173176c5",
  "categoryId": "14ad0d08-d6c2-4aa9-9718-4c59d8074d38",
  "type": "EXPENSE",
  "amount": "499.00",
  "currency": "INR",
  "note": "Dinner",
  "merchant": "Cafe Luna",
  "transactionDate": "2026-06-15",
  "postedAt": "2026-06-15T18:30:00.000Z",
  "metadata": {}
}
```

Transfer request:

```json
{
  "clientMutationId": "android-uuid-for-idempotency",
  "accountId": "source-account-id",
  "transferAccountId": "target-account-id",
  "type": "TRANSFER",
  "amount": "1000.00",
  "currency": "INR",
  "note": "Move to savings",
  "transactionDate": "2026-06-15"
}
```

Response `201 Created`:

```json
{
  "data": {
    "transaction": {
      "id": "8de99b8d-5ce1-497f-9b97-bf35d9c86d45",
      "accountId": "d97e7c39-7977-4afc-9a50-567c173176c5",
      "transferAccountId": null,
      "categoryId": "14ad0d08-d6c2-4aa9-9718-4c59d8074d38",
      "type": "EXPENSE",
      "amount": "499.00",
      "currency": "INR",
      "note": "Dinner",
      "merchant": "Cafe Luna",
      "transactionDate": "2026-06-15",
      "postedAt": "2026-06-15T18:30:00.000Z",
      "metadata": {},
      "createdAt": "2026-06-17T00:40:00.000Z",
      "updatedAt": "2026-06-17T00:40:00.000Z",
      "version": 1
    }
  },
  "meta": {
    "requestId": "req_01JZ..."
  }
}
```

Errors:

```text
400 VALIDATION_ERROR
401 UNAUTHORIZED
404 ACCOUNT_NOT_FOUND
404 CATEGORY_NOT_FOUND
409 DUPLICATE_MUTATION
500 INTERNAL_SERVER_ERROR
```

### Update Transaction

```http
PATCH /api/v1/transactions/{transactionId}
```

Request:

```json
{
  "expectedVersion": 1,
  "categoryId": "14ad0d08-d6c2-4aa9-9718-4c59d8074d38",
  "amount": "549.00",
  "note": "Dinner with tax",
  "merchant": "Cafe Luna",
  "transactionDate": "2026-06-15"
}
```

Response `200 OK`:

```json
{
  "data": {
    "transaction": {
      "id": "8de99b8d-5ce1-497f-9b97-bf35d9c86d45",
      "accountId": "d97e7c39-7977-4afc-9a50-567c173176c5",
      "transferAccountId": null,
      "categoryId": "14ad0d08-d6c2-4aa9-9718-4c59d8074d38",
      "type": "EXPENSE",
      "amount": "549.00",
      "currency": "INR",
      "note": "Dinner with tax",
      "merchant": "Cafe Luna",
      "transactionDate": "2026-06-15",
      "postedAt": "2026-06-15T18:30:00.000Z",
      "metadata": {},
      "createdAt": "2026-06-17T00:40:00.000Z",
      "updatedAt": "2026-06-17T00:45:00.000Z",
      "version": 2
    }
  },
  "meta": {
    "requestId": "req_01JZ..."
  }
}
```

Errors:

```text
400 VALIDATION_ERROR
401 UNAUTHORIZED
404 TRANSACTION_NOT_FOUND
409 VERSION_CONFLICT
500 INTERNAL_SERVER_ERROR
```

### Delete Transaction

```http
DELETE /api/v1/transactions/{transactionId}
```

Request body is empty.

Response `204 No Content`

Errors:

```text
401 UNAUTHORIZED
404 TRANSACTION_NOT_FOUND
409 VERSION_CONFLICT
500 INTERNAL_SERVER_ERROR
```

Optional query parameter for safer offline sync deletes:

```http
DELETE /api/v1/transactions/{transactionId}?expectedVersion=2
```

### List Transactions

```http
GET /api/v1/transactions
```

Query parameters:

```text
accountId optional UUID
categoryId optional UUID
type optional INCOME | EXPENSE | TRANSFER
from optional YYYY-MM-DD
to optional YYYY-MM-DD
limit optional integer, default 50, max 100
cursor optional string
sort optional transactionDateDesc | transactionDateAsc
```

Response `200 OK`:

```json
{
  "data": [
    {
      "id": "8de99b8d-5ce1-497f-9b97-bf35d9c86d45",
      "accountId": "d97e7c39-7977-4afc-9a50-567c173176c5",
      "transferAccountId": null,
      "categoryId": "14ad0d08-d6c2-4aa9-9718-4c59d8074d38",
      "type": "EXPENSE",
      "amount": "549.00",
      "currency": "INR",
      "note": "Dinner with tax",
      "merchant": "Cafe Luna",
      "transactionDate": "2026-06-15",
      "postedAt": "2026-06-15T18:30:00.000Z",
      "metadata": {},
      "createdAt": "2026-06-17T00:40:00.000Z",
      "updatedAt": "2026-06-17T00:45:00.000Z",
      "version": 2
    }
  ],
  "meta": {
    "requestId": "req_01JZ...",
    "pagination": {
      "limit": 50,
      "cursor": null,
      "nextCursor": null,
      "hasMore": false
    }
  }
}
```

Errors:

```text
400 VALIDATION_ERROR
401 UNAUTHORIZED
500 INTERNAL_SERVER_ERROR
```

## Budgets

Budget object:

```json
{
  "id": "0dc24020-3bfe-481f-8cdd-a5e2d153f7b3",
  "categoryId": "14ad0d08-d6c2-4aa9-9718-4c59d8074d38",
  "periodMonth": "2026-06-01",
  "amount": "12000.00",
  "alertThresholdPercent": "80.00",
  "createdAt": "2026-06-17T01:00:00.000Z",
  "updatedAt": "2026-06-17T01:00:00.000Z",
  "version": 1
}
```

### Create Budget

```http
POST /api/v1/budgets
```

Request:

```json
{
  "categoryId": "14ad0d08-d6c2-4aa9-9718-4c59d8074d38",
  "periodMonth": "2026-06-01",
  "amount": "12000.00",
  "alertThresholdPercent": "80.00"
}
```

Response `201 Created`:

```json
{
  "data": {
    "budget": {
      "id": "0dc24020-3bfe-481f-8cdd-a5e2d153f7b3",
      "categoryId": "14ad0d08-d6c2-4aa9-9718-4c59d8074d38",
      "periodMonth": "2026-06-01",
      "amount": "12000.00",
      "alertThresholdPercent": "80.00",
      "createdAt": "2026-06-17T01:00:00.000Z",
      "updatedAt": "2026-06-17T01:00:00.000Z",
      "version": 1
    }
  },
  "meta": {
    "requestId": "req_01JZ..."
  }
}
```

Errors:

```text
400 VALIDATION_ERROR
401 UNAUTHORIZED
404 CATEGORY_NOT_FOUND
409 BUDGET_ALREADY_EXISTS
500 INTERNAL_SERVER_ERROR
```

### List Budgets

```http
GET /api/v1/budgets
```

Query parameters:

```text
periodMonth optional YYYY-MM-01
fromMonth optional YYYY-MM-01
toMonth optional YYYY-MM-01
categoryId optional UUID
```

Response `200 OK`:

```json
{
  "data": [
    {
      "id": "0dc24020-3bfe-481f-8cdd-a5e2d153f7b3",
      "categoryId": "14ad0d08-d6c2-4aa9-9718-4c59d8074d38",
      "periodMonth": "2026-06-01",
      "amount": "12000.00",
      "alertThresholdPercent": "80.00",
      "createdAt": "2026-06-17T01:00:00.000Z",
      "updatedAt": "2026-06-17T01:00:00.000Z",
      "version": 1
    }
  ],
  "meta": {
    "requestId": "req_01JZ..."
  }
}
```

### Get Budget

```http
GET /api/v1/budgets/{budgetId}
```

Response `200 OK`:

```json
{
  "data": {
    "budget": {
      "id": "0dc24020-3bfe-481f-8cdd-a5e2d153f7b3",
      "categoryId": "14ad0d08-d6c2-4aa9-9718-4c59d8074d38",
      "periodMonth": "2026-06-01",
      "amount": "12000.00",
      "alertThresholdPercent": "80.00",
      "createdAt": "2026-06-17T01:00:00.000Z",
      "updatedAt": "2026-06-17T01:00:00.000Z",
      "version": 1
    }
  },
  "meta": {
    "requestId": "req_01JZ..."
  }
}
```

Errors:

```text
401 UNAUTHORIZED
404 BUDGET_NOT_FOUND
500 INTERNAL_SERVER_ERROR
```

### Update Budget

```http
PATCH /api/v1/budgets/{budgetId}
```

Request:

```json
{
  "expectedVersion": 1,
  "amount": "15000.00",
  "alertThresholdPercent": "85.00"
}
```

Response `200 OK`:

```json
{
  "data": {
    "budget": {
      "id": "0dc24020-3bfe-481f-8cdd-a5e2d153f7b3",
      "categoryId": "14ad0d08-d6c2-4aa9-9718-4c59d8074d38",
      "periodMonth": "2026-06-01",
      "amount": "15000.00",
      "alertThresholdPercent": "85.00",
      "createdAt": "2026-06-17T01:00:00.000Z",
      "updatedAt": "2026-06-17T01:10:00.000Z",
      "version": 2
    }
  },
  "meta": {
    "requestId": "req_01JZ..."
  }
}
```

Errors:

```text
400 VALIDATION_ERROR
401 UNAUTHORIZED
404 BUDGET_NOT_FOUND
409 VERSION_CONFLICT
500 INTERNAL_SERVER_ERROR
```

### Delete Budget

```http
DELETE /api/v1/budgets/{budgetId}
```

Request body is empty.

Response `204 No Content`

Errors:

```text
401 UNAUTHORIZED
404 BUDGET_NOT_FOUND
409 VERSION_CONFLICT
500 INTERNAL_SERVER_ERROR
```

Optional query parameter:

```http
DELETE /api/v1/budgets/{budgetId}?expectedVersion=2
```

## Analytics

Analytics endpoints return calculated read models. They should not mutate state.

### Monthly Summary

```http
GET /api/v1/analytics/monthly-summary
```

Query parameters:

```text
month required YYYY-MM
accountId optional UUID
```

Response `200 OK`:

```json
{
  "data": {
    "month": "2026-06",
    "currency": "INR",
    "incomeTotal": "85000.00",
    "expenseTotal": "42350.00",
    "transferTotal": "10000.00",
    "netCashflow": "42650.00",
    "transactionCount": 62,
    "largestExpense": {
      "transactionId": "8de99b8d-5ce1-497f-9b97-bf35d9c86d45",
      "amount": "4500.00",
      "categoryId": "14ad0d08-d6c2-4aa9-9718-4c59d8074d38",
      "transactionDate": "2026-06-10"
    },
    "budgetSummary": {
      "budgetedAmount": "50000.00",
      "spentAmount": "42350.00",
      "remainingAmount": "7650.00",
      "utilizationPercent": "84.70"
    }
  },
  "meta": {
    "requestId": "req_01JZ..."
  }
}
```

Errors:

```text
400 VALIDATION_ERROR
401 UNAUTHORIZED
500 INTERNAL_SERVER_ERROR
```

### Category Summary

```http
GET /api/v1/analytics/category-summary
```

Query parameters:

```text
month required YYYY-MM
type optional INCOME | EXPENSE
accountId optional UUID
```

Response `200 OK`:

```json
{
  "data": {
    "month": "2026-06",
    "currency": "INR",
    "type": "EXPENSE",
    "categories": [
      {
        "categoryId": "14ad0d08-d6c2-4aa9-9718-4c59d8074d38",
        "categoryName": "Food",
        "iconKey": "restaurant",
        "colorHex": "#EF4444",
        "totalAmount": "12350.00",
        "transactionCount": 18,
        "percentage": "29.16",
        "budgetAmount": "12000.00",
        "budgetUtilizationPercent": "102.92"
      }
    ]
  },
  "meta": {
    "requestId": "req_01JZ..."
  }
}
```

Errors:

```text
400 VALIDATION_ERROR
401 UNAUTHORIZED
500 INTERNAL_SERVER_ERROR
```

## Error Handling Strategy

Use a single error envelope for all failed responses. Never return raw database, JWT, or stack trace messages to the client.

Recommended HTTP mapping:

```text
400 VALIDATION_ERROR
401 UNAUTHORIZED
401 INVALID_CREDENTIALS
401 INVALID_REFRESH_TOKEN
403 FORBIDDEN
403 USER_DISABLED
404 RESOURCE_NOT_FOUND
409 VERSION_CONFLICT
409 DUPLICATE_MUTATION
409 EMAIL_ALREADY_EXISTS
409 BUDGET_ALREADY_EXISTS
429 RATE_LIMITED
500 INTERNAL_SERVER_ERROR
503 SERVICE_UNAVAILABLE
```

Validation errors should include field-level details. Authentication errors should use intentionally generic messages for login failures.

Example validation error:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request body is invalid.",
    "details": [
      {
        "field": "amount",
        "message": "Amount must be greater than zero."
      }
    ],
    "requestId": "req_01JZ..."
  }
}
```

Example conflict error:

```json
{
  "error": {
    "code": "VERSION_CONFLICT",
    "message": "The resource was updated by another request.",
    "details": {
      "expectedVersion": 1,
      "currentVersion": 2
    },
    "requestId": "req_01JZ..."
  }
}
```

Backend logging should include `requestId`, `userId` when authenticated, route, status code, latency, and sanitized error code. Passwords, tokens, and raw authorization headers must never be logged.
