# VEIN Round 27

Date: 2026-06-14

## Background Web Push

- Added V19 `web_push_subscriptions`.
- Added authenticated web-push endpoints:
  - `GET /api/v1/notifications/web-push/config`
  - `PUT /api/v1/notifications/web-push/subscription`
  - `DELETE /api/v1/notifications/web-push/subscription`
- Added server-side Web Push delivery using VAPID.
- Existing in-app notifications remain the source of truth.
- Browser Push API is preferred when supported; the previous foreground polling notification path remains as fallback.
- Added service worker `push` handling.
- Settings notification toggle now registers/unregisters browser push subscriptions.

## Configuration

- Defaults are dev-only and can be overridden:
  - `WEB_PUSH_PUBLIC_KEY`
  - `WEB_PUSH_PRIVATE_KEY`
  - `WEB_PUSH_SUBJECT`
  - `WEB_PUSH_TTL_SEC`

## Verification

- Backend `compileJava`: passed.
- Backend `compileTestJava`: passed.
- Frontend `typecheck`: passed.
- Frontend production `build`: passed.
- Live web-push config, subscription save, and subscription delete: passed.
- Flyway V19 applied.
- Chrome smoke: 0 errors.
