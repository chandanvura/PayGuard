ALTER TABLE payments
    ADD COLUMN reconciliation_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE payments
    ADD COLUMN next_reconciliation_at TIMESTAMP WITH TIME ZONE;