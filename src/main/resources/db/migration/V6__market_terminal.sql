-- =============================================================================
-- V6 — Market terminal layer (Phase 1)
-- Extends the crypto-only data model (V1~V5) to the full 4-market terminal:
--   * markets CRYPTO | US | KOSPI | KOSDAQ
--   * providers BINANCE | YFINANCE | PYKRX | COINGECKO (in addition to UPBIT)
--   * extended timeframe set (15m | 1h | 4h | 1d | 3d | 1w | 1M)
--   * market_indices (fear&greed, dominance, alt index, NASDAQ/KOSPI/KOSDAQ)
--   * kimchi_premium (Upbit KRW vs Binance USDT x USD/KRW)
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Widen enum-ish VARCHAR columns. instruments.market / provider_symbols.provider
--    are plain VARCHAR(16) (no DB-level CHECK) so US/KOSPI/KOSDAQ and the new
--    providers already fit. Document the allowed sets for readers / Phase 2-3.
-- -----------------------------------------------------------------------------
COMMENT ON COLUMN instruments.market IS 'CRYPTO | US | KOSPI | KOSDAQ';
COMMENT ON COLUMN provider_symbols.provider IS 'UPBIT | BINANCE | YFINANCE | PYKRX | COINGECKO';

-- -----------------------------------------------------------------------------
-- 2. Extend the candles timeframe CHECK to the full terminal set.
--    The original V1 column is VARCHAR(4) CHECK (timeframe IN ('1h','4h','1d')).
--    New codes ('15m','3d','1w','1M') all fit within length 4.
-- -----------------------------------------------------------------------------
ALTER TABLE candles DROP CONSTRAINT IF EXISTS candles_timeframe_check;
ALTER TABLE candles
  ADD CONSTRAINT candles_timeframe_check
  CHECK (timeframe IN ('15m','1h','4h','1d','3d','1w','1M'));

-- -----------------------------------------------------------------------------
-- 3. market_indices — terminal market gauges (terminal_tab.py).
--    index_key: FEAR_GREED | BTC_DOMINANCE | USDT_DOMINANCE | ALT_INDEX
--               | NASDAQ | KOSPI | KOSDAQ
--    classification: only set for FEAR_GREED (e.g. 'Greed'); NULL otherwise.
-- -----------------------------------------------------------------------------
CREATE TABLE market_indices (
  id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  index_key      VARCHAR(24)  NOT NULL,
  value          NUMERIC(24,8),
  classification VARCHAR(32),
  collected_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
COMMENT ON COLUMN market_indices.index_key IS
  'FEAR_GREED | BTC_DOMINANCE | USDT_DOMINANCE | ALT_INDEX | NASDAQ | KOSPI | KOSDAQ';
-- Latest-per-key lookups.
CREATE INDEX ix_market_indices_key_time ON market_indices(index_key, collected_at DESC);

-- -----------------------------------------------------------------------------
-- 4. kimchi_premium — Upbit(KRW) vs Binance(USDT) x USD/KRW snapshot per coin.
--    premium_pct = (upbit_price / (binance_price * usdkrw) - 1) * 100
-- -----------------------------------------------------------------------------
CREATE TABLE kimchi_premium (
  id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  instrument_id BIGINT       NOT NULL REFERENCES instruments(id),
  upbit_price   NUMERIC(24,8) NOT NULL,
  binance_price NUMERIC(24,8) NOT NULL,
  usdkrw        NUMERIC(18,6) NOT NULL,
  premium_pct   NUMERIC(12,4) NOT NULL,
  collected_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_kimchi_instrument_time ON kimchi_premium(instrument_id, collected_at DESC);
CREATE INDEX ix_kimchi_collected_at ON kimchi_premium(collected_at DESC);

-- -----------------------------------------------------------------------------
-- 5. Map existing crypto instruments to BINANCE (KRW-XXX -> XXXUSDT) for the
--    kimchi premium join. Skip stablecoins / KRW that lack a USDT pair upstream.
-- -----------------------------------------------------------------------------
INSERT INTO provider_symbols (instrument_id, provider, provider_symbol)
SELECT i.id, 'BINANCE',
       replace(i.symbol, 'KRW-', '') || 'USDT'
FROM instruments i
WHERE i.exchange = 'UPBIT'
  AND i.market = 'CRYPTO';

-- -----------------------------------------------------------------------------
-- 6. Seed US equities (S&P500 top ~30, NDX overlap) + a NASDAQ-only handful.
--    exchange = 'NYSE'/'NASDAQ' is informational; provider = YFINANCE (stubbed).
--    quote_currency USD. provider_symbol == ticker.
-- -----------------------------------------------------------------------------
INSERT INTO instruments (market, exchange, symbol, name, quote_currency, status) VALUES
  ('US','NASDAQ','AAPL','Apple','USD','ACTIVE'),
  ('US','NASDAQ','MSFT','Microsoft','USD','ACTIVE'),
  ('US','NASDAQ','NVDA','NVIDIA','USD','ACTIVE'),
  ('US','NASDAQ','AMZN','Amazon','USD','ACTIVE'),
  ('US','NASDAQ','GOOGL','Alphabet A','USD','ACTIVE'),
  ('US','NASDAQ','META','Meta Platforms','USD','ACTIVE'),
  ('US','NASDAQ','TSLA','Tesla','USD','ACTIVE'),
  ('US','NYSE','BRK-B','Berkshire Hathaway B','USD','ACTIVE'),
  ('US','NYSE','JPM','JPMorgan Chase','USD','ACTIVE'),
  ('US','NYSE','UNH','UnitedHealth','USD','ACTIVE'),
  ('US','NYSE','V','Visa','USD','ACTIVE'),
  ('US','NYSE','XOM','Exxon Mobil','USD','ACTIVE'),
  ('US','NYSE','LLY','Eli Lilly','USD','ACTIVE'),
  ('US','NYSE','JNJ','Johnson & Johnson','USD','ACTIVE'),
  ('US','NYSE','MA','Mastercard','USD','ACTIVE'),
  ('US','NASDAQ','AVGO','Broadcom','USD','ACTIVE'),
  ('US','NYSE','PG','Procter & Gamble','USD','ACTIVE'),
  ('US','NYSE','HD','Home Depot','USD','ACTIVE'),
  ('US','NASDAQ','COST','Costco','USD','ACTIVE'),
  ('US','NYSE','MRK','Merck','USD','ACTIVE'),
  ('US','NYSE','ABBV','AbbVie','USD','ACTIVE'),
  ('US','NYSE','CVX','Chevron','USD','ACTIVE'),
  ('US','NASDAQ','PEP','PepsiCo','USD','ACTIVE'),
  ('US','NASDAQ','KO','Coca-Cola','USD','ACTIVE'),
  ('US','NYSE','WMT','Walmart','USD','ACTIVE'),
  ('US','NYSE','BAC','Bank of America','USD','ACTIVE'),
  ('US','NYSE','CRM','Salesforce','USD','ACTIVE'),
  ('US','NASDAQ','NFLX','Netflix','USD','ACTIVE'),
  ('US','NASDAQ','AMD','Advanced Micro Devices','USD','ACTIVE'),
  ('US','NYSE','TMO','Thermo Fisher','USD','ACTIVE'),
  -- NDX-only extras
  ('US','NASDAQ','GOOG','Alphabet C','USD','ACTIVE'),
  ('US','NASDAQ','ADBE','Adobe','USD','ACTIVE'),
  ('US','NASDAQ','CSCO','Cisco','USD','ACTIVE'),
  ('US','NASDAQ','INTU','Intuit','USD','ACTIVE'),
  ('US','NASDAQ','QCOM','Qualcomm','USD','ACTIVE');

-- -----------------------------------------------------------------------------
-- 7. Seed KOSPI (KOSPI_PRESET) — yfinance-style '.KS' tickers, KRW.
-- -----------------------------------------------------------------------------
INSERT INTO instruments (market, exchange, symbol, name, quote_currency, status) VALUES
  ('KOSPI','KRX','005930.KS','삼성전자','KRW','ACTIVE'),
  ('KOSPI','KRX','000660.KS','SK하이닉스','KRW','ACTIVE'),
  ('KOSPI','KRX','035420.KS','NAVER','KRW','ACTIVE'),
  ('KOSPI','KRX','035720.KS','카카오','KRW','ACTIVE'),
  ('KOSPI','KRX','051910.KS','LG화학','KRW','ACTIVE'),
  ('KOSPI','KRX','005380.KS','현대차','KRW','ACTIVE'),
  ('KOSPI','KRX','000270.KS','기아','KRW','ACTIVE'),
  ('KOSPI','KRX','068270.KS','셀트리온','KRW','ACTIVE'),
  ('KOSPI','KRX','006400.KS','삼성SDI','KRW','ACTIVE'),
  ('KOSPI','KRX','105560.KS','KB금융','KRW','ACTIVE'),
  ('KOSPI','KRX','207940.KS','삼성바이오로직스','KRW','ACTIVE'),
  ('KOSPI','KRX','012330.KS','현대모비스','KRW','ACTIVE'),
  ('KOSPI','KRX','003550.KS','LG','KRW','ACTIVE'),
  ('KOSPI','KRX','028260.KS','삼성물산','KRW','ACTIVE'),
  ('KOSPI','KRX','066570.KS','LG전자','KRW','ACTIVE'),
  ('KOSPI','KRX','017670.KS','SK텔레콤','KRW','ACTIVE'),
  ('KOSPI','KRX','034730.KS','SK','KRW','ACTIVE'),
  ('KOSPI','KRX','096770.KS','SK이노베이션','KRW','ACTIVE'),
  ('KOSPI','KRX','010130.KS','고려아연','KRW','ACTIVE'),
  ('KOSPI','KRX','018260.KS','삼성에스디에스','KRW','ACTIVE');

-- -----------------------------------------------------------------------------
-- 8. Seed KOSDAQ (KOSDAQ_PRESET) — '.KQ' tickers, KRW.
-- -----------------------------------------------------------------------------
INSERT INTO instruments (market, exchange, symbol, name, quote_currency, status) VALUES
  ('KOSDAQ','KRX','247540.KQ','에코프로비엠','KRW','ACTIVE'),
  ('KOSDAQ','KRX','091990.KQ','셀트리온헬스케어','KRW','ACTIVE'),
  ('KOSDAQ','KRX','005290.KQ','동진쎄미켐','KRW','ACTIVE'),
  ('KOSDAQ','KRX','263750.KQ','펄어비스','KRW','ACTIVE'),
  ('KOSDAQ','KRX','293490.KQ','카카오게임즈','KRW','ACTIVE'),
  ('KOSDAQ','KRX','196170.KQ','알테오젠','KRW','ACTIVE'),
  ('KOSDAQ','KRX','068760.KQ','셀트리온제약','KRW','ACTIVE'),
  ('KOSDAQ','KRX','066970.KQ','엘앤에프','KRW','ACTIVE'),
  ('KOSDAQ','KRX','357780.KQ','솔브레인','KRW','ACTIVE'),
  ('KOSDAQ','KRX','086520.KQ','에코프로','KRW','ACTIVE');

-- -----------------------------------------------------------------------------
-- 9. provider_symbols for the new equities: YFINANCE for US + KOSPI/KOSDAQ
--    (yfinance ticker == symbol), plus PYKRX for KR (numeric code without suffix).
-- -----------------------------------------------------------------------------
INSERT INTO provider_symbols (instrument_id, provider, provider_symbol)
SELECT id, 'YFINANCE', symbol
FROM instruments
WHERE market IN ('US','KOSPI','KOSDAQ');

INSERT INTO provider_symbols (instrument_id, provider, provider_symbol)
SELECT id, 'PYKRX', split_part(symbol, '.', 1)
FROM instruments
WHERE market IN ('KOSPI','KOSDAQ');
