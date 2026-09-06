-- V9: widen tvl_snapshots percentage-change columns.
-- DefiLlama can report explosive 1d/7d % changes for micro-cap protocols
-- (e.g. >100,000,000%), overflowing NUMERIC(12,4). Widen generously.
ALTER TABLE tvl_snapshots ALTER COLUMN change_1d TYPE NUMERIC(24,6);
ALTER TABLE tvl_snapshots ALTER COLUMN change_7d TYPE NUMERIC(24,6);
