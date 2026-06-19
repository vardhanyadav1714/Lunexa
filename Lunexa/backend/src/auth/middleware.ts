import { createMiddleware } from "hono/factory";
import type { Env, AppVariables } from "../env";
import { ApiError } from "../errors";
import { verifyToken } from "./jwt";

export const requireAuth = createMiddleware<{ Bindings: Env; Variables: AppVariables }>(async (c, next) => {
  const authorization = c.req.header("Authorization");
  const token = authorization?.startsWith("Bearer ") ? authorization.slice("Bearer ".length) : null;

  if (!token) {
    throw new ApiError(401, "UNAUTHORIZED", "Authentication is required.");
  }

  const user = await verifyToken(c.env, token, "access");
  c.set("user", user);

  await next();
});
