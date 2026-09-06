CREATE TABLE explain_feedback (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  signal_id BIGINT NOT NULL REFERENCES pattern_signals(id) ON DELETE CASCADE,
  helpful BOOLEAN NOT NULL,
  reason VARCHAR(48),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, signal_id)
);

CREATE INDEX ix_explain_feedback_signal ON explain_feedback(signal_id);
