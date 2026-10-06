-- Exchange rates fetched from the external provider. Stored once and reused: every external request costs money.
CREATE TABLE exchange_rate
(
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    currency      VARCHAR(3)     NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    rate_date     DATE           NOT NULL,
    units_per_usd NUMERIC(19, 8) NOT NULL CHECK (units_per_usd > 0),
    source_date   DATE           NOT NULL,
    kind          VARCHAR(16)    NOT NULL CHECK (kind IN ('CLOSE', 'PREVIOUS_CLOSE')),
    fetched_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT exchange_rate_currency_date_key UNIQUE (currency, rate_date),
    CONSTRAINT exchange_rate_kind_consistent CHECK (
        (kind = 'CLOSE' AND source_date = rate_date)
            OR (kind = 'PREVIOUS_CLOSE' AND source_date < rate_date))
);

COMMENT ON TABLE exchange_rate IS 'Daily rates as units of currency per 1 USD';
COMMENT ON COLUMN exchange_rate.source_date IS 'Trading day of the close; earlier than rate_date for weekends and holidays';
