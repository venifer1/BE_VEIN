CREATE TABLE scanner_rule_runs (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rule_id BIGINT NOT NULL REFERENCES scanner_rules(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  evaluated_count INT NOT NULL,
  matched_count INT NOT NULL,
  match_rate NUMERIC(10, 4) NOT NULL,
  frequency_grade VARCHAR(16) NOT NULL,
  notification_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_scanner_rule_runs_rule_created
  ON scanner_rule_runs(rule_id, created_at DESC);

CREATE TABLE scanner_rule_matches (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rule_id BIGINT NOT NULL REFERENCES scanner_rules(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  instrument_id BIGINT NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  first_matched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_matched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_notified_at TIMESTAMPTZ,
  UNIQUE(rule_id, instrument_id)
);

CREATE INDEX ix_scanner_rule_matches_rule_active
  ON scanner_rule_matches(rule_id, active);
