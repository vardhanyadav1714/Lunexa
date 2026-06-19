export type Env = {
  DATABASE_URL: string;
  JWT_ACCESS_SECRET: string;
  JWT_REFRESH_SECRET: string;
  JWT_ACCESS_TTL_SECONDS?: string;
  JWT_REFRESH_TTL_SECONDS?: string;
  ALLOWED_ORIGIN?: string;
  RESEND_API_KEY?: string;
  VERIFICATION_EMAIL_FROM?: string;
  EMAIL_VERIFICATION_MODE?: string;
  PASSWORD_RESET_MODE?: string;
};

export type AuthUser = {
  id: string;
  email: string;
  fullName: string;
};

export type AppVariables = {
  requestId: string;
  user: AuthUser;
};
