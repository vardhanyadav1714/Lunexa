import { Hono } from "hono";
import { cors } from "hono/cors";
import type { Env, AppVariables } from "./env";
import { ApiError, toErrorResponse } from "./errors";
import { authRoutes } from "./routes/auth";
import { accountRoutes } from "./routes/accounts";
import { analyticsRoutes } from "./routes/analytics";
import { budgetRoutes } from "./routes/budgets";
import { categoryRoutes } from "./routes/categories";
import { transactionRoutes } from "./routes/transactions";

const app = new Hono<{ Bindings: Env; Variables: AppVariables }>();

app.use("*", async (c, next) => {
  c.set("requestId", crypto.randomUUID());
  await next();
  c.header("X-Request-Id", c.get("requestId"));
});

app.use(
  "*",
  cors({
    origin: (origin, c) => c.env.ALLOWED_ORIGIN || origin || "*",
    allowHeaders: ["Authorization", "Content-Type", "X-Request-Id"],
    allowMethods: ["GET", "POST", "PATCH", "DELETE", "OPTIONS"],
    exposeHeaders: ["X-Request-Id"],
    maxAge: 86_400,
  }),
);

app.get("/", (c) =>
  c.json({
    name: "Lunexa API",
    status: "ok",
    version: "0.1.0",
  }),
);

app.get("/health", (c) =>
  c.json({
    status: "ok",
    requestId: c.get("requestId"),
  }),
);

app.route("/api/v1/auth", authRoutes);
app.route("/api/v1/accounts", accountRoutes);
app.route("/api/v1/categories", categoryRoutes);
app.route("/api/v1/transactions", transactionRoutes);
app.route("/api/v1/budgets", budgetRoutes);
app.route("/api/v1/analytics", analyticsRoutes);

app.notFound((c) => {
  throw new ApiError(404, "RESOURCE_NOT_FOUND", "The requested endpoint was not found.");
});

app.onError((error, c) => toErrorResponse(c, error));

export default app;
