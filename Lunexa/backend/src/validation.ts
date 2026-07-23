import type { Context } from "hono";
import { ZodError, type ZodSchema } from "zod";
import type { Env, AppVariables } from "./env";
import { validationError } from "./errors";

export async function parseJson<T>(
  c: Context<{ Bindings: Env; Variables: AppVariables }>,
  schema: ZodSchema<T>,
): Promise<T> {
  let body: unknown;

  try {
    body = await c.req.json();
  } catch {
    throw validationError([{ field: "body", message: "Body must be valid JSON." }]);
  }

  try {
    return schema.parse(body);
  } catch (error) {
    if (error instanceof ZodError) {
      throw validationError(
        error.issues.map((issue) => ({
          field: issue.path.join("."),
          message: issue.message,
        })),
      );
    }

    throw error;
  }
}

export function parseQuery<T>(query: unknown, schema: ZodSchema<T>): T {
  try {
    return schema.parse(query);
  } catch (error) {
    if (error instanceof ZodError) {
      throw validationError(
        error.issues.map((issue) => ({
          field: issue.path.join("."),
          message: issue.message,
        })),
      );
    }

    throw error;
  }
}

export function requiredQuery(value: string | undefined, field: string): string {
  if (!value) {
    throw validationError([{ field, message: `${field} is required.` }]);
  }

  return value;
}
