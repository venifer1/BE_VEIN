CREATE TABLE scanner_rules (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(80) NOT NULL,
  market VARCHAR(16) NOT NULL,
  timeframe VARCHAR(8) NOT NULL,
  logic VARCHAR(4) NOT NULL,
  conditions JSONB NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_scanner_rules_user_created
  ON scanner_rules(user_id, created_at DESC);
