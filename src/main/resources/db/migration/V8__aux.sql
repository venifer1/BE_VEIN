-- =============================================================================
-- V8 — Auxiliary features (Phase 3)
-- 속보(news) · TVL · 유통량(supply) · 테마/섹터(theme) · 펀비차익(funding) · 틱띄기(scalp)
-- New external providers: DefiLlama, Bybit, Bloomberg RSS (real); Telegram (stub).
-- All money NUMERIC, all times TIMESTAMPTZ (UTC). No CHECK constraints on the
-- enum-ish VARCHARs (documented in COMMENTs) to match the V6 style.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. news_items — Telegram(coinness) + Bloomberg RSS feed, server-collected.
--    UNIQUE(source,url) so the upserting scheduler is idempotent. Items missing a
--    stable url (rare Telegram media) fall back to a synthetic t.me/<id> url.
-- -----------------------------------------------------------------------------
CREATE TABLE news_items (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  source       VARCHAR(16) NOT NULL,
  title        TEXT,
  body         TEXT,
  url          TEXT        NOT NULL,
  published_at TIMESTAMPTZ,
  collected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_news_source_url UNIQUE (source, url)
);
COMMENT ON COLUMN news_items.source IS 'TELEGRAM | BLOOMBERG';
-- Newest-first cursor scans (published_at desc, id desc).
CREATE INDEX ix_news_published_at ON news_items(published_at DESC, id DESC);
CREATE INDEX ix_news_source_published ON news_items(source, published_at DESC);
CREATE INDEX ix_news_collected_at ON news_items(collected_at DESC);

-- -----------------------------------------------------------------------------
-- 2. tvl_snapshots — DefiLlama protocols + chains snapshots (defillama_service.py).
--    entity_type PROTOCOL has slug as external_id (history lookup key); CHAIN uses
--    the chain name. TVL < $1000 rows are filtered upstream before insert.
-- -----------------------------------------------------------------------------
CREATE TABLE tvl_snapshots (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  entity_type  VARCHAR(16)  NOT NULL,
  external_id  VARCHAR(128) NOT NULL,
  name         VARCHAR(256) NOT NULL,
  category     VARCHAR(128),
  chains       TEXT,
  tvl          NUMERIC(30,2),
  mcap         NUMERIC(30,2),
  change_1d    NUMERIC(12,4),
  change_7d    NUMERIC(12,4),
  collected_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON COLUMN tvl_snapshots.entity_type IS 'PROTOCOL | CHAIN';
COMMENT ON COLUMN tvl_snapshots.external_id IS 'PROTOCOL: defillama slug; CHAIN: chain name';
COMMENT ON COLUMN tvl_snapshots.chains IS 'comma-joined chain names for a protocol';
CREATE INDEX ix_tvl_type_collected ON tvl_snapshots(entity_type, collected_at DESC);
CREATE INDEX ix_tvl_collected_at ON tvl_snapshots(collected_at DESC);

-- -----------------------------------------------------------------------------
-- 3. supply_snapshots — CoinGecko /coins/markets circulating supply (supply_tab.py).
-- -----------------------------------------------------------------------------
CREATE TABLE supply_snapshots (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  coingecko_id    VARCHAR(96)  NOT NULL,
  rank            INTEGER,
  name            VARCHAR(128),
  symbol          VARCHAR(32),
  price_usd       NUMERIC(30,10),
  market_cap      NUMERIC(30,2),
  circulating     NUMERIC(40,4),
  total_supply    NUMERIC(40,4),
  max_supply      NUMERIC(40,4),
  circulating_pct NUMERIC(12,4),
  fdv             NUMERIC(30,2),
  collected_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_supply_collected_rank ON supply_snapshots(collected_at DESC, rank ASC);
CREATE INDEX ix_supply_collected_at ON supply_snapshots(collected_at DESC);

-- -----------------------------------------------------------------------------
-- 4. themes + theme_constituents — theme/sector classification (theme_sector_tab.py).
--    market CRYPTO | US | KR. A theme groups constituents; each constituent carries
--    its classification source (manual_json | heuristic | coingecko) and confidence.
-- -----------------------------------------------------------------------------
CREATE TABLE themes (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  market       VARCHAR(16)  NOT NULL,
  name         VARCHAR(128) NOT NULL,
  collected_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uq_theme_market_name UNIQUE (market, name)
);
COMMENT ON COLUMN themes.market IS 'CRYPTO | US | KR';

CREATE TABLE theme_constituents (
  id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  theme_id                 BIGINT       NOT NULL REFERENCES themes(id) ON DELETE CASCADE,
  instrument_ref           VARCHAR(64)  NOT NULL,
  display_name             VARCHAR(128),
  classification_source    VARCHAR(24)  NOT NULL,
  classification_confidence NUMERIC(5,4),
  collected_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uq_theme_constituent UNIQUE (theme_id, instrument_ref)
);
COMMENT ON COLUMN theme_constituents.instrument_ref IS 'coin symbol | US ticker | KR 6-digit code';
COMMENT ON COLUMN theme_constituents.classification_source IS 'manual_json | heuristic | coingecko';
CREATE INDEX ix_theme_constituent_theme ON theme_constituents(theme_id);

-- -----------------------------------------------------------------------------
-- 5. funding_arb_snapshots — Bybit perp funding vs Upbit KRW spot (funding_arb_tab.py).
--    expected_Nx = funding_pct*N - roundtrip fees (Upbit 0.05% + Bybit 0.055% each way).
-- -----------------------------------------------------------------------------
CREATE TABLE funding_arb_snapshots (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  symbol          VARCHAR(32)  NOT NULL,
  name            VARCHAR(128),
  funding_pct     NUMERIC(12,6),
  upbit_price     NUMERIC(30,10),
  bybit_price     NUMERIC(30,10),
  next_funding_at TIMESTAMPTZ,
  expected_1x_pct NUMERIC(12,6),
  expected_2x_pct NUMERIC(12,6),
  collected_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_funding_collected_at ON funding_arb_snapshots(collected_at DESC);

-- -----------------------------------------------------------------------------
-- 6. scalp_scores — Upbit scalping ranking (scalp_ranker.py / scalp_metrics.py).
--    components JSONB carries the per-factor scores + detail (orderbook/trade flow).
--    wall_state: bid_wall | ask_wall | neutral.
-- -----------------------------------------------------------------------------
CREATE TABLE scalp_scores (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  symbol        VARCHAR(32)  NOT NULL,
  scalp_score   NUMERIC(6,2),
  spread_ticks  NUMERIC(12,4),
  tps           NUMERIC(12,4),
  micro_vol     NUMERIC(12,4),
  ob_imbalance  NUMERIC(14,6),
  wall_state    VARCHAR(16),
  components    JSONB,
  collected_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON COLUMN scalp_scores.wall_state IS 'bid_wall | ask_wall | neutral';
CREATE INDEX ix_scalp_collected_score ON scalp_scores(collected_at DESC, scalp_score DESC);
CREATE INDEX ix_scalp_symbol_collected ON scalp_scores(symbol, collected_at DESC);
