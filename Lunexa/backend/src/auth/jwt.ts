import { SignJWT, jwtVerify } from "jose";
import type { AuthUser, Env } from "../env";
import { ApiError } from "../errors";

const encoder = new TextEncoder();

type TokenKind = "access" | "refresh";

type JwtPayload = {
  typ?: TokenKind;
  email?: string;
  fullName?: string;
};

function secretFor(env: Env, kind: TokenKind): Uint8Array {
  const secret = kind === "access" ? env.JWT_ACCESS_SECRET : env.JWT_REFRESH_SECRET;

  if (!secret || secret.length < 32) {
    throw new ApiError(500, "JWT_SECRET_NOT_CONFIGURED", "JWT secret is not configured securely.");
  }

  return encoder.encode(secret);
}

function ttlFor(env: Env, kind: TokenKind): number {
  const raw = kind === "access" ? env.JWT_ACCESS_TTL_SECONDS : env.JWT_REFRESH_TTL_SECONDS;
  const fallback = kind === "access" ? 900 : 2_592_000;
  const ttl = raw ? Number(raw) : fallback;
  return Number.isFinite(ttl) && ttl > 0 ? ttl : fallback;
}

async function signToken(env: Env, user: AuthUser, kind: TokenKind): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const ttl = ttlFor(env, kind);

  return new SignJWT({
    typ: kind,
    email: user.email,
    fullName: user.fullName,
  })
    .setProtectedHeader({ alg: "HS256" })
    .setSubject(user.id)
    .setIssuedAt(now)
    .setExpirationTime(now + ttl)
    .sign(secretFor(env, kind));
}

export async function createTokenPair(env: Env, user: AuthUser) {
  const accessTtl = ttlFor(env, "access");
  const refreshTtl = ttlFor(env, "refresh");

  return {
    accessToken: await signToken(env, user, "access"),
    refreshToken: await signToken(env, user, "refresh"),
    expiresIn: accessTtl,
    refreshExpiresIn: refreshTtl,
  };
}

export async function verifyToken(env: Env, token: string, kind: TokenKind): Promise<AuthUser> {
  try {
    const { payload } = await jwtVerify(token, secretFor(env, kind));
    const typedPayload = payload as JwtPayload;

    if (typedPayload.typ !== kind || !payload.sub || !typedPayload.email || !typedPayload.fullName) {
      throw new ApiError(401, "INVALID_TOKEN", "Invalid token.");
    }

    return {
      id: payload.sub,
      email: typedPayload.email,
      fullName: typedPayload.fullName,
    };
  } catch (error) {
    if (error instanceof ApiError) throw error;

    throw new ApiError(
      401,
      kind === "refresh" ? "INVALID_REFRESH_TOKEN" : "UNAUTHORIZED",
      kind === "refresh" ? "Invalid refresh token." : "Authentication is required.",
    );
  }
}
