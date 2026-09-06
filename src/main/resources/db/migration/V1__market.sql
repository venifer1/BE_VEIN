CREATE TABLE instruments (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  market         VARCHAR(16)  NOT NULL,           -- CRYPTO
  exchange       VARCHAR(16)  NOT NULL,           -- UPBIT
  symbol         VARCHAR(32)  NOT NULL,           -- KRW-BTC
  name           VARCHAR(64),
  quote_currency VARCHAR(8)   NOT NULL,           -- KRW
  status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  UNIQUE (exchange, symbol)
);
CREATE INDEX ix_instruments_market_status ON instruments(market, status);

CREATE TABLE provider_symbols (
  id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  instrument_id   BIGINT NOT NULL REFERENCES instruments(id),
  provider        VARCHAR(16) NOT NULL,
  provider_symbol VARCHAR(32) NOT NULL,
  UNIQUE (provider, provider_symbol)
);

CREATE TABLE candles (
  instrument_id BIGINT        NOT NULL REFERENCES instruments(id),
  timeframe     VARCHAR(4)    NOT NULL CHECK (timeframe IN ('1h','4h','1d')),
  open_time     TIMESTAMPTZ   NOT NULL,           -- 업비트 candle_date_time_utc
  open          NUMERIC(24,8) NOT NULL,
  high          NUMERIC(24,8) NOT NULL,
  low           NUMERIC(24,8) NOT NULL,
  close         NUMERIC(24,8) NOT NULL,
  volume        NUMERIC(28,8) NOT NULL,
  provider      VARCHAR(16)   NOT NULL,
  is_final      BOOLEAN       NOT NULL DEFAULT TRUE,
  collected_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
  PRIMARY KEY (instrument_id, timeframe, open_time, provider)
);
CREATE INDEX ix_candles_open_time ON candles(instrument_id, timeframe, open_time DESC);

CREATE TABLE ingestion_runs (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  provider       VARCHAR(16) NOT NULL,
  job_type       VARCHAR(24) NOT NULL,            -- BACKFILL | POLL | GAPFILL
  timeframe      VARCHAR(4),
  started_at     TIMESTAMPTZ NOT NULL,
  ended_at       TIMESTAMPTZ,
  status         VARCHAR(16) NOT NULL,            -- RUNNING|OK|FAILED
  fetched_count  INT DEFAULT 0,
  inserted_count INT DEFAULT 0,
  gap_count      INT DEFAULT 0,
  error_code     VARCHAR(48)
);
CREATE INDEX ix_ingestion_provider_started ON ingestion_runs(provider, started_at DESC);
