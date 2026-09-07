CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    firebase_uid VARCHAR(128) NOT NULL UNIQUE,
    email VARCHAR(320) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(1000),
    avatar_public_id VARCHAR(255),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE expense_groups (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    owner_id UUID NOT NULL REFERENCES app_users(id),
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE group_members (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES expense_groups(id),
    user_id UUID NOT NULL REFERENCES app_users(id),
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    joined_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_group_member UNIQUE (group_id, user_id),
    CONSTRAINT ck_member_role CHECK (role IN ('OWNER', 'MEMBER'))
);
CREATE INDEX ix_group_members_user ON group_members(user_id, active);

CREATE TABLE invitations (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES expense_groups(id),
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_by UUID NOT NULL REFERENCES app_users(id),
    expires_at TIMESTAMPTZ NOT NULL,
    max_uses INTEGER NOT NULL,
    use_count INTEGER NOT NULL DEFAULT 0,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE expenses (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES expense_groups(id),
    description VARCHAR(200) NOT NULL,
    currency CHAR(3) NOT NULL,
    total NUMERIC(19,4) NOT NULL,
    split_type VARCHAR(20) NOT NULL,
    expense_date DATE NOT NULL,
    created_by UUID NOT NULL REFERENCES app_users(id),
    recurrence_occurrence_key VARCHAR(150) UNIQUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_expense_total CHECK (total > 0),
    CONSTRAINT ck_split_type CHECK (split_type IN ('EQUAL', 'EXACT', 'PERCENTAGE'))
);
CREATE INDEX ix_expenses_group_date ON expenses(group_id, expense_date DESC) WHERE deleted = FALSE;

CREATE TABLE expense_payers (
    id UUID PRIMARY KEY,
    expense_id UUID NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_users(id),
    amount NUMERIC(19,4) NOT NULL,
    CONSTRAINT uq_expense_payer UNIQUE (expense_id, user_id),
    CONSTRAINT ck_payer_amount CHECK (amount > 0)
);

CREATE TABLE expense_shares (
    id UUID PRIMARY KEY,
    expense_id UUID NOT NULL REFERENCES expenses(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_users(id),
    amount NUMERIC(19,4) NOT NULL,
    percentage NUMERIC(7,4),
    CONSTRAINT uq_expense_share UNIQUE (expense_id, user_id),
    CONSTRAINT ck_share_amount CHECK (amount >= 0),
    CONSTRAINT ck_share_percentage CHECK (percentage IS NULL OR (percentage >= 0 AND percentage <= 100))
);

CREATE TABLE settlements (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES expense_groups(id),
    from_user_id UUID NOT NULL REFERENCES app_users(id),
    to_user_id UUID NOT NULL REFERENCES app_users(id),
    currency CHAR(3) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    settlement_date DATE NOT NULL,
    note VARCHAR(300),
    created_by UUID NOT NULL REFERENCES app_users(id),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_settlement_amount CHECK (amount > 0),
    CONSTRAINT ck_settlement_users CHECK (from_user_id <> to_user_id)
);
CREATE INDEX ix_settlements_group ON settlements(group_id, settlement_date DESC) WHERE deleted = FALSE;

CREATE TABLE recurring_expenses (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES expense_groups(id),
    description VARCHAR(200) NOT NULL,
    currency CHAR(3) NOT NULL,
    total NUMERIC(19,4) NOT NULL,
    split_type VARCHAR(20) NOT NULL,
    payer_configuration JSONB NOT NULL,
    split_configuration JSONB NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    next_run_date DATE NOT NULL,
    anchor_day INTEGER NOT NULL,
    zone_id VARCHAR(60) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by UUID NOT NULL REFERENCES app_users(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_recurrence_frequency CHECK (frequency IN ('WEEKLY', 'MONTHLY'))
);
CREATE INDEX ix_recurring_due ON recurring_expenses(active, next_run_date);

CREATE TABLE activity_events (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL REFERENCES expense_groups(id),
    actor_id UUID REFERENCES app_users(id),
    event_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(30) NOT NULL,
    entity_id UUID,
    summary VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_activity_group_created ON activity_events(group_id, created_at DESC);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    notification_type VARCHAR(50) NOT NULL,
    title VARCHAR(120) NOT NULL,
    body VARCHAR(500) NOT NULL,
    data_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_notifications_user_created ON notifications(user_id, created_at DESC);

CREATE TABLE device_registrations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    token VARCHAR(512) NOT NULL UNIQUE,
    platform VARCHAR(20) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    idempotency_key VARCHAR(150) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    resource_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_idempotency UNIQUE (user_id, idempotency_key, operation)
);

