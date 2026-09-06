-- =============================================================================
-- V13 — Remove the Triangle (삼각수렴) pattern feature entirely.
-- (Authored as V9 originally; renumbered to V13 to avoid a version collision
--  with V9__widen_tvl_change.sql which was already applied.)
-- The detector and SignalType.TRIANGLE were removed in code; purge existing
-- TRIANGLE signals and their dependents so no row references the dropped enum
-- value. type/rule_id are plain VARCHAR (no CHECK), so only data needs cleaning.
-- =============================================================================

-- Dependent rows first (FKs: signal_evidence, signal_performance, notifications).
DELETE FROM signal_evidence
 WHERE signal_id IN (SELECT id FROM pattern_signals WHERE type = 'TRIANGLE' OR rule_id = 'TRIANGLE');

DELETE FROM signal_performance
 WHERE signal_id IN (SELECT id FROM pattern_signals WHERE type = 'TRIANGLE' OR rule_id = 'TRIANGLE');

-- notifications.signal_id is nullable — unlink rather than delete the notification.
UPDATE notifications
   SET signal_id = NULL
 WHERE signal_id IN (SELECT id FROM pattern_signals WHERE type = 'TRIANGLE' OR rule_id = 'TRIANGLE');

DELETE FROM pattern_signals WHERE type = 'TRIANGLE' OR rule_id = 'TRIANGLE';

-- Re-document the now-reduced allowed sets.
COMMENT ON COLUMN pattern_signals.type    IS 'ABC | TOP | IMALOL';
COMMENT ON COLUMN pattern_signals.rule_id IS 'ABC | TOP | IMALOL (distinguishes detector within signal_key)';
COMMENT ON COLUMN pattern_signals.subtype IS 'NULL (Triangle SYMMETRIC|ASCENDING|DESCENDING removed)';
