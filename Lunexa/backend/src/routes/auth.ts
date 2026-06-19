import { Hono } from "hono";
import { z } from "zod";
import type { Env, AppVariables, AuthUser } from "../env";
import { ApiError } from "../errors";
import { first, getSql } from "../db";
import { mapUser } from "../mappers";
import { ok } from "../response";
import { parseJson } from "../validation";
import { createTokenPair, verifyToken } from "../auth/jwt";
import { hashPassword, verifyPassword } from "../auth/password";
import { sendPasswordResetEmail, sendVerificationEmail } from "../auth/emailDelivery";
import {
  generateVerificationCode,
  hashVerificationCode,
  verifyVerificationCode,
} from "../auth/emailVerification";

export const authRoutes = new Hono<{ Bindings: Env; Variables: AppVariables }>();

const VERIFICATION_TTL_SECONDS = 10 * 60;
const MAX_VERIFICATION_ATTEMPTS = 5;
const MAX_VERIFICATION_REQUESTS_PER_WINDOW = 5;
const MAX_VERIFICATION_REQUESTS_PER_IP_WINDOW = 20;
const PASSWORD_RESET_TTL_SECONDS = 10 * 60;
const MAX_PASSWORD_RESET_ATTEMPTS = 5;
const MAX_PASSWORD_RESET_REQUESTS_PER_WINDOW = 5;
const MAX_PASSWORD_RESET_REQUESTS_PER_IP_WINDOW = 20;

const blockedEmailDomains = new Set([
  "example.com",
  "example.net",
  "example.org",
  "test.com",
  "mailinator.com",
  "yopmail.com",
  "tempmail.com",
  "temp-mail.org",
  "guerrillamail.com",
  "10minutemail.com",
  "trashmail.com",
  "sharklasers.com",
]);

const blockedNameValues = new Set([
  "admin",
  "demo",
  "fake",
  "name",
  "null",
  "qwerty",
  "test",
  "undefined",
  "user",
]);

function customIssue(ctx: z.RefinementCtx, message: string) {
  ctx.addIssue({
    code: z.ZodIssueCode.custom,
    message,
  });
}

function emailDomain(email: string): string {
  return email.slice(email.lastIndexOf("@") + 1).toLowerCase();
}

function isBlockedEmailDomain(domain: string): boolean {
  return (
    blockedEmailDomains.has(domain) ||
    domain.endsWith(".example") ||
    domain.endsWith(".invalid") ||
    domain.endsWith(".test")
  );
}

function letterCount(value: string): number {
  return Array.from(value.matchAll(/\p{L}/gu)).length;
}

const emailSchema = z.string().trim().email().max(254).toLowerCase();

const registrationEmailSchema = emailSchema.superRefine((email, ctx) => {
  const domain = emailDomain(email);
  if (isBlockedEmailDomain(domain)) {
    customIssue(ctx, "Use a real inbox that you can access.");
  }
});

const fullNameSchema = z
  .string()
  .trim()
  .min(2)
  .max(120)
  .superRefine((name, ctx) => {
    const normalized = name.toLowerCase().replace(/\s+/g, " ").trim();

    if (letterCount(name) < 2) {
      customIssue(ctx, "Full name must include at least two letters.");
    }

    if (!/^[\p{L}\p{M}][\p{L}\p{M}' .-]*$/u.test(name)) {
      customIssue(ctx, "Full name can only contain letters, spaces, apostrophes, periods, or hyphens.");
    }

    if (blockedNameValues.has(normalized) || /(.)\1{4,}/.test(normalized.replace(/\s/g, ""))) {
      customIssue(ctx, "Enter your real name.");
    }
  });

const strongPasswordSchema = z
  .string()
  .min(10)
  .max(128)
  .superRefine((password, ctx) => {
    if (!/[a-z]/.test(password)) customIssue(ctx, "Password needs a lowercase letter.");
    if (!/[A-Z]/.test(password)) customIssue(ctx, "Password needs an uppercase letter.");
    if (!/\d/.test(password)) customIssue(ctx, "Password needs a number.");
    if (!/[^A-Za-z0-9]/.test(password)) customIssue(ctx, "Password needs a symbol.");
  });

const authSchema = z.object({
  email: emailSchema,
  password: z.string().min(8).max(128),
});

const registrationDetailsSchema = z.object({
  email: registrationEmailSchema,
  password: strongPasswordSchema,
  fullName: fullNameSchema,
});

const verificationStartSchema = registrationDetailsSchema;

const registerSchema = registrationDetailsSchema.extend({
  emailVerificationId: z.string().uuid(),
  emailVerificationCode: z.string().regex(/^\d{6}$/, "Enter the 6-digit verification code."),
});

const refreshSchema = z.object({
  refreshToken: z.string().min(20),
});

const passwordResetStartSchema = z.object({
  email: emailSchema,
});

const passwordResetConfirmSchema = z.object({
  resetId: z.string().uuid(),
  email: emailSchema,
  code: z.string().regex(/^\d{6}$/, "Enter the 6-digit reset code."),
  newPassword: strongPasswordSchema,
});

const defaultCategories = [
  { name: "Salary", type: "INCOME", icon: "wallet", color: "#16A34A" },
  { name: "Food", type: "EXPENSE", icon: "restaurant", color: "#EF4444" },
  { name: "Transport", type: "EXPENSE", icon: "car", color: "#F97316" },
  { name: "Shopping", type: "EXPENSE", icon: "shopping-bag", color: "#8B5CF6" },
  { name: "Bills", type: "EXPENSE", icon: "receipt", color: "#0EA5E9" },
  { name: "Health", type: "EXPENSE", icon: "heart-pulse", color: "#EC4899" },
  { name: "Entertainment", type: "EXPENSE", icon: "film", color: "#6366F1" },
];

function defaultCategoryQueries(sql: ReturnType<typeof getSql>, userId: string) {
  return defaultCategories.map((category, index) =>
    sql`
      INSERT INTO categories (user_id, name, type, icon_key, color_hex, is_default, sort_order, created_by, updated_by)
      VALUES (
        ${userId},
        ${category.name},
        ${category.type},
        ${category.icon},
        ${category.color},
        true,
        ${index},
        ${userId},
        ${userId}
      )
    `,
  );
}

async function assertEmailCanRegister(sql: ReturnType<typeof getSql>, email: string) {
  const existing = await sql`
    SELECT id
    FROM users
    WHERE email = ${email}
      AND deleted_at IS NULL
    LIMIT 1
  `;

  if (existing.length > 0) {
    throw new ApiError(409, "EMAIL_ALREADY_EXISTS", "An account with this email already exists.");
  }
}

async function assertVerificationRequestAllowed(
  sql: ReturnType<typeof getSql>,
  email: string,
  requestIp: string | null,
) {
  const recentRows = await sql`
    SELECT count(*) AS count
    FROM email_verification_codes
    WHERE email = ${email}
      AND created_at > now() - interval '15 minutes'
  `;
  const recentCount = Number(first(recentRows)?.count ?? 0);

  if (recentCount >= MAX_VERIFICATION_REQUESTS_PER_WINDOW) {
    throw new ApiError(
      429,
      "EMAIL_VERIFICATION_RATE_LIMITED",
      "Too many verification requests. Try again later.",
    );
  }

  if (!requestIp) return;

  const recentIpRows = await sql`
    SELECT count(*) AS count
    FROM email_verification_codes
    WHERE request_ip = ${requestIp}
      AND created_at > now() - interval '15 minutes'
  `;
  const recentIpCount = Number(first(recentIpRows)?.count ?? 0);

  if (recentIpCount >= MAX_VERIFICATION_REQUESTS_PER_IP_WINDOW) {
    throw new ApiError(
      429,
      "EMAIL_VERIFICATION_RATE_LIMITED",
      "Too many verification requests. Try again later.",
    );
  }
}

async function findActiveUserByEmail(sql: ReturnType<typeof getSql>, email: string) {
  const rows = await sql`
    SELECT id, full_name, email, password_hash, status, created_at, last_login_at
    FROM users
    WHERE email = ${email}
      AND deleted_at IS NULL
    LIMIT 1
  `;

  return first(rows);
}

async function assertPasswordResetRequestAllowed(
  sql: ReturnType<typeof getSql>,
  email: string,
  requestIp: string | null,
) {
  const recentRows = await sql`
    SELECT count(*) AS count
    FROM password_reset_codes
    WHERE email = ${email}
      AND created_at > now() - interval '15 minutes'
  `;
  const recentCount = Number(first(recentRows)?.count ?? 0);

  if (recentCount >= MAX_PASSWORD_RESET_REQUESTS_PER_WINDOW) {
    throw new ApiError(
      429,
      "PASSWORD_RESET_RATE_LIMITED",
      "Too many reset requests. Try again later.",
    );
  }

  if (!requestIp) return;

  const recentIpRows = await sql`
    SELECT count(*) AS count
    FROM password_reset_codes
    WHERE request_ip = ${requestIp}
      AND created_at > now() - interval '15 minutes'
  `;
  const recentIpCount = Number(first(recentIpRows)?.count ?? 0);

  if (recentIpCount >= MAX_PASSWORD_RESET_REQUESTS_PER_IP_WINDOW) {
    throw new ApiError(
      429,
      "PASSWORD_RESET_RATE_LIMITED",
      "Too many reset requests. Try again later.",
    );
  }
}

async function validateEmailVerification(
  sql: ReturnType<typeof getSql>,
  verificationId: string,
  email: string,
  code: string,
) {
  const rows = await sql`
    SELECT id, code_hash, attempt_count, expires_at, consumed_at
    FROM email_verification_codes
    WHERE id = ${verificationId}
      AND email = ${email}
    LIMIT 1
  `;
  const verification = first(rows);

  if (!verification) {
    throw new ApiError(400, "EMAIL_VERIFICATION_INVALID", "Verification code is invalid.");
  }

  if (verification.consumed_at) {
    throw new ApiError(400, "EMAIL_VERIFICATION_USED", "Verification code was already used.");
  }

  if (new Date(String(verification.expires_at)).getTime() <= Date.now()) {
    throw new ApiError(400, "EMAIL_VERIFICATION_EXPIRED", "Verification code has expired.");
  }

  const attempts = Number(verification.attempt_count ?? 0);
  if (attempts >= MAX_VERIFICATION_ATTEMPTS) {
    throw new ApiError(
      429,
      "EMAIL_VERIFICATION_LOCKED",
      "Too many incorrect verification attempts. Request a new code.",
    );
  }

  const isValid = await verifyVerificationCode(code, String(verification.code_hash));
  if (!isValid) {
    await sql`
      UPDATE email_verification_codes
      SET attempt_count = attempt_count + 1
      WHERE id = ${verificationId}
        AND consumed_at IS NULL
    `;
    throw new ApiError(400, "EMAIL_VERIFICATION_INVALID", "Verification code is incorrect.");
  }
}

async function validatePasswordResetCode(
  sql: ReturnType<typeof getSql>,
  resetId: string,
  email: string,
  code: string,
) {
  const rows = await sql`
    SELECT id, user_id, code_hash, attempt_count, expires_at, consumed_at
    FROM password_reset_codes
    WHERE id = ${resetId}
      AND email = ${email}
    LIMIT 1
  `;
  const reset = first(rows);

  if (!reset) {
    throw new ApiError(400, "PASSWORD_RESET_INVALID", "Reset code is invalid.");
  }

  if (reset.consumed_at) {
    throw new ApiError(400, "PASSWORD_RESET_USED", "Reset code was already used.");
  }

  if (new Date(String(reset.expires_at)).getTime() <= Date.now()) {
    throw new ApiError(400, "PASSWORD_RESET_EXPIRED", "Reset code has expired.");
  }

  const attempts = Number(reset.attempt_count ?? 0);
  if (attempts >= MAX_PASSWORD_RESET_ATTEMPTS) {
    throw new ApiError(
      429,
      "PASSWORD_RESET_LOCKED",
      "Too many incorrect reset attempts. Request a new code.",
    );
  }

  const isValid = await verifyVerificationCode(code, String(reset.code_hash));
  if (!isValid) {
    await sql`
      UPDATE password_reset_codes
      SET attempt_count = attempt_count + 1
      WHERE id = ${resetId}
        AND consumed_at IS NULL
    `;
    throw new ApiError(400, "PASSWORD_RESET_INVALID", "Reset code is incorrect.");
  }

  return reset;
}

function isUniqueEmailError(error: unknown): boolean {
  if (!(error instanceof Error)) return false;
  return error.message.includes("ux_users_email_active");
}

async function createVerifiedUser(
  sql: ReturnType<typeof getSql>,
  input: {
    fullName: string;
    email: string;
    passwordHash: string;
    verificationId: string;
  },
) {
  const userId = crypto.randomUUID();

  try {
    const results = await sql.transaction([
      sql`
        UPDATE email_verification_codes
        SET consumed_at = now(),
            attempt_count = attempt_count + 1
        WHERE id = ${input.verificationId}
          AND email = ${input.email}
          AND consumed_at IS NULL
        RETURNING id
      `,
      sql`
        INSERT INTO users (id, full_name, email, password_hash, email_verified_at)
        VALUES (${userId}, ${input.fullName}, ${input.email}, ${input.passwordHash}, now())
        RETURNING id, full_name, email, status, created_at, last_login_at
      `,
      ...defaultCategoryQueries(sql, userId),
    ]);

    if ((results[0] ?? []).length === 0) {
      throw new ApiError(400, "EMAIL_VERIFICATION_USED", "Verification code was already used.");
    }

    const userRow = first(results[1] ?? []);
    if (!userRow) {
      throw new ApiError(500, "USER_CREATE_FAILED", "Unable to create user.");
    }

    return userRow;
  } catch (error) {
    if (isUniqueEmailError(error)) {
      throw new ApiError(409, "EMAIL_ALREADY_EXISTS", "An account with this email already exists.");
    }
    throw error;
  }
}

authRoutes.post("/verification/start", async (c) => {
  const body = await parseJson(c, verificationStartSchema);
  const sql = getSql(c.env);
  const requestIp = c.req.header("CF-Connecting-IP") ?? c.req.header("X-Forwarded-For") ?? null;
  const userAgent = c.req.header("User-Agent") ?? null;

  await assertEmailCanRegister(sql, body.email);
  await assertVerificationRequestAllowed(sql, body.email, requestIp);

  const code = generateVerificationCode();
  const codeHash = await hashVerificationCode(code);

  const rows = await sql`
    INSERT INTO email_verification_codes (email, code_hash, expires_at, request_ip, user_agent)
    VALUES (
      ${body.email},
      ${codeHash},
      now() + (${VERIFICATION_TTL_SECONDS} * interval '1 second'),
      ${requestIp},
      ${userAgent}
    )
    RETURNING id
  `;
  const verification = first(rows);
  if (!verification) {
    throw new ApiError(500, "EMAIL_VERIFICATION_CREATE_FAILED", "Unable to start verification.");
  }

  try {
    const delivery = await sendVerificationEmail(c.env, {
      email: body.email,
      code,
      fullName: body.fullName,
    });

    return ok(
      c,
      {
        verificationId: String(verification.id),
        email: body.email,
        expiresInSeconds: VERIFICATION_TTL_SECONDS,
        delivery: delivery.delivery,
        ...(delivery.debugCode ? { debugCode: delivery.debugCode } : {}),
      },
      201,
    );
  } catch (error) {
    await sql`
      UPDATE email_verification_codes
      SET consumed_at = now()
      WHERE id = ${String(verification.id)}
    `;
    throw error;
  }
});

authRoutes.post("/password-reset/start", async (c) => {
  const body = await parseJson(c, passwordResetStartSchema);
  const sql = getSql(c.env);
  const requestIp = c.req.header("CF-Connecting-IP") ?? c.req.header("X-Forwarded-For") ?? null;
  const userAgent = c.req.header("User-Agent") ?? null;

  const genericResponse = {
    email: body.email,
    expiresInSeconds: PASSWORD_RESET_TTL_SECONDS,
    message: "If this email has a Lunexa account, a reset code has been sent.",
  };

  const userRow = await findActiveUserByEmail(sql, body.email);
  if (!userRow || String(userRow.status) !== "ACTIVE") {
    return ok(c, genericResponse, 202);
  }

  await assertPasswordResetRequestAllowed(sql, body.email, requestIp);

  const code = generateVerificationCode();
  const codeHash = await hashVerificationCode(code);

  const rows = await sql`
    INSERT INTO password_reset_codes (user_id, email, code_hash, expires_at, request_ip, user_agent)
    VALUES (
      ${String(userRow.id)},
      ${body.email},
      ${codeHash},
      now() + (${PASSWORD_RESET_TTL_SECONDS} * interval '1 second'),
      ${requestIp},
      ${userAgent}
    )
    RETURNING id
  `;
  const reset = first(rows);
  if (!reset) {
    throw new ApiError(500, "PASSWORD_RESET_CREATE_FAILED", "Unable to start password reset.");
  }

  try {
    const delivery = await sendPasswordResetEmail(c.env, {
      email: body.email,
      code,
      fullName: String(userRow.full_name),
    });

    return ok(
      c,
      {
        ...genericResponse,
        resetId: String(reset.id),
        delivery: delivery.delivery,
        ...(delivery.debugCode ? { debugCode: delivery.debugCode } : {}),
      },
      202,
    );
  } catch (error) {
    await sql`
      UPDATE password_reset_codes
      SET consumed_at = now()
      WHERE id = ${String(reset.id)}
    `;
    throw error;
  }
});

authRoutes.post("/password-reset/confirm", async (c) => {
  const body = await parseJson(c, passwordResetConfirmSchema);
  const sql = getSql(c.env);

  const reset = await validatePasswordResetCode(sql, body.resetId, body.email, body.code);
  const userRow = await findActiveUserByEmail(sql, body.email);
  if (!userRow || String(userRow.id) !== String(reset.user_id) || String(userRow.status) !== "ACTIVE") {
    throw new ApiError(400, "PASSWORD_RESET_INVALID", "Reset code is invalid.");
  }

  const passwordHash = await hashPassword(body.newPassword);
  const results = await sql.transaction([
    sql`
      UPDATE password_reset_codes
      SET consumed_at = now(),
          attempt_count = attempt_count + 1
      WHERE id = ${body.resetId}
        AND consumed_at IS NULL
      RETURNING id
    `,
    sql`
      UPDATE users
      SET password_hash = ${passwordHash},
          last_login_at = now()
      WHERE id = ${String(userRow.id)}
        AND deleted_at IS NULL
      RETURNING id, full_name, email, status, created_at, last_login_at
    `,
  ]);

  if ((results[0] ?? []).length === 0) {
    throw new ApiError(400, "PASSWORD_RESET_USED", "Reset code was already used.");
  }

  const updatedUser = first(results[1] ?? []);
  if (!updatedUser) {
    throw new ApiError(500, "PASSWORD_RESET_FAILED", "Unable to reset password.");
  }

  const user: AuthUser = {
    id: String(updatedUser.id),
    fullName: String(updatedUser.full_name),
    email: String(updatedUser.email),
  };

  return ok(c, {
    user: mapUser(updatedUser),
    tokens: await createTokenPair(c.env, user),
  });
});

authRoutes.post("/register", async (c) => {
  const body = await parseJson(c, registerSchema);
  const sql = getSql(c.env);

  await assertEmailCanRegister(sql, body.email);
  await validateEmailVerification(
    sql,
    body.emailVerificationId,
    body.email,
    body.emailVerificationCode,
  );

  const passwordHash = await hashPassword(body.password);
  const userRow = await createVerifiedUser(sql, {
    fullName: body.fullName,
    email: body.email,
    passwordHash,
    verificationId: body.emailVerificationId,
  });

  const user: AuthUser = {
    id: String(userRow.id),
    fullName: String(userRow.full_name),
    email: String(userRow.email),
  };

  return ok(
    c,
    {
      user: mapUser(userRow),
      tokens: await createTokenPair(c.env, user),
    },
    201,
  );
});

authRoutes.post("/login", async (c) => {
  const body = await parseJson(c, authSchema);
  const sql = getSql(c.env);

  const rows = await sql`
    SELECT id, full_name, email, password_hash, status, created_at, last_login_at
    FROM users
    WHERE email = ${body.email}
      AND deleted_at IS NULL
    LIMIT 1
  `;

  const userRow = first(rows);
  if (!userRow) {
    throw new ApiError(401, "INVALID_CREDENTIALS", "Invalid email or password.");
  }

  if (String(userRow.status) !== "ACTIVE") {
    throw new ApiError(403, "USER_DISABLED", "This user is not active.");
  }

  const isValidPassword = await verifyPassword(body.password, String(userRow.password_hash));
  if (!isValidPassword) {
    throw new ApiError(401, "INVALID_CREDENTIALS", "Invalid email or password.");
  }

  const updatedRows = await sql`
    UPDATE users
    SET last_login_at = now()
    WHERE id = ${String(userRow.id)}
    RETURNING id, full_name, email, status, created_at, last_login_at
  `;

  const updatedUser = first(updatedRows) ?? userRow;
  const user: AuthUser = {
    id: String(updatedUser.id),
    fullName: String(updatedUser.full_name),
    email: String(updatedUser.email),
  };

  return ok(c, {
    user: mapUser(updatedUser),
    tokens: await createTokenPair(c.env, user),
  });
});

authRoutes.post("/refresh", async (c) => {
  const body = await parseJson(c, refreshSchema);
  const tokenUser = await verifyToken(c.env, body.refreshToken, "refresh");
  const sql = getSql(c.env);

  const rows = await sql`
    SELECT id, full_name, email, status, created_at, last_login_at
    FROM users
    WHERE id = ${tokenUser.id}
      AND deleted_at IS NULL
    LIMIT 1
  `;

  const userRow = first(rows);
  if (!userRow || String(userRow.status) !== "ACTIVE") {
    throw new ApiError(401, "INVALID_REFRESH_TOKEN", "Invalid refresh token.");
  }

  const user: AuthUser = {
    id: String(userRow.id),
    fullName: String(userRow.full_name),
    email: String(userRow.email),
  };

  return ok(c, await createTokenPair(c.env, user));
});
