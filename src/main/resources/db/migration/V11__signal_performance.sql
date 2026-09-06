-- V11: signal performance tracking (기획서 §31). For each pattern signal we
-- record how price actually behaved over fixed wall-clock horizons after
-- detection (1h/4h/1d/3d/7d): the close at the horizon (return_pct), and the
-- max favorable / adverse excursion (mfe_pct / mae_pct) within the window.
-- One row per (signal, horizon); upserted idempotently by a scheduled job.
CREATE TABLE signal_performance (
  id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  signal_id    BIGINT      NOT NULL REFERENCES pattern_signals(id) ON DELETE CASCADE,
  horizon      VARCHAR(4)  NOT NULL,            -- 1h | 4h | 1d | 3d | 7d
  price        NUMERIC(24,8),                   -- close at/after detected_at + horizon
  return_pct   NUMERIC(12,4),                   -- (price/detected_price - 1) * 100
  mfe_pct      NUMERIC(12,4),                   -- max favorable excursion within window
  mae_pct      NUMERIC(12,4),                   -- max adverse excursion within window
  evaluated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (signal_id, horizon)
);
CREATE INDEX ix_signal_performance_signal ON signal_performance(signal_id);
