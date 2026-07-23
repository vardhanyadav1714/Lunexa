import { Hono } from "hono";
import { z } from "zod";
import { requireAuth } from "../auth/middleware";
import { first, getSql, toMoney } from "../db";
import type { Env, AppVariables } from "../env";
import { ok } from "../response";
import { parseQuery } from "../validation";

export const analyticsRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

const uuidSchema = z.string().uuid();
const monthSchema = z.string().regex(/^\d{4}-(0[1-9]|1[0-2])$/);

const monthlySummaryQuerySchema = z.object({
  month: monthSchema,
  accountId: uuidSchema.optional(),
});

const categorySummaryQuerySchema = monthlySummaryQuerySchema.extend({
  type: z.enum(["INCOME", "EXPENSE"]).default("EXPENSE"),
});

analyticsRoutes.use("*", requireAuth);

analyticsRoutes.get("/monthly-summary", async (c) => {
  const user = c.get("user");
  const query = parseQuery(
    {
      month: c.req.query("month"),
      accountId: c.req.query("accountId"),
    },
    monthlySummaryQuerySchema,
  );
  const sql = getSql(c.env);
  const monthStart = `${query.month}-01`;

  const rows = await sql`
    WITH tx AS (
      SELECT type, amount, id, category_id, transaction_date
      FROM transactions
      WHERE user_id = ${user.id}
        AND deleted_at IS NULL
        AND transaction_date >= ${monthStart}::date
        AND transaction_date < (${monthStart}::date + interval '1 month')
        AND (${query.accountId ?? null}::uuid IS NULL OR account_id = ${query.accountId ?? null}::uuid)
    ),
    budget_totals AS (
      SELECT
        COALESCE(SUM(b.amount), 0) AS budgeted_amount
      FROM budgets b
      WHERE b.user_id = ${user.id}
        AND b.deleted_at IS NULL
        AND b.period_month = ${monthStart}::date
    ),
    largest_expense AS (
      SELECT id, amount, category_id, transaction_date
      FROM tx
      WHERE type = 'EXPENSE'
      ORDER BY amount DESC
      LIMIT 1
    )
    SELECT
      COALESCE(SUM(amount) FILTER (WHERE type = 'INCOME'), 0) AS income_total,
      COALESCE(SUM(amount) FILTER (WHERE type = 'EXPENSE'), 0) AS expense_total,
      COALESCE(SUM(amount) FILTER (WHERE type = 'TRANSFER'), 0) AS transfer_total,
      COUNT(*)::int AS transaction_count,
      (SELECT budgeted_amount FROM budget_totals) AS budgeted_amount,
      (SELECT id FROM largest_expense) AS largest_expense_id,
      (SELECT amount FROM largest_expense) AS largest_expense_amount,
      (SELECT category_id FROM largest_expense) AS largest_expense_category_id,
      (SELECT transaction_date FROM largest_expense) AS largest_expense_date
    FROM tx
  `;

  const summary = first(rows) ?? {};
  const incomeTotal = Number(summary.income_total ?? 0);
  const expenseTotal = Number(summary.expense_total ?? 0);
  const budgetedAmount = Number(summary.budgeted_amount ?? 0);
  const remainingAmount = budgetedAmount - expenseTotal;
  const utilization = budgetedAmount > 0 ? (expenseTotal / budgetedAmount) * 100 : 0;

  return ok(c, {
    month: query.month,
    currency: "INR",
    incomeTotal: toMoney(incomeTotal),
    expenseTotal: toMoney(expenseTotal),
    transferTotal: toMoney(summary.transfer_total),
    netCashflow: toMoney(incomeTotal - expenseTotal),
    transactionCount: Number(summary.transaction_count ?? 0),
    largestExpense: summary.largest_expense_id
      ? {
          transactionId: String(summary.largest_expense_id),
          amount: toMoney(summary.largest_expense_amount),
          categoryId: String(summary.largest_expense_category_id),
          transactionDate: String(summary.largest_expense_date).slice(0, 10),
        }
      : null,
    budgetSummary: {
      budgetedAmount: toMoney(budgetedAmount),
      spentAmount: toMoney(expenseTotal),
      remainingAmount: toMoney(remainingAmount),
      utilizationPercent: toMoney(utilization),
    },
  });
});

analyticsRoutes.get("/category-summary", async (c) => {
  const user = c.get("user");
  const query = parseQuery(
    {
      month: c.req.query("month"),
      accountId: c.req.query("accountId"),
      type: c.req.query("type"),
    },
    categorySummaryQuerySchema,
  );
  const sql = getSql(c.env);
  const monthStart = `${query.month}-01`;

  const rows = await sql`
    WITH category_totals AS (
      SELECT
        c.id AS category_id,
        c.name AS category_name,
        c.icon_key,
        c.color_hex,
        COALESCE(SUM(t.amount), 0) AS total_amount,
        COUNT(t.id)::int AS transaction_count,
        COALESCE(MAX(b.amount), 0) AS budget_amount
      FROM categories c
      LEFT JOIN transactions t
        ON t.category_id = c.id
       AND t.user_id = c.user_id
       AND t.deleted_at IS NULL
       AND t.transaction_date >= ${monthStart}::date
       AND t.transaction_date < (${monthStart}::date + interval '1 month')
       AND (${query.accountId ?? null}::uuid IS NULL OR t.account_id = ${query.accountId ?? null}::uuid)
      LEFT JOIN budgets b
        ON b.category_id = c.id
       AND b.user_id = c.user_id
       AND b.deleted_at IS NULL
       AND b.period_month = ${monthStart}::date
      WHERE c.user_id = ${user.id}
        AND c.deleted_at IS NULL
        AND c.type = ${query.type}
      GROUP BY c.id, c.name, c.icon_key, c.color_hex
    ),
    grand_total AS (
      SELECT COALESCE(SUM(total_amount), 0) AS value
      FROM category_totals
    )
    SELECT
      category_id,
      category_name,
      icon_key,
      color_hex,
      total_amount,
      transaction_count,
      budget_amount,
      CASE
        WHEN (SELECT value FROM grand_total) > 0
        THEN (total_amount / (SELECT value FROM grand_total)) * 100
        ELSE 0
      END AS percentage
    FROM category_totals
    WHERE total_amount > 0 OR budget_amount > 0
    ORDER BY total_amount DESC, category_name ASC
  `;

  return ok(c, {
    month: query.month,
    currency: "INR",
    type: query.type,
    categories: rows.map((row) => {
      const totalAmount = Number(row.total_amount ?? 0);
      const budgetAmount = Number(row.budget_amount ?? 0);

      return {
        categoryId: String(row.category_id),
        categoryName: String(row.category_name),
        iconKey: row.icon_key ? String(row.icon_key) : null,
        colorHex: row.color_hex ? String(row.color_hex) : null,
        totalAmount: toMoney(totalAmount),
        transactionCount: Number(row.transaction_count ?? 0),
        percentage: toMoney(row.percentage),
        budgetAmount: toMoney(budgetAmount),
        budgetUtilizationPercent: budgetAmount > 0 ? toMoney((totalAmount / budgetAmount) * 100) : "0.00",
      };
    }),
  });
});
