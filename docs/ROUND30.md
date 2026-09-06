# VEIN Round 30

Date: 2026-06-16

## Liquidation Spike Alerts

- Added liquidation spike notifications on top of the existing Binance USD-M force-order stream.
- Existing 1h/24h aggregation remains the source for spike detection.
- `MEDIUM` and `HIGH` spike levels now fan out in-app/web-push notifications to approved users.
- Added alert cooldown:
  - `vein.liquidation.spike-alert-cooldown-sec`
  - env override `LIQUIDATION_SPIKE_ALERT_COOLDOWN_SEC`
  - default `1800` seconds
- Alert logic resets after the spike level returns to `NORMAL`.
- Escalation from `MEDIUM` to `HIGH` can notify before cooldown expires.
- Notification creation failures are isolated so the liquidation stream keeps accepting events.

## Verification

- Backend `compileJava`: passed.
- Backend `compileTestJava`: passed.
- Frontend `typecheck`: passed.
- Targeted Gradle test execution is still blocked by the repository's known non-ASCII path class-loading issue. Classes compile, but Gradle test discovery reports `ClassNotFoundException` for existing test classes under this workspace path.
