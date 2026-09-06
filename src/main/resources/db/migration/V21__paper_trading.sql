CREATE TABLE paper_accounts (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  base_currency VARCHAR(8) NOT NULL DEFAULT 'KRW',
  initial_balance NUMERIC(24,8) NOT NULL,
  cash_balance NUMERIC(24,8) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  simulation_run INT NOT NULL DEFAULT 1,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_paper_accounts_user_status
  ON paper_accounts(user_id, status);

CREATE TABLE paper_orders (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  account_id BIGINT NOT NULL REFERENCES paper_accounts(id) ON DELETE CASCADE,
  instrument_id BIGINT NOT NULL REFERENCES instruments(id),
  signal_id BIGINT REFERENCES pattern_signals(id) ON DELETE SET NULL,
  investment_type VARCHAR(12) NOT NULL DEFAULT 'SPOT',
  position_side VARCHAR(8),
  side VARCHAR(8) NOT NULL,
  type VARCHAR(12) NOT NULL,
  price NUMERIC(24,8),
  quantity NUMERIC(24,8) NOT NULL,
  leverage NUMERIC(10,4) NOT NULL DEFAULT 1,
  reduce_only BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(16) NOT NULL,
  fee_model_version VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_paper_orders_account_created
  ON paper_orders(account_id, created_at DESC);

CREATE TABLE paper_fills (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES paper_orders(id) ON DELETE CASCADE,
  price NUMERIC(24,8) NOT NULL,
  quantity NUMERIC(24,8) NOT NULL,
  fee NUMERIC(24,8) NOT NULL,
  slippage NUMERIC(24,8) NOT NULL DEFAULT 0,
  liquidity_source VARCHAR(24) NOT NULL,
  filled_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE paper_positions (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  account_id BIGINT NOT NULL REFERENCES paper_accounts(id) ON DELETE CASCADE,
  instrument_id BIGINT NOT NULL REFERENCES instruments(id),
  investment_type VARCHAR(12) NOT NULL DEFAULT 'SPOT',
  position_side VARCHAR(8),
  quantity NUMERIC(24,8) NOT NULL,
  avg_price NUMERIC(24,8) NOT NULL,
  margin NUMERIC(24,8) NOT NULL DEFAULT 0,
  leverage NUMERIC(10,4) NOT NULL DEFAULT 1,
  realized_pnl NUMERIC(24,8) NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(account_id, instrument_id, investment_type, position_side)
);

CREATE TABLE ledger_entries (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  account_id BIGINT NOT NULL REFERENCES paper_accounts(id) ON DELETE CASCADE,
  type VARCHAR(24) NOT NULL,
  amount NUMERIC(24,8) NOT NULL,
  currency VARCHAR(8) NOT NULL,
  reference_type VARCHAR(24),
  reference_id BIGINT,
  idempotency_key VARCHAR(96) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(idempotency_key)
);

CREATE INDEX ix_ledger_entries_account_created
  ON ledger_entries(account_id, created_at DESC);
