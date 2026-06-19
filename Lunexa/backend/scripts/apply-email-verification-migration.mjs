import { readFileSync } from "node:fs";
import { neon } from "@neondatabase/serverless";

function loadLocalEnv() {
  try {
    const envFile = readFileSync(new URL("../.dev.vars", import.meta.url), "utf8");
    for (const line of envFile.split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) continue;

      const separatorIndex = trimmed.indexOf("=");
      if (separatorIndex === -1) continue;

      const key = trimmed.slice(0, separatorIndex).trim();
      let value = trimmed.slice(separatorIndex + 1).trim();
      if (
        (value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))
      ) {
        value = value.slice(1, -1);
      }
      process.env[key] ??= value;
    }
  } catch {
    // .dev.vars is optional; DATABASE_URL may already be set in the shell.
  }
}

function migrationStatements(sqlText) {
  return sqlText
    .split(/\r?\n/)
    .filter((line) => !line.trim().startsWith("--"))
    .join("\n")
    .split(";")
    .map((statement) => statement.trim())
    .filter(Boolean);
}

loadLocalEnv();

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) {
  console.error("DATABASE_URL is required. Set it in backend/.dev.vars or in the shell.");
  process.exit(1);
}

const sql = neon(databaseUrl);
const migration = readFileSync(
  new URL("../db/migrations/0002_email_verification_codes.sql", import.meta.url),
  "utf8",
);

for (const statement of migrationStatements(migration)) {
  await sql.query(statement);
}

console.log("Applied 0002_email_verification_codes.sql");
