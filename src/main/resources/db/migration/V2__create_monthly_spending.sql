-- Running USD total per account, expense category and business month.
-- Deliberate denormalization of SUM(bank_transaction.amount_usd): registering a transaction is O(1),
-- and the row is what concurrent transactions lock (SELECT ... FOR UPDATE) so they never see the same remainder.
-- It is always updated in the same database transaction as the transaction it counts.
CREATE TABLE monthly_spending
(
    account          VARCHAR(10)    NOT NULL CHECK (account ~ '^[0-9]{10}$'),
    expense_category VARCHAR(16)    NOT NULL CHECK (expense_category IN ('PRODUCT', 'SERVICE')),
    month_start      DATE           NOT NULL CHECK (EXTRACT(DAY FROM month_start) = 1),
    spent_usd        NUMERIC(19, 2) NOT NULL CHECK (spent_usd >= 0),
    PRIMARY KEY (account, expense_category, month_start)
);

COMMENT ON TABLE monthly_spending IS 'USD spent per account, category and business month; lock target for limit checks';
