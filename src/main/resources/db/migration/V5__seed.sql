-- Internal tester (SUPER_ADMIN, APPROVED). password = 'vein1234'
-- BCrypt($2a$10$) hash verified against 'vein1234'.
INSERT INTO users (email, password_hash, role, status)
VALUES ('tester@vein.local',
        '$2a$10$dHxUYfnR0kc8RCJBtHR1E.43QHGYZiZLwuyiJ3Pe24vp7mlkL8gKy',
        'SUPER_ADMIN', 'APPROVED');

-- Default watchlist for the tester.
INSERT INTO watchlists (user_id, name)
SELECT id, 'default' FROM users WHERE lower(email) = 'tester@vein.local';

-- 20 representative Upbit KRW instruments.
INSERT INTO instruments (market, exchange, symbol, name, quote_currency, status) VALUES
  ('CRYPTO','UPBIT','KRW-BTC',  'Bitcoin',     'KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-ETH',  'Ethereum',    'KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-XRP',  'Ripple',      'KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-SOL',  'Solana',      'KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-DOGE', 'Dogecoin',    'KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-ADA',  'Cardano',     'KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-TRX',  'TRON',        'KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-AVAX', 'Avalanche',   'KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-LINK', 'Chainlink',   'KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-DOT',  'Polkadot',    'KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-MATIC','Polygon',     'KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-BCH',  'Bitcoin Cash','KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-LTC',  'Litecoin',    'KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-ETC',  'Ethereum Classic','KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-ATOM', 'Cosmos',      'KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-NEAR', 'NEAR Protocol','KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-SUI',  'Sui',         'KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-SHIB', 'Shiba Inu',   'KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-APT',  'Aptos',       'KRW','ACTIVE'),
  ('CRYPTO','UPBIT','KRW-SAND', 'The Sandbox', 'KRW','ACTIVE');

-- provider_symbols: identical symbol on Upbit.
INSERT INTO provider_symbols (instrument_id, provider, provider_symbol)
SELECT id, 'UPBIT', symbol FROM instruments WHERE exchange = 'UPBIT';
