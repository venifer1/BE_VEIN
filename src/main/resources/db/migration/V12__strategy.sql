CREATE TABLE strategies (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  name VARCHAR(80) NOT NULL,
  type VARCHAR(16) NOT NULL,        -- ABC|TOP|IMALOL|TRIANGLE
  market VARCHAR(16),               -- nullable (all)
  timeframe VARCHAR(8),             -- nullable
  params JSONB NOT NULL,            -- the full backtest RunRequest
  metrics JSONB,                    -- snapshot of last result metrics (nullable)
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_strategies_user_created ON strategies(user_id, created_at DESC);
