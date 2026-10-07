ALTER TABLE bank_transaction
    ADD COLUMN attempts        INTEGER      NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    ADD COLUMN next_attempt_at TIMESTAMPTZ,
    ADD COLUMN last_error      VARCHAR(500);

CREATE INDEX ix_bank_transaction_pending ON bank_transaction (received_at) WHERE status = 'PENDING';
