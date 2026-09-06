CREATE TABLE alerts (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  instrument_id BIGINT NOT NULL REFERENCES instruments(id),
  signal_type VARCHAR(16) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  cooldown_sec INT NOT NULL DEFAULT 3600 CHECK (cooldown_sec > 0),
  last_triggered_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_alerts_user_enabled ON alerts(user_id, enabled);

CREATE TABLE notifications (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id   BIGINT NOT NULL REFERENCES users(id),
  signal_id BIGINT REFERENCES pattern_signals(id),
  alert_id  BIGINT REFERENCES alerts(id),
  status VARCHAR(16) NOT NULL DEFAULT 'CREATED',  -- CREATED|SENT|READ|EXPIRED|FAILED
  title VARCHAR(120) NOT NULL,
  body  VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  read_at TIMESTAMPTZ,
  UNIQUE (user_id, alert_id, signal_id)           -- 중복 방지
);
CREATE INDEX ix_notif_user_created ON notifications(user_id, created_at DESC);

CREATE TABLE delivery_attempts (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  notification_id BIGINT NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  channel VARCHAR(16) NOT NULL DEFAULT 'IN_APP',
  status  VARCHAR(16) NOT NULL,                   -- OK|RETRY|FAILED
  attempt_no INT NOT NULL DEFAULT 1,
  error_code VARCHAR(48),
  attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ShedLock table for distributed scheduler locking (부록 G-1, Redis provider used at runtime;
-- table kept for portability if a JDBC lock provider is ever swapped in).
CREATE TABLE shedlock (
  name VARCHAR(64) NOT NULL PRIMARY KEY,
  lock_until TIMESTAMPTZ NOT NULL,
  locked_at TIMESTAMPTZ NOT NULL,
  locked_by VARCHAR(255) NOT NULL
);
