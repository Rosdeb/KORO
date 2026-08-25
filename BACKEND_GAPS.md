# Backend Gaps — Missing / Incomplete APIs

This is a reference of every endpoint the admin panel's [API_MAPPING.md] expects
that either doesn't exist in this backend, or exists but is incomplete. Each
entry below was verified directly against the controller source in this repo
(file + line), not just against the frontend doc's claims.

## 1. ~~Category CRUD — Update & Delete missing~~ RESOLVED

`PUT /api/v1/admin/categories/{id}` and `DELETE /api/v1/admin/categories/{id}`
have been added, following the `LanguageController` partial-update pattern —
[ConceptController.java:44-62](src/main/java/com/koro/app/concept/controller/ConceptController.java#L44-L62).
(`GET /api/v1/categories/{id}` still doesn't exist, but nothing in the admin
panel mapping needed it.)

## 2. ~~Concept CRUD — Update & Delete missing~~ RESOLVED

`PUT /api/v1/admin/concepts/{id}` and `DELETE /api/v1/admin/concepts/{id}`
have been added, including re-resolving the `category` reference when a new
category id is supplied — [ConceptController.java:96-121](src/main/java/com/koro/app/concept/controller/ConceptController.java#L96-L121).

## 3. ~~Translation CRUD — Update & Delete missing~~ RESOLVED

`PUT /api/v1/admin/translations/{id}` and `DELETE /api/v1/admin/translations/{id}`
have been added, reusing the same `TranslationRequest` DTO the create endpoint
already used — [TranslationController.java:132-161](src/main/java/com/koro/app/translation/controller/TranslationController.java#L132-L161).

All three now mirror `LanguageController`'s existing pattern exactly:
[LanguageController.java:49](src/main/java/com/koro/app/language/controller/LanguageController.java#L49)
(`PUT`) and [LanguageController.java:68](src/main/java/com/koro/app/language/controller/LanguageController.java#L68)
(`DELETE`) — partial update (only non-null fields overwritten), 404 on
missing id, `@PreAuthorize("hasRole('ADMIN')")`, no duplicate-name
revalidation on update (consistent with how `updateLanguage` behaves).

## 4. `PUT /api/v1/admin/users/{id}/roles` does not exist

`AdminController.java` ([full file](src/main/java/com/koro/app/admin/controller/AdminController.java))
only defines three endpoints:

- `GET /statistics` — line 47
- `GET /users` — line 63
- `PUT /users/{id}/status` — line 72

There is **no** `/users/{id}/roles` mapping anywhere in the controller. This
isn't a documentation gap — the frontend's `adminUsersApi.updateRoles` call is
pointed at an endpoint that genuinely does not exist on this backend yet.
Role management (grant/revoke `ROLE_ADMIN`, `ROLE_LANGUAGE_REVIEWER`, etc.) has
no server-side implementation at all.

## 5. `GET /api/v1/admin/users/{id}` does not exist

Same file, same three endpoints as above — there's no single-user detail
endpoint. The only way to fetch one admin-visible user today is to call
`GET /api/v1/admin/users` (line 63, returns the full list, no filtering) and
find the id client-side.

## 6. `GET /api/v1/admin/users` has no pagination/search/sort params

[AdminController.java:64](src/main/java/com/koro/app/admin/controller/AdminController.java#L64)
— `getAllUsers()` takes no parameters and always returns
`userRepository.findAll()`. There's no `page`, `size`, `q`, or `sort` support,
so every list will return the entire user table in one response. Fine at
current scale; will need real pagination before the user table grows large.

## 7. Activity log is hard-locked to the calling user — no admin view of another user's activity

[ActivityController.java:21-33](src/main/java/com/koro/app/activity/controller/ActivityController.java#L21-L33)
calls `activityLogService.getLogsForCurrentUser()` /
`getLogsForCurrentUserFiltered(...)` unconditionally — there is no `userId`
parameter, and no `hasRole('ADMIN')` branch that would let an admin pull
someone else's timeline. Same for
[getActivityStatistics() at line 35-38](src/main/java/com/koro/app/activity/controller/ActivityController.java#L35-L38).
This is why the admin panel's "Activity Timeline" on the User Details page
only ever shows data when the admin is viewing their own account.

## 8. No submission history — only pending is listable

`SubmissionController.java` has:

- `POST /submissions` (create)
- `GET /submissions` (caller's own submissions)
- `GET /admin/submissions/pending`
- `GET /admin/submissions/{id}` (single submission, any status — exists but
  not mentioned in the frontend mapping doc)
- `POST /admin/submissions/{id}/approve`
- `POST /admin/submissions/{id}/reject`

There's no `GET /admin/submissions?status=APPROVED|REJECTED` or equivalent, so
once a submission leaves `PENDING` it's no longer retrievable as part of any
list — only individually by id if you already know it.

## 9. No roles/permissions endpoint

No controller anywhere in the codebase exposes a roles or permissions list.
The admin panel's "Roles & Permissions" page has nothing to call; it's
necessarily a static table in the frontend.

## 10. No platform settings endpoint

No `SettingsController` or equivalent exists. Any admin-configurable platform
setting (beyond a user's own profile) has no backing API today.

## 11. `/admin/statistics` and `/activity/statistics` return undocumented `object`

[AdminController.java:47-61](src/main/java/com/koro/app/admin/controller/AdminController.java#L47-L61)
and `ActivityController.java:35-38` both return a raw `Map<String, Object>` /
service-defined object with no DTO, so OpenAPI can't generate a real schema
for either. Not broken, but any frontend consuming these is coding against
whatever keys happen to be in the map today — a DTO would make the contract
explicit and catch accidental field renames at compile time.

## 12. No server-side tabular report / CSV export endpoint

`ExportController.java` only handles the PDF vocabulary export feature —
[ExportController.java:41](src/main/java/com/koro/app/export/controller/ExportController.java#L41)
(`POST /pdf`), [line 62](src/main/java/com/koro/app/export/controller/ExportController.java#L62)
(`GET /history`), [line 68](src/main/java/com/koro/app/export/controller/ExportController.java#L68)
(`GET /{id}`), and [line 81](src/main/java/com/koro/app/export/controller/ExportController.java#L81)
(`GET /files/{filename}`) — all owner-scoped end-user features, unrelated to
admin reporting. There is no endpoint anywhere that generates or exports the
tabular reports shown on the admin Reports page; that page is assembled
entirely from the same list endpoints as Overview/Analytics, with CSV
generated client-side. Not broken (client-side CSV works fine at current
data volumes), but there's no way to schedule/email/download those reports
server-side, and every row has to be fetched to the browser to produce one.

---

## Priority if implementing

1. ~~Category/Concept/Translation update+delete~~ — done (#1–3).
2. **`PUT /admin/users/{id}/roles`** (#4) — currently a real dead endpoint on
   the frontend; role management doesn't work at all until this exists.
3. **`GET /admin/users/{id}`** (#5) — cheap addition, removes the
   fetch-everything-and-filter workaround.
4. **Admin-scoped activity access** (#7) — needs a
   `hasRole('ADMIN')`-gated overload (e.g. `GET /admin/activity/{userId}`)
   rather than changing the existing self-scoped endpoint.
5. Everything else (#6, #8–11) is a scale/completeness improvement, not a
   broken feature.
