-- Expense transactions. Named bank_transaction because TRANSACTION is an SQL keyword.
CREATE TABLE bank_transaction
(
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_from            VARCHAR(10)    NOT NULL CHECK (account_from ~ '^[0-9]{10}$'),
    account_to              VARCHAR(10)    NOT NULL CHECK (account_to ~ '^[0-9]{10}$'),
    currency                VARCHAR(3)     NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    amount                  NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    expense_category        VARCHAR(16)    NOT NULL CHECK (expense_category IN ('PRODUCT', 'SERVICE')),
    occurred_at             TIMESTAMPTZ    NOT NULL,
    occurred_offset_seconds INTEGER        NOT NULL CHECK (occurred_offset_seconds BETWEEN -64800 AND 64800),
    received_at             TIMESTAMPTZ    NOT NULL,
    status                  VARCHAR(16)    NOT NULL CHECK (status IN ('PENDING', 'PROCESSED')),
    amount_usd              NUMERIC(19, 2) CHECK (amount_usd >= 0),
    limit_id                BIGINT REFERENCES spending_limit (id),
    limit_exceeded          BOOLEAN,
    processed_at            TIMESTAMPTZ,
    -- A processed transaction always has its USD amount, limit and flag; a pending one has none of them
    CONSTRAINT bank_transaction_processing_consistent CHECK (
        (status = 'PENDING'
            AND amount_usd IS NULL AND limit_id IS NULL AND limit_exceeded IS NULL AND processed_at IS NULL)
            OR (status = 'PROCESSED'
            AND amount_usd IS NOT NULL AND limit_id IS NOT NULL AND limit_exceeded IS NOT NULL AND processed_at IS NOT NULL))
);

COMMENT ON COLUMN bank_transaction.occurred_at IS 'Moment of the transaction; TIMESTAMPTZ stores it in UTC';
COMMENT ON COLUMN bank_transaction.occurred_offset_seconds IS 'Offset the client sent, to return the date as it came';
COMMENT ON COLUMN bank_transaction.limit_id IS 'Limit the transaction was checked against';

-- Report of exceeded transactions of an account, ordered by date: only flagged rows are indexed
CREATE INDEX ix_bank_transaction_exceeded ON bank_transaction (account_from, occurred_at) WHERE limit_exceeded;

-- Foreign keys are not indexed automatically in PostgreSQL; needed for joins and aggregation by limit
CREATE INDEX ix_bank_transaction_limit ON bank_transaction (limit_id);
