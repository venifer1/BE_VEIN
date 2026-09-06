-- =============================================================================
-- V7 — Pattern engine extension (Phase 2)
-- Extends the alpha ABC/TRIANGLE signal model (V2) to all 4 legacy detectors:
--   ABC | TOP | IMALOL | TRIANGLE  (single pattern_signals table, rule_id distinguishes)
-- Adds the card/detail fields the scanner contract (§4) requires:
--   subtype       — TRIANGLE SYMMETRIC|ASCENDING|DESCENDING (NULL for ABC/TOP/IMALOL)
--   c_target      — C 예상가 (ABC/TOP); IMALOL stores its projected close here too
--   current_price — last close at detection (card 현재가 + near_only computation)
--   market        — CRYPTO|US|KOSPI|KOSDAQ (denormalized for fast scanner filtering)
-- evidence_type is widened to include BOLL_UPPER|BOLL_MID|BOLL_LOWER|MATCH_BOX.
-- Columns stay plain VARCHAR (no DB-level CHECK, like V6); allowed sets documented
-- via COMMENT so Phase 3 / readers see the contract.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. New signal columns. type/rule_id already VARCHAR(16/24); just document the
--    widened allowed set (ABC|TOP|IMALOL|TRIANGLE).
-- -----------------------------------------------------------------------------
ALTER TABLE pattern_signals ADD COLUMN subtype       VARCHAR(16);
ALTER TABLE pattern_signals ADD COLUMN c_target      NUMERIC(24,8);
ALTER TABLE pattern_signals ADD COLUMN current_price NUMERIC(24,8);
ALTER TABLE pattern_signals ADD COLUMN market        VARCHAR(16);

COMMENT ON COLUMN pattern_signals.type IS 'ABC | TOP | IMALOL | TRIANGLE';
COMMENT ON COLUMN pattern_signals.rule_id IS 'ABC | TOP | IMALOL | TRIANGLE (distinguishes detector within signal_key)';
COMMENT ON COLUMN pattern_signals.subtype IS 'TRIANGLE: SYMMETRIC | ASCENDING | DESCENDING; NULL otherwise';
COMMENT ON COLUMN pattern_signals.c_target IS 'C 예상가 for ABC/TOP (and IMALOL projected close); NULL for TRIANGLE';
COMMENT ON COLUMN pattern_signals.current_price IS 'Last final close at detection (card 현재가 / near_only basis)';
COMMENT ON COLUMN pattern_signals.market IS 'CRYPTO | US | KOSPI | KOSDAQ (denormalized from instruments.market)';

-- Backfill market on any pre-existing alpha rows from the instruments table.
UPDATE pattern_signals s
SET market = i.market
FROM instruments i
WHERE s.instrument_id = i.id
  AND s.market IS NULL;

-- -----------------------------------------------------------------------------
-- 2. Widen evidence_type allowed set (Bollinger + match box for IMALOL).
--    Column is VARCHAR(24) (no CHECK) — BOLL_*/MATCH_BOX already fit; document.
-- -----------------------------------------------------------------------------
COMMENT ON COLUMN signal_evidence.evidence_type IS
  'PIVOT_0 | PIVOT_A | PIVOT_B | C_TARGET | TREND_UPPER | TREND_LOWER | BOLL_UPPER | BOLL_MID | BOLL_LOWER | MATCH_BOX';

-- -----------------------------------------------------------------------------
-- 3. Scanner filters by market (with status/detected_at). Index it.
-- -----------------------------------------------------------------------------
CREATE INDEX ix_signals_market_status_det ON pattern_signals(market, status, detected_at DESC);
