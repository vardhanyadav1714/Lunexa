import { Hono } from "hono";
import { z } from "zod";
import { requireAuth } from "../auth/middleware";
import { first, getSql } from "../db";
import type { Env, AppVariables } from "../env";
import { ApiError } from "../errors";
import { mapAccount } from "../mappers";
import { list, ok } from "../response";
import { parseJson } from "../validation";

export const accountRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

const createAccountSchema = z.object({
  name: z.string().trim().min(1).max(80),
  type: z.enum(["CASH", "BANK", "WALLET", "CREDIT_CARD", "INVESTMENT", "OTHER"]),
  currency: z.string().trim().length(3).toUpperCase().default("INR"),
  openingBalance: z.string().regex(/^-?\d{1,12}(\.\d{1,2})?$/).default("0.00"),
  sortOrder: z.number().int().min(0).max(10_000).default(0),
});

accountRoutes.use("*", requireAuth);

accountRoutes.get("/", async (c) => {
  const sql = getSql(c.env);
  const user = c.get("user");

  const rows = await sql`
    SELECT id, name, type, currency, opening_balance, current_balance, is_archived, sort_order, created_at, updated_at, version
    FROM accounts
    WHERE user_id = ${user.id}
      AND deleted_at IS NULL
    ORDER BY sort_order ASC, name ASC
  `;

  return list(c, rows.map(mapAccount));
});

accountRoutes.post("/", async (c) => {
  const body = await parseJson(c, createAccountSchema);
  const sql = getSql(c.env);
  const user = c.get("user");

  try {
    const rows = await sql`
      INSERT INTO accounts (
        user_id,
        name,
        type,
        currency,
        opening_balance,
        current_balance,
        sort_order,
        created_by,
        updated_by
      )
      VALUES (
        ${user.id},
        ${body.name},
        ${body.type},
        ${body.currency},
        ${body.openingBalance},
        ${body.openingBalance},
        ${body.sortOrder},
        ${user.id},
        ${user.id}
      )
      RETURNING id, name, type, currency, opening_balance, current_balance, is_archived, sort_order, created_at, updated_at, version
    `;

    const account = first(rows);
    if (!account) {
      throw new ApiError(500, "ACCOUNT_CREATE_FAILED", "Unable to create account.");
    }

    return ok(c, { account: mapAccount(account) }, 201);
  } catch (error) {
    if (error instanceof ApiError) throw error;

    const message = error instanceof Error ? error.message : "";
    if (message.includes("ux_accounts_user_name_active")) {
      throw new ApiError(409, "ACCOUNT_ALREADY_EXISTS", "An active account with this name already exists.");
    }

    throw error;
  }
});
