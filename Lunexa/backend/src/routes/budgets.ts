import { Hono } from "hono";
import { z } from "zod";
import { requireAuth } from "../auth/middleware";
import { first, getSql } from "../db";
import type { Env, AppVariables } from "../env";
import { ApiError } from "../errors";
import { mapBudget } from "../mappers";
import { list, ok } from "../response";
import { parseJson } from "../validation";

export const budgetRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

const uuidSchema = z.string().uuid();
const moneySchema = z.string().regex(/^\d{1,12}(\.\d{1,2})?$/);
const monthSchema = z.string().regex(/^\d{4}-\d{2}-01$/);

const createBudgetSchema = z.object({
  categoryId: uuidSchema,
  periodMonth: monthSchema,
  amount: moneySchema,
  alertThresholdPercent: moneySchema.default("80.00"),
});

const updateBudgetSchema = z.object({
  expectedVersion: z.number().int().positive().optional(),
  amount: moneySchema.optional(),
  alertThresholdPercent: moneySchema.optional(),
});

async function ensureExpenseCategory(sql: ReturnType<typeof getSql>, userId: string, categoryId: string) {
  const rows = await sql`
    SELECT id
    FROM categories
    WHERE id = ${categoryId}
      AND user_id = ${userId}
      AND type = 'EXPENSE'
      AND deleted_at IS NULL
    LIMIT 1
  `;

  if (rows.length === 0) {
    throw new ApiError(404, "CATEGORY_NOT_FOUND", "Expense category was not found.");
  }
}

budgetRoutes.use("*", requireAuth);

budgetRoutes.get("/", async (c) => {
  const user = c.get("user");
  const sql = getSql(c.env);
  const params: unknown[] = [user.id];
  let query = `
    SELECT id, category_id, period_month, amount, alert_threshold_percent, created_at, updated_at, version
    FROM budgets
    WHERE user_id = $1
      AND deleted_at IS NULL
  `;

  const periodMonth = c.req.query("periodMonth");
  if (periodMonth) {
    params.push(periodMonth);
    query += ` AND period_month = $${params.length}`;
  }

  const fromMonth = c.req.query("fromMonth");
  if (fromMonth) {
    params.push(fromMonth);
    query += ` AND period_month >= $${params.length}`;
  }

  const toMonth = c.req.query("toMonth");
  if (toMonth) {
    params.push(toMonth);
    query += ` AND period_month <= $${params.length}`;
  }

  const categoryId = c.req.query("categoryId");
  if (categoryId) {
    params.push(categoryId);
    query += ` AND category_id = $${params.length}`;
  }

  query += " ORDER BY period_month DESC, created_at DESC";

  const rows = await sql.query(query, params);
  return list(c, rows.map(mapBudget));
});

budgetRoutes.get("/:budgetId", async (c) => {
  const user = c.get("user");
  const sql = getSql(c.env);

  const rows = await sql`
    SELECT id, category_id, period_month, amount, alert_threshold_percent, created_at, updated_at, version
    FROM budgets
    WHERE id = ${c.req.param("budgetId")}
      AND user_id = ${user.id}
      AND deleted_at IS NULL
    LIMIT 1
  `;

  const budget = first(rows);
  if (!budget) {
    throw new ApiError(404, "BUDGET_NOT_FOUND", "Budget was not found.");
  }

  return ok(c, { budget: mapBudget(budget) });
});

budgetRoutes.post("/", async (c) => {
  const body = await parseJson(c, createBudgetSchema);
  const user = c.get("user");
  const sql = getSql(c.env);

  await ensureExpenseCategory(sql, user.id, body.categoryId);

  try {
    const rows = await sql`
      INSERT INTO budgets (
        user_id,
        category_id,
        period_month,
        amount,
        alert_threshold_percent,
        created_by,
        updated_by
      )
      VALUES (
        ${user.id},
        ${body.categoryId},
        ${body.periodMonth},
        ${body.amount},
        ${body.alertThresholdPercent},
        ${user.id},
        ${user.id}
      )
      RETURNING id, category_id, period_month, amount, alert_threshold_percent, created_at, updated_at, version
    `;

    const budget = first(rows);
    if (!budget) {
      throw new ApiError(500, "BUDGET_CREATE_FAILED", "Unable to create budget.");
    }

    return ok(c, { budget: mapBudget(budget) }, 201);
  } catch (error) {
    if (error instanceof ApiError) throw error;

    const message = error instanceof Error ? error.message : "";
    if (message.includes("ux_budgets_user_category_month_active")) {
      throw new ApiError(409, "BUDGET_ALREADY_EXISTS", "Budget already exists for this category and month.");
    }

    throw error;
  }
});

budgetRoutes.patch("/:budgetId", async (c) => {
  const body = await parseJson(c, updateBudgetSchema);
  const user = c.get("user");
  const sql = getSql(c.env);
  const budgetId = c.req.param("budgetId");

  const existingRows = await sql`
    SELECT id, version
    FROM budgets
    WHERE id = ${budgetId}
      AND user_id = ${user.id}
      AND deleted_at IS NULL
    LIMIT 1
  `;

  const existing = first(existingRows);
  if (!existing) {
    throw new ApiError(404, "BUDGET_NOT_FOUND", "Budget was not found.");
  }

  if (body.expectedVersion && Number(existing.version) !== body.expectedVersion) {
    throw new ApiError(409, "VERSION_CONFLICT", "The budget was updated by another request.", {
      expectedVersion: body.expectedVersion,
      currentVersion: Number(existing.version),
    });
  }

  const hasAmount = Object.prototype.hasOwnProperty.call(body, "amount");
  const hasThreshold = Object.prototype.hasOwnProperty.call(body, "alertThresholdPercent");

  const rows = await sql`
    UPDATE budgets
    SET
      amount = CASE WHEN ${hasAmount} THEN ${body.amount ?? null}::numeric ELSE amount END,
      alert_threshold_percent = CASE
        WHEN ${hasThreshold} THEN ${body.alertThresholdPercent ?? null}::numeric
        ELSE alert_threshold_percent
      END,
      updated_by = ${user.id}
    WHERE id = ${budgetId}
      AND user_id = ${user.id}
      AND deleted_at IS NULL
    RETURNING id, category_id, period_month, amount, alert_threshold_percent, created_at, updated_at, version
  `;

  const budget = first(rows);
  if (!budget) {
    throw new ApiError(500, "BUDGET_UPDATE_FAILED", "Unable to update budget.");
  }

  return ok(c, { budget: mapBudget(budget) });
});

budgetRoutes.delete("/:budgetId", async (c) => {
  const user = c.get("user");
  const sql = getSql(c.env);
  const budgetId = c.req.param("budgetId");
  const expectedVersion = c.req.query("expectedVersion");

  const existingRows = await sql`
    SELECT id, version
    FROM budgets
    WHERE id = ${budgetId}
      AND user_id = ${user.id}
      AND deleted_at IS NULL
    LIMIT 1
  `;

  const existing = first(existingRows);
  if (!existing) {
    throw new ApiError(404, "BUDGET_NOT_FOUND", "Budget was not found.");
  }

  if (expectedVersion && Number(existing.version) !== Number(expectedVersion)) {
    throw new ApiError(409, "VERSION_CONFLICT", "The budget was updated by another request.", {
      expectedVersion: Number(expectedVersion),
      currentVersion: Number(existing.version),
    });
  }

  await sql`
    UPDATE budgets
    SET deleted_at = now(),
        deleted_by = ${user.id},
        updated_by = ${user.id}
    WHERE id = ${budgetId}
      AND user_id = ${user.id}
      AND deleted_at IS NULL
  `;

  return c.body(null, 204);
});
