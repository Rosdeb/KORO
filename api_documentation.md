# Koro — REST API Documentation

This document lists **every** REST API endpoint in the **Koro Multilingual & Indigenous Language Platform**, including which role(s) can call it.

---

## 💻 Swagger UI & OpenAPI Specs
When the application is running locally:
*   **Interactive Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
*   **OpenAPI Specs (JSON)**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 🔐 Roles

| Role | Who | Scope |
|---|---|---|
| `ROLE_USER` | Every registered account | Standard app features: translate, search, scan, save, submit, export. |
| `ROLE_LANGUAGE_REVIEWER` | Content reviewer | Everything `ROLE_USER` can do, **plus** the submission review queue (approve/reject). No dictionary-editing rights, no user/category/language management. |
| `ROLE_MODERATOR` | Content moderator | Everything `ROLE_USER` can do, **plus** the submission review queue (approve/reject), **plus limited dictionary editing** — can correct an *existing* concept or translation (`PUT`), but cannot create new ones directly (must go through submission review) or delete them. No category/language/user/role management. |
| `ROLE_ADMIN` | Platform operator | Full control: everything above, plus user management (status + role grants), full category/language/concept/translation CRUD (create + update + delete), and dashboard statistics. |

`ROLE_LANGUAGE_REVIEWER` and `ROLE_MODERATOR` currently grant identical review-queue access; `ROLE_MODERATOR` additionally gets the limited dictionary-edit rights above. Both are intentionally kept below `ROLE_ADMIN`, which is the only role with create/delete rights over categories, languages, concepts, and translations, and the only role that can manage users or grant roles.

This is enforced identically at two layers:
1. **`SecurityConfig`** — a defense-in-depth request-level filter (submission-review paths open to `ADMIN`/`LANGUAGE_REVIEWER`/`MODERATOR`; the concept/translation `PUT` routes open to `ADMIN`/`MODERATOR`; everything else under `/api/v1/admin/**` restricted to `ADMIN`).
2. **`@PreAuthorize`** on each controller method, so the rule travels with the code even if the URL changes.

**Role assignment is admin-only.** `POST /api/v1/auth/register` always creates a plain `ROLE_USER` account — it does not accept a client-supplied `roles` field (previously anyone could self-register as `admin` or `reviewer`; this has been fixed). The **only** way to grant `ROLE_ADMIN`, `ROLE_LANGUAGE_REVIEWER`, or `ROLE_MODERATOR` is for an existing admin to call `PUT /api/v1/admin/users/{id}/roles`.

All protected endpoints require a `Bearer <JWT_TOKEN>` in the `Authorization` header.

---

## 📋 Full permission matrix

| Endpoint | Method | Public | User | Reviewer | Moderator | Admin |
|---|---|:---:|:---:|:---:|:---:|:---:|
| `/api/v1/auth/register` | POST | ✅ | – | – | – | – |
| `/api/v1/auth/login` | POST | ✅ | – | – | – | – |
| `/api/v1/auth/refresh` | POST | ✅ | – | – | – | – |
| `/api/v1/auth/logout` | POST | | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/auth/forgot-password` | POST | ✅ | – | – | – | – |
| `/api/v1/auth/reset-password` | POST | ✅ | – | – | – | – |
| `/api/v1/users/profile` | GET / PUT | | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/users/change-password` | PUT | | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/languages`, `/languages/{id}` | GET | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/categories` | GET | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/concepts`, `/concepts/{id}` | GET | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/translations` | GET | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/translations/search` | POST | | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/collections/**` | GET/POST/PUT/DELETE | | ✅ own | ✅ own | ✅ own | ✅ own |
| `/api/v1/images/recognize`, `/images/history`, `/images/files/{name}` | POST/GET | | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/export/pdf`, `/export/history`, `/export/{id}`, `/export/files/{name}` | POST/GET | | ✅ own | ✅ own | ✅ own | ✅ own |
| `/api/v1/submissions` | GET / POST | | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/activity`, `/activity/statistics` | GET | | ✅ own | ✅ own | ✅ own | ✅ own |
| `/api/v1/admin/submissions/pending` | GET | | | ✅ | ✅ | ✅ |
| `/api/v1/admin/submissions/{id}` | GET | | | ✅ | ✅ | ✅ |
| `/api/v1/admin/submissions/history` | GET | | | ✅ own | ✅ own | ✅ own |
| `/api/v1/admin/submissions/{id}/approve` | POST | | | ✅ | ✅ | ✅ |
| `/api/v1/admin/submissions/{id}/reject` | POST | | | ✅ | ✅ | ✅ |
| `/api/v1/admin/concepts/{id}` | PUT | | | | ✅ | ✅ |
| `/api/v1/admin/translations/{id}` | PUT | | | | ✅ | ✅ |
| `/api/v1/admin/statistics` | GET | | | | | ✅ |
| `/api/v1/admin/users` | GET | | | | | ✅ |
| `/api/v1/admin/users/{id}/status` | PUT | | | | | ✅ |
| `/api/v1/admin/users/{id}/roles` | PUT | | | | | ✅ |
| `/api/v1/admin/languages` | POST | | | | | ✅ |
| `/api/v1/admin/languages/{id}` | PUT / DELETE | | | | | ✅ |
| `/api/v1/admin/categories` | POST | | | | | ✅ |
| `/api/v1/admin/categories/{id}` | PUT / DELETE | | | | | ✅ |
| `/api/v1/admin/concepts` | POST | | | | | ✅ |
| `/api/v1/admin/concepts/{id}` | DELETE | | | | | ✅ |
| `/api/v1/admin/translations` | POST | | | | | ✅ |
| `/api/v1/admin/translations/{id}` | DELETE | | | | | ✅ |

> **Not yet built:** the table you gave includes a "Review Reports" capability for Moderator/Admin. There is no content-flagging/report feature anywhere in this codebase today (no `Report` entity, no submit-a-report endpoint for users) — "Moderation History" above is served from the existing submission audit trail (`reviewedBy`/`reviewedAt` on `TranslationSubmission`), which is a different thing. Say the word if you want a real report/flag system built; it would need a new entity + a user-facing "report this entry" endpoint before there's anything for a moderator to review.

---

## 1. Authentication API (`/api/v1/auth`)

### Register User
*   **URL**: `/api/v1/auth/register`
*   **Method**: `POST`
*   **Access**: Public
*   **Request Body**:
    ```json
    {
      "name": "John Doe",
      "email": "user@koro.com",
      "password": "password",
      "nativeLanguage": "Bangla",
      "preferredLanguage": "Chakma"
    }
    ```
    *Always creates a `ROLE_USER` account. There is no client-controlled `roles` field — this was a privilege-escalation hole (anyone could self-register as admin) that has been closed. To grant elevated roles, an admin must call `PUT /api/v1/admin/users/{id}/roles` after the account exists.*
*   **Response (200 OK)**:
    ```json
    {
      "message": "User registered successfully!"
    }
    ```

### Login
*   **URL**: `/api/v1/auth/login`
*   **Method**: `POST`
*   **Access**: Public
*   **Request Body**:
    ```json
    {
      "email": "admin@koro.com",
      "password": "password"
    }
    ```
*   **Response (200 OK)**:
    ```json
    {
      "token": {
        "access_token": "eyJhbGciOi...",
        "refresh_token": "4a15993e-..."
      },
      "id": "603d2b...",
      "name": "Koro Admin",
      "email": "admin@koro.com",
      "roles": ["ROLE_ADMIN", "ROLE_USER"]
    }
    ```

### Refresh Token
*   **URL**: `/api/v1/auth/refresh`
*   **Method**: `POST`
*   **Access**: Public
*   **Request Body**:
    ```json
    { "refreshToken": "4a15993e-..." }
    ```
*   **Response (200 OK)**:
    ```json
    {
      "accessToken": "eyJhbGciOi...",
      "refreshToken": "4a15993e-...",
      "tokenType": "Bearer"
    }
    ```

### Logout
*   **URL**: `/api/v1/auth/logout`
*   **Method**: `POST`
*   **Access**: Authenticated (any role)

### Forgot / Reset Password
*   **URLs**: `/api/v1/auth/forgot-password`, `/api/v1/auth/reset-password`
*   **Method**: `POST`
*   **Access**: Public

---

## 2. User Profile API (`/api/v1/users`)

### Get / Update Current User Profile
*   **URL**: `/api/v1/users/profile`
*   **Method**: `GET` / `PUT`
*   **Access**: Authenticated (any role)

### Change Password
*   **URL**: `/api/v1/users/change-password`
*   **Method**: `PUT`
*   **Access**: Authenticated (any role)

---

## 3. Language, Category & Concept API (`/api/v1`)

### Get Active Languages / Language by ID
*   **URL**: `/api/v1/languages`, `/api/v1/languages/{id}`
*   **Method**: `GET`
*   **Access**: Public

### Get Categories
*   **URL**: `/api/v1/categories`
*   **Method**: `GET`
*   **Access**: Public

### Get Concepts / Concept by ID
*   **URL**: `/api/v1/concepts`, `/api/v1/concepts/{id}`
*   **Method**: `GET`
*   **Access**: Public

### Update Concept — Admin or Moderator
*   **URL**: `/api/v1/admin/concepts/{id}`
*   **Method**: `PUT`
*   **Access**: `ROLE_ADMIN` or `ROLE_MODERATOR`
*   Partial update (only non-null fields overwritten): `name`, `description`, `referenceImage`, `category.id`. This is the "limited dictionary edit" a moderator has — fixing an existing entry. Creating (`POST`) or deleting (`DELETE`) a concept remains `ROLE_ADMIN` only.

---

## 4. Translation & Search API (`/api/v1/translations`)

### List / Search Translations
*   **URL**: `/api/v1/translations` (`GET`), `/api/v1/translations/search` (`POST`)
*   **Access**: `GET` is public; `POST /search` requires authentication (any role)

### Update Translation — Admin or Moderator
*   **URL**: `/api/v1/admin/translations/{id}`
*   **Method**: `PUT`
*   **Access**: `ROLE_ADMIN` or `ROLE_MODERATOR`
*   Partial update: `conceptId`, `languageId`, `text`, `pronunciation`, `notes`. Same "limited edit" reasoning as concepts — moderators fix what already exists; `POST` (create) and `DELETE` remain `ROLE_ADMIN` only.

---

## 5. Saved Vocabulary Collections (`/api/v1/collections`)

*   **URLs**: `/api/v1/collections`, `/api/v1/collections/{id}`, `/api/v1/collections/{id}/items`, `/api/v1/collections/{id}/items/{itemId}`
*   **Methods**: `GET`, `POST`, `PUT`, `DELETE`
*   **Access**: Authenticated, owner-only (a user can only read/modify their own collections — enforced in the controller, not just by role)

---

## 6. AI Vision API (`/api/v1/images`)

*   **URLs**: `/api/v1/images/recognize` (`POST`), `/api/v1/images/history` (`GET`), `/api/v1/images/files/{filename}` (`GET`)
*   **Access**: Authenticated (any role)

---

## 7. Crowd-Sourced Submissions (`/api/v1/submissions`)

A submission is a full dictionary entry — a word in its **source language**, plus the two translations that are always required (Bangla, English) — not a generic "suggested translation" against an existing concept. The relationship being captured is:

`Source Language + Source Word → Bangla Meaning + English Meaning`

### Submit a Word
*   **URL**: `/api/v1/submissions`
*   **Method**: `POST`
*   **Access**: Authenticated (any role)
*   **Request Body**:
    ```json
    {
      "categoryId": "66c7ab...",
      "sourceLanguageId": "66c758...",
      "sourceWord": "বান",
      "banglaTranslation": "গাছ",
      "englishTranslation": "Tree",
      "pronunciation": "ban",
      "exampleSentence": "বান রেপত ফুল ফুদগै (optional, any language)",
      "note": "Community dialect variant (optional)"
    }
    ```
    *   `categoryId`, `sourceLanguageId`, `sourceWord`, `banglaTranslation`, `englishTranslation` are **required** — a `400` is returned listing any missing field.
    *   `pronunciation`, `exampleSentence`, `note` are optional.
*   **Duplicate protection**: before creating the submission, the source word is checked case-insensitively against (a) existing official dictionary entries for that source language and (b) any submission for that language/word that isn't already `REJECTED` (i.e. `PENDING` or `APPROVED`). A match on either returns:
    *   **Response (409 Conflict)**:
        ```json
        "Error: This word already exists or has already been submitted for review."
        ```
*   **Response (200 OK)** — the created submission, `status: "PENDING"`.

### List My Submissions
*   **URL**: `/api/v1/submissions`
*   **Method**: `GET`
*   **Access**: Authenticated (any role)
*   **Response**: each submission includes `sourceWord`, `sourceLanguage`, `category`, `banglaTranslation`, `englishTranslation`, `status`, `createdAt`, and — for resolved submissions — `reviewedAt`, `reviewerNote`, and `rejectionReason` (when rejected).

### Review Queue — Reviewer, Moderator, or Admin
*   **URLs**:
    ```http
    GET  /api/v1/admin/submissions/pending
    GET  /api/v1/admin/submissions/{id}
    GET  /api/v1/admin/submissions/history
    POST /api/v1/admin/submissions/{id}/approve
    POST /api/v1/admin/submissions/{id}/reject
    ```
*   **Access**: `ROLE_ADMIN`, `ROLE_LANGUAGE_REVIEWER`, or `ROLE_MODERATOR`
*   **`GET /history`** *(new)* — the calling user's own past approved/rejected submissions, newest first (their personal "Moderation History"). Not a global audit log; each reviewer/moderator/admin only sees what *they* reviewed.
*   **Approve — Request Body** (optional):
    ```json
    { "reviewerNote": "Verified by community linguist." }
    ```
    On approval: the submission is marked `APPROVED`, and its data is published into the existing dictionary tables —
    1. Find (by English name, case-insensitive) or create a `Concept` for `englishTranslation`, tagged with the submission's `category`.
    2. Upsert the `Translation` row for the source language (`sourceWord`, `pronunciation`, `note`, `exampleSentence`), and for Bangla/English (skipped if the source language *is* Bangla or English, to avoid overwriting the same row twice).
    3. The word is now returned by the existing `GET /api/v1/translations`, `POST /api/v1/translations/search`, and image-recognition matching — no new dictionary-read endpoint was needed.
*   **Reject — Request Body** (`rejectionReason` required):
    ```json
    { "rejectionReason": "Duplicate of an existing entry.", "reviewerNote": "See submission #123." }
    ```
    Returns `400` if `rejectionReason` is missing or blank.

---

## 8. PDF Generation API (`/api/v1/export`)

*   **URLs**: `/api/v1/export/pdf` (`POST`), `/api/v1/export/history` (`GET`), `/api/v1/export/{id}` (`GET`, owner-only), `/api/v1/export/files/{filename}` (`GET`)
*   **Access**: Authenticated (any role)

---

## 9. Admin Dashboard API (`/api/v1/admin`) — `ROLE_ADMIN` only

### Dashboard Statistics
*   **URL**: `/api/v1/admin/statistics`
*   **Method**: `GET`
*   **Response**: total users/languages/concepts/translations, pending approvals, today's activity count.

### List All Users
*   **URL**: `/api/v1/admin/users`
*   **Method**: `GET`

### Update User Status
*   **URL**: `/api/v1/admin/users/{id}/status`
*   **Method**: `PUT`
*   **Query Param**: `status` = `ACTIVE` | `SUSPENDED` | ...

### Grant / Revoke User Roles *(new)*
*   **URL**: `/api/v1/admin/users/{id}/roles`
*   **Method**: `PUT`
*   **Request Body**: a JSON array of role names (with or without the `ROLE_` prefix)
    ```json
    ["ADMIN", "USER"]
    ```
    or
    ```json
    ["LANGUAGE_REVIEWER"]
    ```
*   **Behavior**: replaces the user's role set with the given roles; `ROLE_USER` is always kept. Unknown role names return `400 Bad Request`.
*   **Response (200 OK)**: the updated `UserResponse`, including the new `roles` array.
*   This is the **only** endpoint in the system that can grant `ROLE_ADMIN` or `ROLE_LANGUAGE_REVIEWER`.

### Language / Category / Concept / Translation CRUD
*   **URLs**:
    ```http
    GET    /api/v1/admin/languages          -- ADMIN or MODERATOR; returns ALL languages, including inactive ones
    POST   /api/v1/admin/languages
    PUT    /api/v1/admin/languages/{id}
    DELETE /api/v1/admin/languages/{id}     -- rejected (400) if any translation still references the language

    POST   /api/v1/admin/categories
    PUT    /api/v1/admin/categories/{id}
    DELETE /api/v1/admin/categories/{id}

    POST   /api/v1/admin/concepts
    PUT    /api/v1/admin/concepts/{id}      -- ADMIN or MODERATOR (see §3)
    DELETE /api/v1/admin/concepts/{id}      -- rejected (400) if any translation still references the concept

    POST   /api/v1/admin/translations
    PUT    /api/v1/admin/translations/{id}  -- ADMIN or MODERATOR (see §4)
    DELETE /api/v1/admin/translations/{id}
    ```
*   **Access**: `ROLE_ADMIN` only, except the routes marked above which also accept `ROLE_MODERATOR`.
*   **Note**: the public `GET /api/v1/languages` only returns languages with `active: true` (see §3). To manage — and see — inactive languages, use the admin-only `GET /api/v1/admin/languages`, which returns every language regardless of `active` status. Setting `active: false` via `PUT` is a soft-deactivation: the record still exists and is still editable, it just drops out of the public list.

---

## 🧪 Seeded test accounts

`DatabaseInitializer` seeds four accounts on first run (empty database) so you can exercise every role immediately:

| Email | Password | Roles |
|---|---|---|
| `admin@koro.com` | `password` | `ROLE_ADMIN`, `ROLE_USER` |
| `reviewer@koro.com` | `password` | `ROLE_LANGUAGE_REVIEWER`, `ROLE_USER` |
| `moderator@koro.com` | `password` | `ROLE_MODERATOR`, `ROLE_USER` |
| `user@koro.com` | `password` | `ROLE_USER` |
