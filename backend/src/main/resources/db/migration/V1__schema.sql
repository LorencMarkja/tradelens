CREATE TABLE lot (
    code                    VARCHAR(32)   PRIMARY KEY,
    product                 VARCHAR(100)  NOT NULL,
    quantity_mt             NUMERIC(19,6) NOT NULL CHECK (quantity_mt > 0),
    location                VARCHAR(200)  NOT NULL,
    currency                VARCHAR(3)    NOT NULL,
    purchase_cost_per_mt    NUMERIC(19,6) NOT NULL,
    financed_capital_per_mt NUMERIC(19,6) NOT NULL,
    annual_rate             NUMERIC(9,6)  NOT NULL,
    day_count_basis         INTEGER       NOT NULL CHECK (day_count_basis > 0)
);

CREATE TABLE offer (
    id                     BIGINT        PRIMARY KEY,
    lot_code               VARCHAR(32)   NOT NULL REFERENCES lot (code),
    buyer_name             VARCHAR(100)  NOT NULL UNIQUE,
    sale_price_per_mt      NUMERIC(19,6) CHECK (sale_price_per_mt > 0),
    sale_price_source      VARCHAR(30)   NOT NULL,
    logistics_cost_per_mt  NUMERIC(19,6) CHECK (logistics_cost_per_mt >= 0),
    logistics_source       VARCHAR(30)   NOT NULL,
    financing_days         INTEGER       CHECK (financing_days BETWEEN 0 AND 365),
    financing_days_source  VARCHAR(30)   NOT NULL,
    valid_until            DATE          NOT NULL,
    logistics_quote_expiry DATE,
    version                BIGINT        NOT NULL
);

CREATE TABLE update_proposal (
    id                  UUID         PRIMARY KEY,
    origin              VARCHAR(20)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    message             TEXT,
    original_extraction TEXT,
    ai_model            VARCHAR(100),
    prompt_version      VARCHAR(40),
    offer_id            BIGINT REFERENCES offer (id),
    base_offer_version  BIGINT,
    draft               TEXT         NOT NULL,
    corrections         TEXT         NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    applied_at          TIMESTAMPTZ
);

CREATE TABLE change_event (
    id                  BIGSERIAL     PRIMARY KEY,
    proposal_id         UUID          NOT NULL UNIQUE REFERENCES update_proposal (id),
    offer_id            BIGINT        NOT NULL REFERENCES offer (id),
    occurred_at         TIMESTAMPTZ   NOT NULL,
    origin              VARCHAR(20)   NOT NULL,
    changes             TEXT          NOT NULL,
    message             TEXT,
    original_extraction TEXT,
    corrections         TEXT          NOT NULL,
    ai_model            VARCHAR(100),
    prompt_version      VARCHAR(40),
    total_margin_before NUMERIC(24,6),
    total_margin_after  NUMERIC(24,6)
);

CREATE INDEX idx_change_event_occurred_at ON change_event (occurred_at DESC);
