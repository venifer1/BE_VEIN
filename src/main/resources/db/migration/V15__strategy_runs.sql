CREATE TABLE strategy_runs (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  strategy_id BIGINT NOT NULL REFERENCES strategies(id) ON DELETE CASCADE,
  metrics JSONB NOT NULL,             -- snapshot of backtest metrics at run time
  trade_count INT,                    -- denormalized for quick history charts
  total_return_pct NUMERIC(14,4),     -- denormalized
  win_rate NUMERIC(7,2),              -- denormalized
  run_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_strategy_runs_strategy_run_at ON strategy_runs(strategy_id, run_at DESC);
