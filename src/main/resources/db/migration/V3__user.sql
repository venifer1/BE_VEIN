CREATE TABLE users (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email         VARCHAR(254) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,            -- BCrypt
  role          VARCHAR(16)  NOT NULL DEFAULT 'TESTER',
  status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',  -- PENDING|APPROVED|LOCKED
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_email ON users(lower(email));

CREATE TABLE refresh_tokens (
  id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id    BIGINT NOT NULL REFERENCES users(id),
  token_hash VARCHAR(100) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ
);
CREATE INDEX ix_refresh_user_exp ON refresh_tokens(user_id, expires_at);

CREATE TABLE watchlists (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  name VARCHAR(40) NOT NULL DEFAULT 'default',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, name)
);
CREATE TABLE watchlist_items (
  watchlist_id  BIGINT NOT NULL REFERENCES watchlists(id) ON DELETE CASCADE,
  instrument_id BIGINT NOT NULL REFERENCES instruments(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (watchlist_id, instrument_id)
);
CREATE INDEX ix_watchitem_instrument ON watchlist_items(instrument_id);

CREATE TABLE audit_logs (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  actor_id BIGINT, action VARCHAR(48) NOT NULL, target VARCHAR(64),
  ip VARCHAR(45), detail JSONB, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_actor_created ON audit_logs(actor_id, created_at DESC);
