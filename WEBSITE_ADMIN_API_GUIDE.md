# Website and admin panel integration

Base URL: `https://YOUR-BACKEND.onrender.com/api/v1` (replace with your deployed backend).
Every path below is relative to that base URL. JSON requests use `Content-Type: application/json`.
Authenticated requests use `Authorization: Bearer <access-token>`.
Do not expose RESEND_API_KEY in either frontend. The backend sends OTP emails.

## Authentication: shared by website and admin panel

| Method | Path | JSON request | Success |
|---|---|---|---|
| POST | `/auth/register` | `{"name":"Example User","email":"person@example.com","password":"secret123","nativeLanguage":"bn","preferredLanguage":"en"}` | 202; show OTP screen |
| POST | `/auth/verify-email` | `{"email":"person@example.com","otp":"012345"}` | 200; show login |
| POST | `/auth/resend-verification` | `{"email":"person@example.com"}` | 202 |
| POST | `/auth/login` | `{"email":"person@example.com","password":"secret123"}` | 200; tokens and user |
| POST | `/auth/forgot-password` | `{"email":"person@example.com"}` | 202; show reset form |
| POST | `/auth/reset-password` | `{"email":"person@example.com","otp":"012345","newPassword":"newSecret123"}` | 200; show login |
| POST | `/auth/refresh` | `{"refreshToken":"YOUR_REFRESH_TOKEN"}` | 200; refreshed access token |
| POST | `/auth/logout` | No body; attach access token | 200; clear frontend session |

Registration name is 2–50 characters, email is at most 50 characters, and passwords are 6–40 characters. Keep OTP as a six-digit string (preserve leading zeros). Registration and reset verification codes are separate. There is no standalone password-reset OTP verification endpoint: submit code and new password together.

Registration creates only temporary data until verification. OTP validity ends 15 minutes after the first request, including resends; wait at least 60 seconds before resending. Maximum five sends and five verification attempts per email/purpose/window. Expired temporary data is deleted asynchronously by MongoDB TTL. After expiration, restart registration or forgot-password. To resend a password-reset code, call forgot-password again.

202 responses are generic and do not guarantee an email was sent or that an account exists. Existing registered accounts do not need the new registration verification flow. See [OTP operations and configuration](AUTH_EMAIL_VERIFICATION.md).

Login response:

```json
{
  "token": {
    "access_token": "ACCESS_TOKEN",
    "refresh_token": "REFRESH_TOKEN"
  },
  "id": "USER_ID",
  "name": "Example User",
  "email": "person@example.com",
  "roles": ["ROLE_USER"]
}
```

Refresh response uses different field names:

```json
{
  "accessToken": "NEW_ACCESS_TOKEN",
  "refreshToken": "REFRESH_TOKEN",
  "tokenType": "Bearer"
}
```

Admin login uses `/auth/login`, too. Check `roles` for `ROLE_ADMIN`, `ROLE_MODERATOR`, or `ROLE_LANGUAGE_REVIEWER` to render the appropriate panel. Registration never grants elevated roles. An existing admin grants them through the roles API. Backend authorization remains authoritative.

## Public website APIs

| Method | Path | Purpose / inputs |
|---|---|---|
| GET | `/languages` | Active languages |
| GET | `/languages/{id}` | Language detail |
| GET | `/categories` | Categories |
| GET | `/concepts` | Concept list |
| GET | `/concepts/{id}` | Concept detail |
| GET | `/translations` | Optional conceptId, languageId; provide both page and size for pagination |
| GET | `/translations/count` | Total count (number) |
| POST | `/translations/search` | `{"query":"water","sourceLanguageId":"SOURCE_ID","targetLanguageId":"TARGET_ID"}` |
| GET | `/leaderboard` | Optional type, period, limit |
| GET | `/leaderboard/submissions` | Optional period, limit |
| GET | `/leaderboard/translations` | Optional period, limit |
| GET | `/export/files/{filename}` | Public exported PDF download |

## Signed-in website APIs

All require a bearer token. Resource ownership rules apply in addition to authentication.

| Method | Path | Purpose |
|---|---|---|
| GET | `/users/profile` | Current profile, including roles |
| PUT | `/users/profile` | Update name, profileImage, nativeLanguage, preferredLanguage |
| PUT | `/users/change-password` | `{"oldPassword":"secret123","newPassword":"newSecret123"}` |
| GET | `/submissions` | My submissions; page=0, size=20 by default |
| POST | `/submissions` | Submit a translation for review |
| GET | `/collections` | My collections |
| POST | `/collections` | Create collection |
| GET | `/collections/{id}` | Collection detail |
| PUT | `/collections/{id}` | Update collection |
| DELETE | `/collections/{id}` | Delete collection |
| POST | `/collections/{id}/items` | Add item |
| POST | `/collections/{id}/items/bulk` | Add multiple items |
| PATCH | `/collections/{id}/items/{itemId}` | Update item |
| DELETE | `/collections/{id}/items/{itemId}` | Remove item |
| PUT | `/collections/{id}/chapters` | Update chapter ordering |
| GET | `/activity` | Current user's activity |
| GET | `/activity/statistics` | Current user's activity statistics |
| POST | `/images/recognize` | Upload image for recognition; multipart request |
| GET | `/images/history` | Recognition history |
| GET | `/images/files/{filename}` | Image download |
| POST | `/export/pdf` | Generate PDF |
| GET | `/export/history` | Export history |
| GET | `/export/{id}` | Export detail |

For existing feature payloads, see [API documentation](api_documentation.md), [collection/book details](CHANGES_my_books.md), and [search/submission details](CHANGES_search_and_submission.md). The table here inventories routes; it is not a complete schema for every feature.

## Admin panel APIs

All require a bearer token. A = ROLE_ADMIN, M = ROLE_MODERATOR, R = ROLE_LANGUAGE_REVIEWER. Multiple letters mean any listed role.

| Method | Path | Roles | Purpose / inputs |
|---|---|---|---|
| GET | `/admin/statistics` | A | Dashboard counts |
| GET | `/admin/users` | A | All users |
| PUT | `/admin/users/{id}/status?status=SUSPENDED` | A | Status: ACTIVE, INACTIVE, SUSPENDED |
| PUT | `/admin/users/{id}/roles` | A | JSON array, e.g. `["MODERATOR"]`; replaces elevated roles, always keeps ROLE_USER |
| GET | `/admin/submissions/pending` | A/M/R | Pending queue; page, size |
| GET | `/admin/submissions/{id}` | A/M/R | Submission detail |
| GET | `/admin/submissions/history` | A/M/R | Current reviewer's own history; page, size |
| POST | `/admin/submissions/{id}/approve` | A/M/R | Review submission |
| POST | `/admin/submissions/{id}/reject` | A/M/R | Reject submission |
| POST | `/admin/submissions/approve-all` | A | Approve all pending; no body |
| GET | `/admin/leaderboard` | A/M | Optional status, from, to, limit; dates YYYY-MM-DD |
| GET | `/admin/leaderboard/users/{userId}` | A/M | Contributor audit |
| GET | `/admin/languages` | A/M | All languages including inactive |
| POST | `/admin/languages` | A | Create language |
| PUT | `/admin/languages/{id}` | A | Update language |
| DELETE | `/admin/languages/{id}` | A | Delete language if no translations reference it |
| POST | `/admin/categories` | A | Create category |
| PUT | `/admin/categories/{id}` | A | Update category |
| DELETE | `/admin/categories/{id}` | A | Delete category |
| POST | `/admin/concepts` | A | Create concept |
| PUT | `/admin/concepts/{id}` | A/M | Update concept |
| DELETE | `/admin/concepts/{id}` | A | Delete concept |
| POST | `/admin/translations` | A | Create translation directly |
| PUT | `/admin/translations/{id}` | A/M | Update translation |
| DELETE | `/admin/translations/{id}` | A | Delete translation |

Use the public list/detail endpoints for categories, concepts, and translations in the admin panel. There are no separate admin GET list routes for these resources.

Language update/delete controller annotations mention moderators, but SecurityConfig's broader admin rule requires ADMIN for those methods. This table reflects effective access. The older RBAC guide's BANNED status example is incorrect; the implemented enum is SUSPENDED.

## Frontend integration notes

- For signup: register → verify-email → login. Do not mark the user signed in after registration or verification alone.
- For recovery: forgot-password → reset-password → login. A successful reset revokes refresh tokens, but issued access JWTs remain valid until their expiry.
- Read login tokens from `response.token.access_token` / `response.token.refresh_token`; read refreshed tokens from `response.accessToken` / `response.refreshToken`.
- Attach a bearer token to logout even though the route is publicly permitted; without it the endpoint cannot identify the user's refresh token to revoke. Clear frontend tokens afterward.
- Handle OTP failures: 400 invalid/expired/exhausted/used code; 429 cooldown or limit; 503 delivery unavailable. Auth message responses have a `message` field. Other existing endpoints can return plain-text errors; do not assume every error is JSON.
- On protected APIs, 401 means authentication is needed; 403 means insufficient permission. Avoid refresh retry loops.
- Add the exact deployed website/admin origin to backend CORS configuration if it is not already allowed.
- No admin API currently lists pending email challenges, exposes OTPs, or manually verifies registrations.

Outside the API base path, `GET /health` is a public health check. Security configuration also permits `/swagger-ui/index.html` and `/v3/api-docs`; availability depends on the deployed OpenAPI integration.
