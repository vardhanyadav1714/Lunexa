BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name TEXT NOT NULL CHECK (char_length(trim(full_name)) BETWEEN 2 AND 120),
    email CITEXT NOT NULL,
    password_hash TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    email_verified_at TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    deleted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0)
);

CREATE TABLE email_verification_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email CITEXT NOT NULL,
    code_hash TEXT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    request_ip TEXT,
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE password_reset_codes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    email CITEXT NOT NULL,
    code_hash TEXT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    request_ip TEXT,
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL CHECK (char_length(trim(name)) BETWEEN 1 AND 80),
    type TEXT NOT NULL
        CHECK (type IN ('CASH', 'BANK', 'WALLET', 'CREDIT_CARD', 'INVESTMENT', 'OTHER')),
    currency CHAR(3) NOT NULL DEFAULT 'INR' CHECK (currency = upper(currency)),
    opening_balance NUMERIC(14, 2) NOT NULL DEFAULT 0,
    current_balance NUMERIC(14, 2) NOT NULL DEFAULT 0,
    is_archived BOOLEAN NOT NULL DEFAULT false,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    deleted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    UNIQUE (id, user_id)
);

CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name TEXT NOT NULL CHECK (char_length(trim(name)) BETWEEN 1 AND 80),
    type TEXT NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    icon_key TEXT,
    color_hex TEXT CHECK (color_hex IS NULL OR color_hex ~ '^#[0-9A-Fa-f]{6}$'),
    is_default BOOLEAN NOT NULL DEFAULT false,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    deleted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    UNIQUE (id, user_id)
);

CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id UUID NOT NULL,
    transfer_account_id UUID,
    category_id UUID,
    type TEXT NOT NULL CHECK (type IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL DEFAULT 'INR' CHECK (currency = upper(currency)),
    note TEXT,
    merchant TEXT,
    transaction_date DATE NOT NULL DEFAULT CURRENT_DATE,
    posted_at TIMESTAMPTZ,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    deleted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    FOREIGN KEY (account_id, user_id)
        REFERENCES accounts(id, user_id)
        ON DELETE RESTRICT,
    FOREIGN KEY (transfer_account_id, user_id)
        REFERENCES accounts(id, user_id)
        ON DELETE RESTRICT,
    FOREIGN KEY (category_id, user_id)
        REFERENCES categories(id, user_id)
        ON DELETE RESTRICT,
    CHECK (
        (
            type = 'TRANSFER'
            AND transfer_account_id IS NOT NULL
            AND category_id IS NULL
            AND transfer_account_id <> account_id
        )
        OR (
            type IN ('INCOME', 'EXPENSE')
            AND transfer_account_id IS NULL
        )
    )
);

CREATE TABLE budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id UUID NOT NULL,
    period_month DATE NOT NULL,
    amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    alert_threshold_percent NUMERIC(5, 2) NOT NULL DEFAULT 80.00
        CHECK (alert_threshold_percent BETWEEN 0 AND 100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    deleted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    FOREIGN KEY (category_id, user_id)
        REFERENCES categories(id, user_id)
        ON DELETE RESTRICT,
    CHECK (period_month = date_trunc('month', period_month)::date)
);

CREATE UNIQUE INDEX ux_users_email_active
    ON users(email)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_users_status_active
    ON users(status)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_email_verification_codes_email_active
    ON email_verification_codes(email, created_at DESC)
    WHERE consumed_at IS NULL;

CREATE INDEX idx_email_verification_codes_expiry
    ON email_verification_codes(expires_at)
    WHERE consumed_at IS NULL;

CREATE INDEX idx_password_reset_codes_email_active
    ON password_reset_codes(email, created_at DESC)
    WHERE consumed_at IS NULL;

CREATE INDEX idx_password_reset_codes_user_active
    ON password_reset_codes(user_id, created_at DESC)
    WHERE consumed_at IS NULL;

CREATE INDEX idx_password_reset_codes_expiry
    ON password_reset_codes(expires_at)
    WHERE consumed_at IS NULL;

CREATE UNIQUE INDEX ux_accounts_user_name_active
    ON accounts(user_id, lower(name))
    WHERE deleted_at IS NULL;

CREATE INDEX idx_accounts_user_active
    ON accounts(user_id, sort_order, name)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_accounts_user_type_active
    ON accounts(user_id, type)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_categories_user_type_name_active
    ON categories(user_id, type, lower(name))
    WHERE deleted_at IS NULL;

CREATE INDEX idx_categories_user_type_active
    ON categories(user_id, type, sort_order, name)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_categories_user_default_active
    ON categories(user_id, is_default)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_transactions_user_date_active
    ON transactions(user_id, transaction_date DESC, id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_transactions_account_date_active
    ON transactions(user_id, account_id, transaction_date DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_transactions_category_date_active
    ON transactions(user_id, category_id, transaction_date DESC)
    WHERE deleted_at IS NULL AND category_id IS NOT NULL;

CREATE INDEX idx_transactions_type_date_active
    ON transactions(user_id, type, transaction_date DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_transactions_transfer_account_active
    ON transactions(user_id, transfer_account_id)
    WHERE deleted_at IS NULL AND transfer_account_id IS NOT NULL;

CREATE INDEX idx_transactions_metadata_gin
    ON transactions USING GIN (metadata)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_budgets_user_category_month_active
    ON budgets(user_id, category_id, period_month)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_budgets_user_month_active
    ON budgets(user_id, period_month)
    WHERE deleted_at IS NULL;

CREATE OR REPLACE FUNCTION set_audit_fields()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    NEW.version = OLD.version + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_set_audit_fields
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_audit_fields();

CREATE TRIGGER trg_accounts_set_audit_fields
    BEFORE UPDATE ON accounts
    FOR EACH ROW
    EXECUTE FUNCTION set_audit_fields();

CREATE TRIGGER trg_categories_set_audit_fields
    BEFORE UPDATE ON categories
    FOR EACH ROW
    EXECUTE FUNCTION set_audit_fields();

CREATE TRIGGER trg_transactions_set_audit_fields
    BEFORE UPDATE ON transactions
    FOR EACH ROW
    EXECUTE FUNCTION set_audit_fields();

CREATE TRIGGER trg_budgets_set_audit_fields
    BEFORE UPDATE ON budgets
    FOR EACH ROW
    EXECUTE FUNCTION set_audit_fields();

COMMIT;
