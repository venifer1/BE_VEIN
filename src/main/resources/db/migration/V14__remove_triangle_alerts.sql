-- =============================================================================
-- V14 — Triangle removal cleanup for alerts (companion to V13).
-- Alert.signalType maps to the SignalType enum, which no longer has TRIANGLE,
-- so any leftover alert with signal_type='TRIANGLE' fails to load (500 on read).
-- Unlink dependent notifications first (FK notifications.alert_id), then delete.
-- =============================================================================

UPDATE notifications
   SET alert_id = NULL
 WHERE alert_id IN (SELECT id FROM alerts WHERE signal_type = 'TRIANGLE');

DELETE FROM alerts WHERE signal_type = 'TRIANGLE';
