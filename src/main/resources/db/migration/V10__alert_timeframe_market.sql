-- V10: alerts gain timeframe + market so the rule row can show them and
-- alerts can be timeframe-scoped (client sends both; previously dropped).
ALTER TABLE alerts ADD COLUMN timeframe VARCHAR(4);
ALTER TABLE alerts ADD COLUMN market    VARCHAR(16);
