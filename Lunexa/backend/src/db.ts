import { neon } from "@neondatabase/serverless";
import type { Env } from "./env";
import { ApiError } from "./errors";

export type DbRow = Record<string, any>;
export type SqlQuery = Promise<DbRow[]>;
export type Sql = ((strings: TemplateStringsArray, ...params: unknown[]) => SqlQuery) & {
  query: (query: string, params?: unknown[]) => SqlQuery;
  transaction: (queries: SqlQuery[]) => Promise<DbRow[][]>;
};

export function getSql(env: Env): Sql {
  if (!env.DATABASE_URL) {
    throw new ApiError(500, "DATABASE_NOT_CONFIGURED", "Database connection is not configured.");
  }

  return neon(env.DATABASE_URL) as unknown as Sql;
}

export function first<T extends DbRow>(rows: T[]): T | null {
  return rows.length > 0 ? rows[0] : null;
}

export function toMoney(value: unknown): string {
  if (value === null || value === undefined) return "0.00";
  return Number(value).toFixed(2);
}

export function toIso(value: unknown): string | null {
  if (!value) return null;
  return new Date(value as string).toISOString();
}
