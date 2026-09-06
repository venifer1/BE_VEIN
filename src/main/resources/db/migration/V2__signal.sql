CREATE TABLE pattern_signals (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  instrument_id BIGINT NOT NULL REFERENCES instruments(id),
  type          VARCHAR(16) NOT NULL,             -- ABC | TRIANGLE
  timeframe     VARCHAR(4)  NOT NULL,
  status        VARCHAR(20) NOT NULL,             -- DETECTED|NEAR_COMPLETION|INVALIDATED|EXPIRED|CLOSED
  score         NUMERIC(6,2),                     -- 알파: 완성도 기본값(또는 NULL)
  detected_at        TIMESTAMPTZ NOT NULL,
  anchor_candle_time TIMESTAMPTZ NOT NULL,        -- ABC=B봉, TRIANGLE=마지막 꼭짓점 봉
  algorithm_version  VARCHAR(32) NOT NULL,        -- abc-java-1.0.0
  rule_id            VARCHAR(24) NOT NULL,        -- ABC / TRIANGLE
  invalidation_rule  VARCHAR(32),                 -- A_LOW_BREAK 등
  invalidation_price NUMERIC(24,8),
  expires_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  signal_key    VARCHAR(128) NOT NULL UNIQUE      -- algo|rule|instrument|timeframe|anchor
);
CREATE INDEX ix_signals_type_status_det ON pattern_signals(type, status, detected_at DESC);

CREATE TABLE signal_evidence (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  signal_id     BIGINT NOT NULL REFERENCES pattern_signals(id) ON DELETE CASCADE,
  evidence_type VARCHAR(24) NOT NULL,             -- PIVOT_0|PIVOT_A|PIVOT_B|TREND_UPPER|TREND_LOWER|C_TARGET
  sequence_no   INT NOT NULL,
  price         NUMERIC(24,8),
  candle_time   TIMESTAMPTZ,
  payload       JSONB,
  UNIQUE (signal_id, evidence_type, sequence_no)
);
