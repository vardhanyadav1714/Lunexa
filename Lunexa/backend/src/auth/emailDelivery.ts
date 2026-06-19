import type { Env } from "../env";
import { ApiError } from "../errors";

type VerificationEmailInput = {
  email: string;
  code: string;
  fullName: string;
};

type CodeDelivery = {
  delivery: "email" | "dev";
  debugCode?: string;
};

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => {
    switch (character) {
      case "&":
        return "&amp;";
      case "<":
        return "&lt;";
      case ">":
        return "&gt;";
      case '"':
        return "&quot;";
      default:
        return "&#39;";
    }
  });
}

export async function sendVerificationEmail(
  env: Env,
  input: VerificationEmailInput,
): Promise<CodeDelivery> {
  if (env.EMAIL_VERIFICATION_MODE === "dev") {
    console.info("Lunexa verification code", {
      email: input.email,
      code: input.code,
    });
    return {
      delivery: "dev",
      debugCode: input.code,
    };
  }

  if (!env.RESEND_API_KEY || !env.VERIFICATION_EMAIL_FROM) {
    throw new ApiError(
      500,
      "EMAIL_VERIFICATION_NOT_CONFIGURED",
      "Email verification is not configured.",
    );
  }

  const safeFullName = escapeHtml(input.fullName);

  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${env.RESEND_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: env.VERIFICATION_EMAIL_FROM,
      to: [input.email],
      subject: "Your Lunexa verification code",
      text: `Hi ${input.fullName},\n\nYour Lunexa verification code is ${input.code}. It expires in 10 minutes.\n\nIf you did not request this, you can ignore this email.`,
      html: `
        <div style="font-family:Inter,Arial,sans-serif;line-height:1.6;color:#102019">
          <p>Hi ${safeFullName},</p>
          <p>Your Lunexa verification code is:</p>
          <p style="font-size:28px;font-weight:700;letter-spacing:4px">${input.code}</p>
          <p>This code expires in 10 minutes.</p>
          <p>If you did not request this, you can ignore this email.</p>
        </div>
      `,
    }),
  });

  if (!response.ok) {
    const body = await response.text().catch(() => "");
    console.error("Lunexa verification email failed", {
      status: response.status,
      body,
    });
    throw new ApiError(502, "EMAIL_DELIVERY_FAILED", "Unable to send verification email.");
  }

  return { delivery: "email" };
}

export async function sendPasswordResetEmail(
  env: Env,
  input: VerificationEmailInput,
): Promise<CodeDelivery> {
  if (env.PASSWORD_RESET_MODE === "dev" || env.EMAIL_VERIFICATION_MODE === "dev") {
    console.info("Lunexa password reset code", {
      email: input.email,
      code: input.code,
    });
    return {
      delivery: "dev",
      debugCode: input.code,
    };
  }

  if (!env.RESEND_API_KEY || !env.VERIFICATION_EMAIL_FROM) {
    throw new ApiError(
      500,
      "PASSWORD_RESET_NOT_CONFIGURED",
      "Password reset email is not configured.",
    );
  }

  const safeFullName = escapeHtml(input.fullName);

  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${env.RESEND_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: env.VERIFICATION_EMAIL_FROM,
      to: [input.email],
      subject: "Reset your Lunexa password",
      text: `Hi ${input.fullName},\n\nYour Lunexa password reset code is ${input.code}. It expires in 10 minutes.\n\nIf you did not request this, you can ignore this email.`,
      html: `
        <div style="font-family:Inter,Arial,sans-serif;line-height:1.6;color:#102019">
          <p>Hi ${safeFullName},</p>
          <p>Use this code to reset your Lunexa password:</p>
          <p style="font-size:28px;font-weight:700;letter-spacing:4px">${input.code}</p>
          <p>This code expires in 10 minutes.</p>
          <p>If you did not request this, you can ignore this email.</p>
        </div>
      `,
    }),
  });

  if (!response.ok) {
    const body = await response.text().catch(() => "");
    console.error("Lunexa password reset email failed", {
      status: response.status,
      body,
    });
    throw new ApiError(502, "EMAIL_DELIVERY_FAILED", "Unable to send password reset email.");
  }

  return { delivery: "email" };
}
