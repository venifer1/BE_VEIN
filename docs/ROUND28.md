# VEIN Round 28

Date: 2026-06-14

## Admin User Operations

- Restricted Admin APIs to `SUPER_ADMIN`.
- Added `GET /api/v1/admin/users`.
- Added `PATCH /api/v1/admin/users/{id}/approve`.
- Added `PATCH /api/v1/admin/users/{id}/lock`.
- Prevented an admin from locking their own account.
- User status changes are written to the existing audit log table.

## Admin Audit Logs

- Added `GET /api/v1/admin/audit-logs`.
- Admin dashboard now shows recent audit activity with actor, target, IP, and detail metadata.
- Admin dashboard now includes user status controls and keeps overview, user list, and audit log queries in sync after mutations.
- Mock mode supports the new admin user and audit endpoints.

## Verification

- Backend `compileJava`: passed.
- Backend `compileTestJava`: passed.
- Frontend `typecheck`: passed.
- Frontend production `build`: passed.
- Live authenticated scanner run: passed.
- Live admin users and audit logs APIs: passed.
- Chrome smoke: 0 errors.

## Running Servers

- Backend: `http://localhost:8080`
- Frontend: `http://localhost:3000`
