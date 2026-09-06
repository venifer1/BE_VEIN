# VEIN Round 29

Date: 2026-06-16

## Saved Scanner Rule Alerts

- Added V20 `scanner_rule_runs` and `scanner_rule_matches`.
- Added scheduled evaluation for enabled saved scanner rules.
- New matches create in-app/web-push notifications through the existing notification pipeline.
- Match state is episode-based: an instrument notifies when it first appears, stays quiet while still matched, and can notify again after it clears and reappears.
- Added rule enable/disable update: `PATCH /api/v1/scanner/rules/{id}`.
- Added rule run history: `GET /api/v1/scanner/rules/{id}/history`.
- Scanner rule list now includes `latest_run`.

## Admin And UI

- Admin overview now includes active scanner matches and scanner runs in the last 24 hours.
- Scanner saved-rule rows now show latest scheduled run rate and notification count.
- Saved scanner rules can be toggled on/off from the scanner screen.
- Mock mode supports scanner rule patch/history and the new admin scanner fields.

## Verification

- Backend `compileJava`: passed.
- Backend `compileTestJava`: passed.
- Frontend `typecheck`: passed.
- Frontend production `build`: passed.
- Chrome smoke was not run successfully because both local servers were down; `localhost:3000` refused the connection.
- Attempting to start the frontend with `Start-Process` failed in the current PowerShell environment due duplicate `Path/PATH` keys.
