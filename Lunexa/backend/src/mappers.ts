import { toIso, toMoney } from "./db";

type Row = Record<string, unknown>;

export function mapUser(row: Row) {
  return {
    id: String(row.id),
    fullName: String(row.full_name),
    email: String(row.email),
    status: String(row.status),
    createdAt: toIso(row.created_at),
    lastLoginAt: toIso(row.last_login_at),
  };
}

export function mapAccount(row: Row) {
  return {
    id: String(row.id),
    name: String(row.name),
    type: String(row.type),
    currency: String(row.currency),
    openingBalance: toMoney(row.opening_balance),
    currentBalance: toMoney(row.current_balance),
    isArchived: Boolean(row.is_archived),
    sortOrder: Number(row.sort_order),
    createdAt: toIso(row.created_at),
    updatedAt: toIso(row.updated_at),
    version: Number(row.version),
  };
}

export function mapCategory(row: Row) {
  return {
    id: String(row.id),
    name: String(row.name),
    type: String(row.type),
    iconKey: row.icon_key ? String(row.icon_key) : null,
    colorHex: row.color_hex ? String(row.color_hex) : null,
    isDefault: Boolean(row.is_default),
    sortOrder: Number(row.sort_order),
    createdAt: toIso(row.created_at),
    updatedAt: toIso(row.updated_at),
    version: Number(row.version),
  };
}

export function mapTransaction(row: Row) {
  return {
    id: String(row.id),
    accountId: String(row.account_id),
    transferAccountId: row.transfer_account_id ? String(row.transfer_account_id) : null,
    categoryId: row.category_id ? String(row.category_id) : null,
    type: String(row.type),
    amount: toMoney(row.amount),
    currency: String(row.currency),
    note: row.note ? String(row.note) : null,
    merchant: row.merchant ? String(row.merchant) : null,
    transactionDate: String(row.transaction_date).slice(0, 10),
    postedAt: toIso(row.posted_at),
    metadata: row.metadata ?? {},
    createdAt: toIso(row.created_at),
    updatedAt: toIso(row.updated_at),
    version: Number(row.version),
  };
}

export function mapBudget(row: Row) {
  return {
    id: String(row.id),
    categoryId: String(row.category_id),
    periodMonth: String(row.period_month).slice(0, 10),
    amount: toMoney(row.amount),
    alertThresholdPercent: toMoney(row.alert_threshold_percent),
    createdAt: toIso(row.created_at),
    updatedAt: toIso(row.updated_at),
    version: Number(row.version),
  };
}
