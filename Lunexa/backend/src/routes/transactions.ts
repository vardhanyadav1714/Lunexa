import { Hono } from "hono";
import { z } from "zod";
import { requireAuth } from "../auth/middleware";
import { first, getSql, type Sql } from "../db";
import type { Env, AppVariables } from "../env";
import { ApiError } from "../errors";
import { mapTransaction } from "../mappers";
import { list, ok } from "../response";
import { parseJson, parseQuery } from "../validation";

export const transactionRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

const uuidSchema = z.string().uuid();
const moneySchema = z.string().regex(/^\d{1,12}(\.\d{1,2})?$/);
const dateSchema = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);
const cursorSchema = z.string().regex(/^\d{4}-\d{2}-\d{2}_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);

const createTransactionSchema = z
  .object({
    clientMutationId: z.string().trim().min(1).max(120).optional(),
    accountId: uuidSchema,
    transferAccountId: uuidSchema.optional(),
    categoryId: uuidSchema.optional(),
    type: z.enum(["INCOME", "EXPENSE", "TRANSFER"]),
    amount: moneySchema,
    currency: z.string().trim().length(3).transform((value) => value.toUpperCase()).default("INR"),
    note: z.string().trim().max(500).nullable().optional(),
    merchant: z.string().trim().max(120).nullable().optional(),
    transactionDate: dateSchema,
    postedAt: z.string().datetime().nullable().optional(),
    metadata: z.record(z.string(), z.unknown()).default({}),
  })
  .superRefine((value, ctx) => {
    if (value.type === "TRANSFER") {
      if (!value.transferAccountId) {
        ctx.addIssue({ code: "custom", path: ["transferAccountId"], message: "transferAccountId is required." });
      }
      if (value.transferAccountId === value.accountId) {
        ctx.addIssue({ code: "custom", path: ["transferAccountId"], message: "Transfer accounts must be different." });
      }
      if (value.categoryId) {
        ctx.addIssue({ code: "custom", path: ["categoryId"], message: "Transfers cannot have a category." });
      }
    } else if (!value.categoryId) {
      ctx.addIssue({ code: "custom", path: ["categoryId"], message: "categoryId is required." });
    }
  });

const updateTransactionSchema = z.object({
  expectedVersion: z.number().int().positive().optional(),
  categoryId: uuidSchema.nullable().optional(),
  amount: moneySchema.optional(),
  note: z.string().trim().max(500).nullable().optional(),
  merchant: z.string().trim().max(120).nullable().optional(),
  transactionDate: dateSchema.optional(),
  postedAt: z.string().datetime().nullable().optional(),
  metadata: z.record(z.string(), z.unknown()).optional(),
});

const listTransactionsQuerySchema = z.object({
  limit: z.coerce.number().int().min(1).max(100).default(50),
  cursor: cursorSchema.optional(),
  accountId: uuidSchema.optional(),
  categoryId: uuidSchema.optional(),
  type: z.enum(["INCOME", "EXPENSE", "TRANSFER"]).optional(),
  from: dateSchema.optional(),
  to: dateSchema.optional(),
});

async function ensureAccount(sql: Sql, userId: string, accountId: string, code = "ACCOUNT_NOT_FOUND") {
  const rows = await sql`
    SELECT id
    FROM accounts
    WHERE id = ${accountId}
      AND user_id = ${userId}
      AND is_archived = false
      AND deleted_at IS NULL
    LIMIT 1
  `;

  if (rows.length === 0) {
    throw new ApiError(404, code, "Account was not found.");
  }
}

async function ensureCategory(sql: Sql, userId: string, categoryId: string, type?: string) {
  const rows = await sql`
    SELECT id, type
    FROM categories
    WHERE id = ${categoryId}
      AND user_id = ${userId}
      AND deleted_at IS NULL
    LIMIT 1
  `;

  const category = first(rows);
  if (!category) {
    throw new ApiError(404, "CATEGORY_NOT_FOUND", "Category was not found.");
  }

  if (type && String(category.type) !== type) {
    throw new ApiError(400, "CATEGORY_TYPE_MISMATCH", "Category type does not match transaction type.");
  }
}

function signedDelta(type: string, amount: number): number {
  if (type === "INCOME") return amount;
  return -amount;
}

transactionRoutes.use("*", requireAuth);

transactionRoutes.get("/", async (c) => {
  const user = c.get("user");
  const sql = getSql(c.env);
  const queryParams = parseQuery(
    {
      limit: c.req.query("limit"),
      cursor: c.req.query("cursor"),
      accountId: c.req.query("accountId"),
      categoryId: c.req.query("categoryId"),
      type: c.req.query("type"),
      from: c.req.query("from"),
      to: c.req.query("to"),
    },
    listTransactionsQuerySchema,
  );
  const limit = queryParams.limit;
  const params: unknown[] = [user.id];
  let query = `
    SELECT id, account_id, transfer_account_id, category_id, type, amount, currency, note, merchant,
           transaction_date, posted_at, metadata, created_at, updated_at, version
    FROM transactions
    WHERE user_id = $1
      AND deleted_at IS NULL
  `;

  const filters = [
    ["accountId", "account_id"],
    ["categoryId", "category_id"],
    ["type", "type"],
  ] as const;

  for (const [queryName, columnName] of filters) {
    const value = queryParams[queryName];
    if (value) {
      params.push(value);
      query += ` AND ${columnName} = $${params.length}`;
    }
  }

  const from = queryParams.from;
  if (from) {
    params.push(from);
    query += ` AND transaction_date >= $${params.length}`;
  }

  const to = queryParams.to;
  if (to) {
    params.push(to);
    query += ` AND transaction_date <= $${params.length}`;
  }

  const cursor = queryParams.cursor;
  if (cursor) {
    const [cursorDate, cursorId] = cursor.split("_");
    if (cursorDate && cursorId) {
      params.push(cursorDate, cursorId);
      query += ` AND (transaction_date, id) < ($${params.length - 1}::date, $${params.length}::uuid)`;
    }
  }

  params.push(limit + 1);
  query += ` ORDER BY transaction_date DESC, id DESC LIMIT $${params.length}`;

  const rows = await sql.query(query, params);
  const hasMore = rows.length > limit;
  const visibleRows = hasMore ? rows.slice(0, limit) : rows;
  const lastRow = visibleRows[visibleRows.length - 1];
  const nextCursor = hasMore && lastRow ? `${String(lastRow.transaction_date).slice(0, 10)}_${String(lastRow.id)}` : null;

  return list(c, visibleRows.map(mapTransaction), {
    limit,
    cursor: cursor ?? null,
    nextCursor,
    hasMore,
  });
});

transactionRoutes.post("/", async (c) => {
  const body = await parseJson(c, createTransactionSchema);
  const user = c.get("user");
  const sql = getSql(c.env);

  await ensureAccount(sql, user.id, body.accountId);
  if (body.transferAccountId) {
    await ensureAccount(sql, user.id, body.transferAccountId, "TRANSFER_ACCOUNT_NOT_FOUND");
  }
  if (body.categoryId) {
    await ensureCategory(sql, user.id, body.categoryId, body.type);
  }

  if (body.clientMutationId) {
    const duplicates = await sql`
      SELECT id, account_id, transfer_account_id, category_id, type, amount, currency, note, merchant,
             transaction_date, posted_at, metadata, created_at, updated_at, version
      FROM transactions
      WHERE user_id = ${user.id}
        AND metadata->>'clientMutationId' = ${body.clientMutationId}
        AND deleted_at IS NULL
      LIMIT 1
    `;

    const duplicate = first(duplicates);
    if (duplicate) {
      return ok(c, { transaction: mapTransaction(duplicate) });
    }
  }

  const metadata = {
    ...body.metadata,
    ...(body.clientMutationId ? { clientMutationId: body.clientMutationId } : {}),
  };
  const amount = Number(body.amount);
  const insertQuery = sql`
    INSERT INTO transactions (
      user_id,
      account_id,
      transfer_account_id,
      category_id,
      type,
      amount,
      currency,
      note,
      merchant,
      transaction_date,
      posted_at,
      metadata,
      created_by,
      updated_by
    )
    VALUES (
      ${user.id},
      ${body.accountId},
      ${body.transferAccountId ?? null},
      ${body.categoryId ?? null},
      ${body.type},
      ${body.amount},
      ${body.currency},
      ${body.note ?? null},
      ${body.merchant ?? null},
      ${body.transactionDate},
      ${body.postedAt ?? null},
      ${JSON.stringify(metadata)}::jsonb,
      ${user.id},
      ${user.id}
    )
    RETURNING id, account_id, transfer_account_id, category_id, type, amount, currency, note, merchant,
              transaction_date, posted_at, metadata, created_at, updated_at, version
  `;

  const balanceQueries = [
    sql`
      UPDATE accounts
      SET current_balance = current_balance + ${signedDelta(body.type, amount)}
      WHERE id = ${body.accountId}
        AND user_id = ${user.id}
    `,
  ];

  if (body.type === "TRANSFER" && body.transferAccountId) {
    balanceQueries.push(sql`
      UPDATE accounts
      SET current_balance = current_balance + ${amount}
      WHERE id = ${body.transferAccountId}
        AND user_id = ${user.id}
    `);
  }

  const results = await sql.transaction([insertQuery, ...balanceQueries]);
  const transaction = first(results[0]);

  if (!transaction) {
    throw new ApiError(500, "TRANSACTION_CREATE_FAILED", "Unable to create transaction.");
  }

  return ok(c, { transaction: mapTransaction(transaction) }, 201);
});

transactionRoutes.patch("/:transactionId", async (c) => {
  const body = await parseJson(c, updateTransactionSchema);
  const transactionId = c.req.param("transactionId");
  const user = c.get("user");
  const sql = getSql(c.env);

  const rows = await sql`
    SELECT id, account_id, transfer_account_id, category_id, type, amount, currency, note, merchant,
           transaction_date, posted_at, metadata, created_at, updated_at, version
    FROM transactions
    WHERE id = ${transactionId}
      AND user_id = ${user.id}
      AND deleted_at IS NULL
    LIMIT 1
  `;

  const existing = first(rows);
  if (!existing) {
    throw new ApiError(404, "TRANSACTION_NOT_FOUND", "Transaction was not found.");
  }

  if (body.expectedVersion && Number(existing.version) !== body.expectedVersion) {
    throw new ApiError(409, "VERSION_CONFLICT", "The transaction was updated by another request.", {
      expectedVersion: body.expectedVersion,
      currentVersion: Number(existing.version),
    });
  }

  if (body.categoryId) {
    await ensureCategory(sql, user.id, body.categoryId, String(existing.type));
  }

  if (String(existing.type) === "TRANSFER" && body.categoryId !== undefined) {
    throw new ApiError(400, "TRANSFER_CATEGORY_NOT_ALLOWED", "Transfers cannot have a category.");
  }

  const currentAmount = Number(existing.amount);
  const nextAmount = body.amount ? Number(body.amount) : currentAmount;
  const amountDelta = nextAmount - currentAmount;
  const hasCategory = Object.prototype.hasOwnProperty.call(body, "categoryId");
  const hasNote = Object.prototype.hasOwnProperty.call(body, "note");
  const hasMerchant = Object.prototype.hasOwnProperty.call(body, "merchant");
  const hasDate = Object.prototype.hasOwnProperty.call(body, "transactionDate");
  const hasPostedAt = Object.prototype.hasOwnProperty.call(body, "postedAt");
  const hasMetadata = Object.prototype.hasOwnProperty.call(body, "metadata");

  const updateQuery = sql`
    UPDATE transactions
    SET
      category_id = CASE WHEN ${hasCategory} THEN ${body.categoryId ?? null}::uuid ELSE category_id END,
      amount = ${nextAmount.toFixed(2)},
      note = CASE WHEN ${hasNote} THEN ${body.note ?? null} ELSE note END,
      merchant = CASE WHEN ${hasMerchant} THEN ${body.merchant ?? null} ELSE merchant END,
      transaction_date = CASE WHEN ${hasDate} THEN ${body.transactionDate ?? null}::date ELSE transaction_date END,
      posted_at = CASE WHEN ${hasPostedAt} THEN ${body.postedAt ?? null}::timestamptz ELSE posted_at END,
      metadata = CASE WHEN ${hasMetadata} THEN ${JSON.stringify(body.metadata ?? {})}::jsonb ELSE metadata END,
      updated_by = ${user.id}
    WHERE id = ${transactionId}
      AND user_id = ${user.id}
      AND deleted_at IS NULL
    RETURNING id, account_id, transfer_account_id, category_id, type, amount, currency, note, merchant,
              transaction_date, posted_at, metadata, created_at, updated_at, version
  `;

  const balanceQueries = [];
  if (amountDelta !== 0) {
    balanceQueries.push(sql`
      UPDATE accounts
      SET current_balance = current_balance + ${signedDelta(String(existing.type), amountDelta)}
      WHERE id = ${String(existing.account_id)}
        AND user_id = ${user.id}
    `);

    if (String(existing.type) === "TRANSFER" && existing.transfer_account_id) {
      balanceQueries.push(sql`
        UPDATE accounts
        SET current_balance = current_balance + ${amountDelta}
        WHERE id = ${String(existing.transfer_account_id)}
          AND user_id = ${user.id}
      `);
    }
  }

  const results = await sql.transaction([updateQuery, ...balanceQueries]);
  const transaction = first(results[0]);

  if (!transaction) {
    throw new ApiError(500, "TRANSACTION_UPDATE_FAILED", "Unable to update transaction.");
  }

  return ok(c, { transaction: mapTransaction(transaction) });
});

transactionRoutes.delete("/:transactionId", async (c) => {
  const transactionId = c.req.param("transactionId");
  const expectedVersion = c.req.query("expectedVersion");
  const user = c.get("user");
  const sql = getSql(c.env);

  const rows = await sql`
    SELECT id, account_id, transfer_account_id, type, amount, version
    FROM transactions
    WHERE id = ${transactionId}
      AND user_id = ${user.id}
      AND deleted_at IS NULL
    LIMIT 1
  `;

  const existing = first(rows);
  if (!existing) {
    throw new ApiError(404, "TRANSACTION_NOT_FOUND", "Transaction was not found.");
  }

  if (expectedVersion && Number(existing.version) !== Number(expectedVersion)) {
    throw new ApiError(409, "VERSION_CONFLICT", "The transaction was updated by another request.", {
      expectedVersion: Number(expectedVersion),
      currentVersion: Number(existing.version),
    });
  }

  const amount = Number(existing.amount);
  const deleteQuery = sql`
    UPDATE transactions
    SET deleted_at = now(),
        deleted_by = ${user.id},
        updated_by = ${user.id}
    WHERE id = ${transactionId}
      AND user_id = ${user.id}
      AND deleted_at IS NULL
  `;
  const balanceQueries = [
    sql`
      UPDATE accounts
      SET current_balance = current_balance - ${signedDelta(String(existing.type), amount)}
      WHERE id = ${String(existing.account_id)}
        AND user_id = ${user.id}
    `,
  ];

  if (String(existing.type) === "TRANSFER" && existing.transfer_account_id) {
    balanceQueries.push(sql`
      UPDATE accounts
      SET current_balance = current_balance - ${amount}
      WHERE id = ${String(existing.transfer_account_id)}
        AND user_id = ${user.id}
    `);
  }

  await sql.transaction([deleteQuery, ...balanceQueries]);
  return c.body(null, 204);
});
