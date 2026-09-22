-- The item catalogue, from the Wiki's /mapping endpoint. Nearly static: the id
-- is the game's own item id, so it is the natural primary key rather than a
-- surrogate.
CREATE TABLE items (
    id            INTEGER PRIMARY KEY,
    name          TEXT    NOT NULL,
    examine       TEXT,
    members       BOOLEAN NOT NULL DEFAULT FALSE,
    buy_limit     INTEGER,          -- null means the Wiki does not publish one
    high_alch     INTEGER,
    low_alch      INTEGER,
    shop_value    INTEGER,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_items_name ON items (lower(name));

-- The current price of each item, from /latest. One row per item, overwritten
-- on every poll: this is "now", not history.
CREATE TABLE latest_prices (
    item_id       INTEGER PRIMARY KEY REFERENCES items (id) ON DELETE CASCADE,
    high          INTEGER,          -- what buyers are paying == what you SELL at
    low           INTEGER,          -- what sellers are asking == what you BUY at
    high_time     TIMESTAMPTZ,
    low_time      TIMESTAMPTZ,
    high_volume   BIGINT,
    low_volume    BIGINT,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Append-only price history, from /5m. This is ours, not the Wiki's: the API
-- only serves recent windows, so 1-day / 1-week / 4-week averages have to be
-- computed from snapshots we kept ourselves.
CREATE TABLE price_history (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id       INTEGER NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    avg_high      INTEGER,
    avg_low       INTEGER,
    high_volume   BIGINT NOT NULL DEFAULT 0,
    low_volume    BIGINT NOT NULL DEFAULT 0,
    captured_at   TIMESTAMPTZ NOT NULL
);

-- Every history query is "this item, over this window, newest first", so the
-- index leads with item_id and then captured_at.
CREATE INDEX idx_price_history_item_time ON price_history (item_id, captured_at DESC);

-- One row per item per capture. Makes the ingest idempotent: re-running a poll
-- for a window already stored conflicts instead of duplicating, which matters
-- because a retry must not corrupt the averages.
CREATE UNIQUE INDEX uq_price_history_item_capture ON price_history (item_id, captured_at);
