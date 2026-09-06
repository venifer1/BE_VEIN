# VEIN Round 26

Date: 2026-06-14

## Basic Admin Dashboard

- Added `GET /api/v1/admin/overview`.
- Returns authenticated operational counts for:
  - users by status
  - alert rules enabled/disabled
  - notifications unread and by status
  - Explain feedback helpful rate
  - saved scanner rules enabled/disabled
  - pattern signals by status
  - instruments by market
- Added `/admin` frontend page with 30-second refresh and manual refresh.
- Added an Admin bottom-tab entry and included `/admin` in Chrome smoke coverage.

## Verification

- Backend `compileJava`: passed.
- Backend `compileTestJava`: passed.
- Frontend `typecheck`: passed.
- Frontend production `build`: passed.
- Live `GET /api/v1/admin/overview`: passed.
- Chrome smoke: 0 errors.

## Running Servers

- Backend: `http://localhost:8080`
- Frontend: `http://localhost:3000`
