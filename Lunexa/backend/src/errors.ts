import type { Context } from "hono";
import type { ContentfulStatusCode } from "hono/utils/http-status";
import type { Env, AppVariables } from "./env";

export class ApiError extends Error {
  readonly status: ContentfulStatusCode;
  readonly code: string;
  readonly details?: unknown;

  constructor(status: ContentfulStatusCode, code: string, message: string, details?: unknown) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

export function toErrorResponse(
  c: Context<{ Bindings: Env; Variables: AppVariables }>,
  error: unknown,
) {
  const requestId = c.get("requestId") ?? crypto.randomUUID();

  if (error instanceof ApiError) {
    return c.json(
      {
        error: {
          code: error.code,
          message: error.message,
          details: error.details,
          requestId,
        },
      },
      error.status,
    );
  }

  console.error("Unhandled API error", {
    requestId,
    error:
      error instanceof Error
        ? {
            name: error.name,
            message: error.message,
            stack: error.stack,
          }
        : error,
  });

  return c.json(
    {
      error: {
        code: "INTERNAL_SERVER_ERROR",
        message: "Something went wrong.",
        requestId,
      },
    },
    500,
  );
}

export function validationError(details: unknown) {
  return new ApiError(400, "VALIDATION_ERROR", "Request body is invalid.", details);
}
