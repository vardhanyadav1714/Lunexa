import { Hono } from "hono";
import { z } from "zod";
import { requireAuth } from "../auth/middleware";
import { first, getSql } from "../db";
import type { Env, AppVariables } from "../env";
import { ApiError } from "../errors";
import { mapCategory } from "../mappers";
import { list, ok } from "../response";
import { parseJson } from "../validation";

export const categoryRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

const createCategorySchema = z.object({
  name: z.string().trim().min(1).max(80),
  type: z.enum(["INCOME", "EXPENSE"]),
  iconKey: z.string().trim().max(60).optional(),
  colorHex: z.string().regex(/^#[0-9A-Fa-f]{6}$/).optional(),
  sortOrder: z.number().int().min(0).max(10_000).default(0),
});

categoryRoutes.use("*", requireAuth);

categoryRoutes.get("/", async (c) => {
  const type = c.req.query("type");
  const sql = getSql(c.env);
  const user = c.get("user");
  const params: unknown[] = [user.id];
  let query = `
    SELECT id, name, type, icon_key, color_hex, is_default, sort_order, created_at, updated_at, version
    FROM categories
    WHERE user_id = $1
      AND deleted_at IS NULL
  `;

  if (type) {
    params.push(type);
    query += ` AND type = $${params.length}`;
  }

  query += " ORDER BY type ASC, sort_order ASC, name ASC";

  const rows = await sql.query(query, params);
  return list(c, rows.map(mapCategory));
});

categoryRoutes.post("/", async (c) => {
  const body = await parseJson(c, createCategorySchema);
  const sql = getSql(c.env);
  const user = c.get("user");

  try {
    const rows = await sql`
      INSERT INTO categories (
        user_id,
        name,
        type,
        icon_key,
        color_hex,
        is_default,
        sort_order,
        created_by,
        updated_by
      )
      VALUES (
        ${user.id},
        ${body.name},
        ${body.type},
        ${body.iconKey ?? null},
        ${body.colorHex ?? null},
        false,
        ${body.sortOrder},
        ${user.id},
        ${user.id}
      )
      RETURNING id, name, type, icon_key, color_hex, is_default, sort_order, created_at, updated_at, version
    `;

    const category = first(rows);
    if (!category) {
      throw new ApiError(500, "CATEGORY_CREATE_FAILED", "Unable to create category.");
    }

    return ok(c, { category: mapCategory(category) }, 201);
  } catch (error) {
    if (error instanceof ApiError) throw error;

    const message = error instanceof Error ? error.message : "";
    if (message.includes("ux_categories_user_type_name_active")) {
      throw new ApiError(409, "CATEGORY_ALREADY_EXISTS", "An active category with this name already exists.");
    }

    throw error;
  }
});
