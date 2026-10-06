-- Monthly spending limits in USD, per account and expense category.
-- Rows are immutable: a client sets a new limit instead of changing an old one.
CREATE TABLE spending_limit
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account          VARCHAR(10)   NOT NULL CHECK (account ~ '^[0-9]{10}$'),
    expense_category VARCHAR(16)   NOT NULL CHECK (expense_category IN ('PRODUCT', 'SERVICE')),
    limit_sum        NUMERIC(19, 2) NOT NULL CHECK (limit_sum >= 0),
    currency         VARCHAR(3)    NOT NULL CHECK (currency = 'USD'),
    limit_datetime   TIMESTAMPTZ   NOT NULL,
    is_default       BOOLEAN       NOT NULL
);

COMMENT ON TABLE spending_limit IS 'Monthly spending limits in USD; rows are never updated or deleted';
COMMENT ON COLUMN spending_limit.limit_datetime IS 'Moment the limit was set; it applies to transactions made at or after it';
COMMENT ON COLUMN spending_limit.is_default IS 'Created by the service (1000 USD) for an account and category without a client limit';

-- Lookup of the latest client limit set at or before a moment: index-only range scan, no sorting
CREATE UNIQUE INDEX ux_spending_limit_client_moment
    ON spending_limit (account, expense_category, limit_datetime DESC)
    WHERE NOT is_default;

-- At most one system default per account and category; target of INSERT ... ON CONFLICT DO NOTHING
CREATE UNIQUE INDEX ux_spending_limit_default
    ON spending_limit (account, expense_category)
    WHERE is_default;

CREATE FUNCTION forbid_spending_limit_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'spending_limit rows are immutable, set a new limit instead (% on id %)', TG_OP, OLD.id;
END;
$$;

CREATE TRIGGER spending_limit_immutable
    BEFORE UPDATE OR DELETE
    ON spending_limit
    FOR EACH ROW
EXECUTE FUNCTION forbid_spending_limit_change();
