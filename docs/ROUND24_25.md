# VEIN Round 24-25

Date: 2026-06-14

## Round 24 - Saved-rule frequency simulation

- `POST /api/v1/scanner/rules/{id}/simulate`
- Runs a saved condition rule against the current market snapshot.
- Returns evaluated/matched counts, match rate, `LOW/MEDIUM/HIGH`, and up to five samples.
- The value is a current-snapshot estimate, not a historical frequency backtest.
- Live check: CRYPTO 1d RSI<30 evaluated 257, matched 42, rate 16.34%, grade HIGH.

## Round 25 - Liquidation aggregation and spike detection

- `GET /api/v1/liquidations/summary`
- Returns 1h/24h count, total, long/short split, maximum event, and `partial` coverage.
- Compares the latest five minutes with the prior 55-minute five-minute baseline.
- Spike levels are `NORMAL`, `MEDIUM`, and `HIGH`.
- Data > Liquidations now shows time-window cards and a spike badge.
- Aggregation uses the latest 500 in-memory events. `partial=true` means the buffer does not cover the full window.

## Verification

- Backend `compileJava` and `compileTestJava`: passed.
- `LiquidationServiceTest`: 3 passed from an ASCII-only path.
- Frontend `typecheck` and production `build`: passed.
- Live liquidation summary: passed.
- Saved-rule save, simulate, and delete: passed.
- Chrome smoke: 0 errors.
