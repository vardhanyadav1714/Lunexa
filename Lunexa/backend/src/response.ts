import type { Context } from "hono";
import type { ContentfulStatusCode } from "hono/utils/http-status";
import type { Env, AppVariables } from "./env";

export function ok(
  c: Context<{ Bindings: Env; Variables: AppVariables }>,
  data: unknown,
  status: ContentfulStatusCode = 200,
) {
  return c.json(
    {
      data,
      meta: {
        requestId: c.get("requestId"),
      },
    },
    status,
  );
}

export function list(
  c: Context<{ Bindings: Env; Variables: AppVariables }>,
  data: unknown[],
  pagination?: {
    limit: number;
    cursor: string | null;
    nextCursor: string | null;
    hasMore: boolean;
  },
) {
  return c.json({
    data,
    meta: {
      requestId: c.get("requestId"),
      ...(pagination ? { pagination } : {}),
    },
  });
}
