CREATE TABLE account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id TEXT,
    type TEXT NOT NULL CHECK (type in ('CUSTOMER_WALLET', 'MERCHANT', 'BANK_CLEARING', 'PAYOUTS_IN_FLIGHT', 'FEE_REVENUE', 'SUSPENSE', 'ADJUSTMENT')),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status in ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX one_wallet_per_customer ON account (owner_id) WHERE type = 'CUSTOMER_WALLET';
CREATE UNIQUE INDEX one_account_per_system_type ON account (type)
    WHERE type IN ('BANK_CLEARING', 'PAYOUTS_IN_FLIGHT', 'FEE_REVENUE', 'SUSPENSE', 'ADJUSTMENT');

CREATE TABLE journal_entry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id TEXT UNIQUE NOT NULL,
    type TEXT NOT NULL CHECK (type in ('TOP_UP', 'TRANSFER', 'PAYMENT_CAPTURE', 'REFUND', 'PAYOUT_DEBIT', 'PAYOUT_SETTLE', 'PAYOUT_REVERSAL', 'SUSPENSE', 'ADJUSTMENT')),
    reference_type TEXT,
    reference_id TEXT,
    description TEXT,
    actor TEXT NOT NULL,
    correlation_id TEXT,
    posted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE posting (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id UUID NOT NULL REFERENCES journal_entry(id),
    account_id UUID NOT NULL REFERENCES account(id),
    amount BIGINT NOT NULL CONSTRAINT non_zero_amount CHECK (amount <> 0),
    posted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX show_wallet_statement ON posting (account_id, posted_at);
CREATE INDEX show_entry_postings ON posting (entry_id);

CREATE TABLE balance (
    account_id UUID PRIMARY KEY REFERENCES account(id),
    posted BIGINT NOT NULL DEFAULT 0,
    held BIGINT NOT NULL DEFAULT 0 CONSTRAINT non_neg_held CHECK (held >= 0),
    overdraft_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT no_overdraft CHECK (overdraft_allowed OR posted - held >= 0)
);