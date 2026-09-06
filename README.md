# VEIN Backend (Internal Alpha MVP)

Spring Boot 3.3.x / Java 21 / PostgreSQL 16 / Redis 7 / Flyway. Package root `com.vein`.

Implements the first vertical slice of the VEIN trading-intelligence internal alpha:
candle ingestion (Upbit) → ABC / Triangle pattern detection → signal storage → list/detail
API → watchlist → alerts → in-app notifications, behind JWT auth, per the authoritative
spec (`docs/MVP실행기준표_추출.txt`, appendices A–I) and the shared API contract
(`docs/API_CONTRACT.md`).

## Prerequisites
- JDK 21 (the Gradle toolchain targets Java 21; Gradle will provision/locate it).
- Docker (for PostgreSQL + Redis).

## Run

```bash
# 0. Create your local env file (NOT committed) and fill in the blanks
cp .env.example .env
#    - DB_PASSWORD : any value
#    - JWT_SECRET  : openssl rand -base64 48   (required, >= 32 bytes)

# 1. Start infra (postgres:16 + redis:7). docker compose reads .env automatically.
docker compose up -d

# 2. Run the app (Flyway applies V1..V21 on startup; hibernate ddl-auto=validate)
#    docker compose reads .env automatically, but Spring Boot does NOT —
#    export it into the shell first so the ${...} placeholders resolve.
set -a && . ./.env && set +a && ./gradlew bootRun
```

PowerShell equivalent for step 2:

```powershell
Get-Content .env | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object {
  $k, $v = $_ -split '=', 2
  Set-Item -Path "env:$($k.Trim())" -Value $v.Trim()
}
.\gradlew bootRun
```

The app listens on `http://localhost:8080`.

### Environment
**No secret has a hardcoded default.** `.env.example` is the full template; copy it to `.env`
(git-ignored) and fill it in. Anything marked *required* has **no fallback** — the app fails
fast at startup rather than booting with a known-public value.

| Var | Required | Notes |
|---|---|---|
| `DB_URL` | no (`jdbc:postgresql://localhost:5432/vein`) | JDBC URL |
| `DB_NAME` / `DB_USERNAME` | for `docker compose` | container + app must agree |
| `DB_PASSWORD` | **yes** | PostgreSQL password; shared by compose and the app |
| `JWT_SECRET` | **yes** | HS256 signing key, >= 32 bytes. `openssl rand -base64 48` |
| `JWT_ACCESS_TTL` | no (`900`) | access token TTL (s) |
| `JWT_REFRESH_TTL` | no (`1209600`) | refresh token TTL (s) |
| `REDIS_HOST` | no (`localhost`) | |
| `WEB_PUSH_PUBLIC_KEY` / `WEB_PUSH_PRIVATE_KEY` | no | VAPID pair. **Blank = web push silently disabled**, everything else works. Generate with `npx web-push generate-vapid-keys` |
| `WEB_PUSH_SUBJECT` | no (`mailto:admin@vein.local`) | VAPID contact, not a secret |
| `SIDECAR_URL` | no (`http://localhost:8099`) | Python equity/news sidecar; falls back to synthetic stubs when down |
| `INGESTION_ENABLED` | no (`false`) | `true` enables the scheduled pollers + scanners |
| `LIQUIDATION_ENABLED` / `SCALP_ENABLED` | no (`false`) | heavy feeds, opt-in |

Upbit market/candle endpoints are public — **no API key required** (부록 G-3).

> **Never commit `.env`.** `.gitignore` covers `.env` and `.env.*` while keeping `.env.example`.

## Seeded account (V5__seed.sql)
`V5__seed.sql` creates one internal tester (`role SUPER_ADMIN`, `status APPROVED` — only
APPROVED accounts may log in) plus 20 representative KRW instruments
(KRW-BTC, KRW-ETH, KRW-XRP, KRW-SOL, KRW-DOGE + 15 more) with matching `provider_symbols`
and a default watchlist.

The password originally shipped in `V5__seed.sql` was a weak, publicly-committed value.
**`V22__rotate_seed_admin_credentials.sql` replaces that hash** with the BCrypt of a 20-char
random password; `V5` itself is left untouched so Flyway checksums stay valid on existing
databases. The new plaintext lives **only** in your git-ignored `.env` as `SEED_ADMIN_PASSWORD`.

> ⚠️ If you deployed this anywhere before V22 was applied, treat that instance's seed account as
> compromised — reset it there directly rather than relying on this migration.

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"email\": \"tester@vein.local\", \"password\": \"$SEED_ADMIN_PASSWORD\"}"
```

## API docs (Swagger)
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Public endpoints: `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/system/status`,
`/actuator/health`, swagger. Everything else requires `Authorization: Bearer <access_token>`.

## Test

```bash
./gradlew test
```

- **Golden tests** (`AbcGoldenTest`, `TriangleGoldenTest`) are pure-Java and need no DB. They
  load every `*.json` from `golden_fixtures/` (skipping `_index.json`), run the detectors on
  `input_candles`, and assert against `expected` within `tolerance` (pivot index exact,
  price relative error ≤ `price_rel`, score ± `score`). Fixtures are copied into
  `src/test/resources/golden_fixtures/` by the Gradle `copyGoldenFixtures` task.
  The provided `krw_btc_1d.json` expects 0 ABC and 0 Triangle patterns — the detectors return 0.
- **TOP / IMALOL tests** (`TopGoldenTest`, `ImalolGoldenTest`) are pure-Java unit tests over
  deterministic synthetic candle series (`SyntheticBars`): each exercises one known pattern and
  one known non-pattern, asserting pivot ordering / box invariants / projected-target geometry.
  **TODO (golden fixtures):** the repo has no TOP/IMALOL fixtures yet. Extend the legacy
  `tools/extract_golden_fixtures.py` to also run `core/top_detector.detect_top_patterns` and the
  `ui/tab_imalol` rule (`_build_pattern_row`) over the same candle inputs and emit
  `expected.top` / `expected.imalol` blocks (pivots/c_target for TOP; match-box bounds +
  bollinger band for IMALOL). Once committed under `golden_fixtures/`, swap these unit tests to
  fixture-driven golden tests like `AbcGoldenTest`.
- Repository/controller integration tests use Testcontainers (require Docker).

## Detectors are pure
`com.vein.pattern.{core,abc,top,imalol,triangle}` take `List<Bar>` in and return result objects
with no DB/network/current-time/future-candle dependency, so they are deterministic and
golden-testable. TOP inverts candles (negate O/H/L/C, swap high↔low), reuses the ABC detector,
then re-inverts (`top-java-1.0.0`); IMALOL (`imalol-java-1.0.0`) reuses `Indicators` for
Bollinger(20,2.0) + MA20 and merges all matched 2-candle boxes per instrument+timeframe.
Parameters match the legacy Python config (부록 E / 표 18·19): `LOCAL_WIN=5`, `SEARCH_WINDOW=100`,
ABC `MIN_A_DROP_PCT=0.20`, `MIN_B_RETRACE_PCT=0.236`, `MAX_B_RETRACE_PCT=0.886`,
`MIN_0_PROMINENCE=0.10`, `STRICT_WAVE=true`, `MAX_PATTERNS=3`; triangle `min_seg_len=40`,
`min_contraction=0.25` (width_ratio ≤ 0.75), etc.

## Phase 1 — Market terminal layer (4 markets)

Extends the crypto-only slice to the full terminal across `CRYPTO | US | KOSPI | KOSDAQ`.

### New endpoints
- `GET /api/v1/market/indices` — Fear&Greed (+classification), BTC/USDT dominance, alt index,
  NASDAQ/KOSPI/KOSDAQ index values. Serves cached `market_indices` snapshots; refreshed ~60s by
  `TerminalScheduler` (gated by `INGESTION_ENABLED`). Freshness in `meta`.
- `GET /api/v1/market/kimchi-premium?sort=premium_desc|premium_asc` — Upbit(KRW) vs Binance(USDT)
  × USD/KRW. `premium_pct = (upbit / (binance × usdkrw) − 1) × 100`. BTC/ETH/XRP pinned on top.
  Cached in `kimchi_premium`.
- `GET /api/v1/instruments?q=&market=&status=` — unified 4-market search. `market` omitted = all
  markets. Korean 초성 search supported (`q=ㅅㅈ` matches 삼성전자), else case-insensitive substring.
  Capped at 30 rows. `GET /api/v1/instruments/{id}` returns detail.
- `GET /api/v1/instruments/{id}/candles?timeframe=` — extended timeframe set per market; invalid
  (market, timeframe) combos return `UNSUPPORTED_TIMEFRAME`.
- `GET /api/v1/instruments/{id}/indicators?timeframe=` — RSI(14), MA(5/20/60/120), Bollinger(20,2),
  MACD(12,26,9). Pure math in `com.vein.indicator.Indicators` (reused by Phase 2/3).

### Timeframes
- CRYPTO: `15m | 1h | 4h | 1d | 3d | 1w | 1M` (3d synthesized from daily candles per
  legacy `data/_build_3day.py`; 1w/1M via Upbit weeks/months endpoints).
- US/KOSPI/KOSDAQ (equities): `1d | 3d | 1w`.

### Providers (real vs stub)
| Provider | Code | Status | Source |
|---|---|---|---|
| Upbit | `UPBIT` | **real** | REST candles + tickers (KRW) |
| Binance | `BINANCE` | **real** | REST klines + ticker/price (USDT) |
| CoinGecko | `COINGECKO` | **real** | `/global` dominance (429 Retry-After aware) |
| alternative.me | `ALTERNATIVE_ME` | **real** | `/fng/` fear&greed |
| Exchange rate | `EXCHANGE_RATE` | **real** | open.er-api.com → exchangerate-api.com fallback |
| US equities | `YFINANCE` | **STUB** | deterministic synthetic candles |
| KR equities | `PYKRX` | **STUB** | deterministic synthetic candles |

All real providers are keyless public APIs. Each provider sits behind a Resilience4j
ratelimiter + retry + circuit breaker; provider DTOs never leave the provider package
(normalized to `RawCandle`/domain types).

> **⚠ Equity data is stubbed.** yfinance (US) and pykrx (KR) are Python-only libraries with no
> Java equivalent, so `UsEquityProvider`/`KrEquityProvider` return deterministic synthetic daily
> OHLCV (clearly not real market data), and NASDAQ/KOSPI/KOSDAQ index values in `/market/indices`
> are synthetic stand-ins. The contract and UI work today against these stubs.
>
> **TODO (follow-up integration): replace with a real yfinance/pykrx bridge** — e.g. a sidecar
> Python microservice exposing OHLCV over HTTP (`GET /candles?ticker=AAPL&interval=1d`), or a
> commercial US/KR market-data vendor. Search the codebase for
> `// TODO: replace with real yfinance/pykrx bridge`.

### Migration V6 (`V6__market_terminal.sql`)
- Relaxes the `candles.timeframe` CHECK to the full set (`15m,1h,4h,1d,3d,1w,1M`).
- Adds `market_indices` and `kimchi_premium` tables.
- Documents `instruments.market` (`CRYPTO|US|KOSPI|KOSDAQ`) and `provider_symbols.provider`
  (`UPBIT|BINANCE|YFINANCE|PYKRX|COINGECKO`).
- Maps existing crypto instruments to `BINANCE` (`KRW-XXX → XXXUSDT`).
- Seeds ~35 US equities (S&P500 top 30 + NDX overlap), 20 KOSPI, 10 KOSDAQ, each with
  YFINANCE (and PYKRX for KR) `provider_symbols`. The V5 crypto seed is preserved.

## Conventions
- All times UTC / `TIMESTAMPTZ`; serialized ISO-8601 with `Z`.
- Money/quantity as `BigDecimal`, serialized as JSON strings.
- JSON keys `snake_case` (global Jackson `SNAKE_CASE`).
- Responses use the common envelope: `{data, meta}` for success, `{error:{code,message,trace_id,field_errors}}` for errors.
- Lists are cursor-paginated, newest-first (cursor = base64 of `detected_at|id`).
- Flyway owns the schema (`V1__market` … `V5__seed`); JPA runs `ddl-auto=validate`.

## Gradle wrapper
The Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`,
`gradle-wrapper.properties` → Gradle 8.10) is committed and functional; run `./gradlew` directly.
