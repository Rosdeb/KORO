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
| `/api/v1/translations`, `/translations/search` | GET / POST | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/collections/**` | GET/POST/PUT/PATCH/DELETE | | ✅ own | ✅ own | ✅ own | ✅ own |
| `/api/v1/images/recognize`, `/images/history`, `/images/files/{name}` | POST/GET | | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/export/pdf`, `/export/history`, `/export/{id}` | POST/GET | | ✅ own | ✅ own | ✅ own | ✅ own |
| `/api/v1/export/files/{name}` | GET | ✅ | ✅ | ✅ | ✅ | ✅ |
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
*   **Search request body**: `{ "query": "...", "sourceLanguageId": "...", "targetLanguageId": "..." }` — only `query` is required.
    *   `sourceLanguageId` + `targetLanguageId`: cross-language lookup — match `query` in the source language, return the matching concepts' entries in the target language.
    *   `targetLanguageId` only: search within that one language.
    *   neither: global search across every language.
*   **Matching**: `query` is matched (case-insensitively, as a substring) against the translation text, its pronunciation, and the concept name/description, so both English (`water`, `wa`) and Bangla (`পানি`, `পান`) queries work, as does the romanized pronunciation (`pani`).
*   **Unicode**: the query and all stored text are Unicode-normalized to NFC with zero-width joiners stripped, so Bangla words spelled with canonically-equivalent code point sequences (e.g. a precomposed vs. decomposed `ৌ`) still match. Existing rows are normalized once on startup.

### Update Translation — Admin or Moderator
*   **URL**: `/api/v1/admin/translations/{id}`
*   **Method**: `PUT`
*   **Access**: `ROLE_ADMIN` or `ROLE_MODERATOR`
*   Partial update: `conceptId`, `languageId`, `text`, `pronunciation`, `notes`. Same "limited edit" reasoning as concepts — moderators fix what already exists; `POST` (create) and `DELETE` remain `ROLE_ADMIN` only.

---

## 5. Saved Vocabulary Collections / "My Books" (`/api/v1/collections`)

*   **Access**: Authenticated, owner-only (enforced in the controller, not just by role).

| Method | URL | Purpose |
|---|---|---|
| `GET` | `/api/v1/collections` | list my books — returns `CollectionSummaryResponse[]` with `itemCount` + `chapterCount` (no `items` array) |
| `POST` | `/api/v1/collections` | create — body `{ name, description? }`, returns `CollectionResponse` |
| `GET` | `/api/v1/collections/{id}` | full book — `CollectionResponse` with `items` sorted by `(chapter, displayOrder, name)` and the effective `chapterOrder` |
| `PUT` | `/api/v1/collections/{id}` | rename / re-describe — body `{ name, description? }` |
| `DELETE` | `/api/v1/collections/{id}` | delete book + its items |
| `POST` | `/api/v1/collections/{id}/items` | add one word — body `{ conceptId, languageId, chapter?, notes?, displayOrder? }`. `displayOrder` defaults to end-of-chapter. **`409`** if that concept+language is already in the book |
| `POST` | `/api/v1/collections/{id}/items/bulk` | add many — body `{ languageId, conceptIds[], chapter?, notes? }`, returns `{ added: CollectionItemResponse[], skipped: [{conceptId, reason}] }` (always `200`) |
| `PATCH` | `/api/v1/collections/{id}/items/{itemId}` | partial update — body any of `{ chapter, notes, displayOrder, languageId }`. `409` if the language change would collide with another item |
| `PUT` | `/api/v1/collections/{id}/chapters` | bulk rename + reorder chapters — body `{ chapters: [{ from, to? }] }`; list order becomes the book's chapter order (stored, honoured by PDF) |
| `DELETE` | `/api/v1/collections/{id}/items/{itemId}` | remove one word |

`CollectionSummaryResponse`: `id, name, description, itemCount, chapterCount, createdAt, updatedAt`.
`CollectionResponse`: `id, name, description, chapterOrder[], createdAt, updatedAt, items[]`.
`CollectionItemResponse`: `id, conceptId, conceptName, categoryName, languageId, languageName, translationText, pronunciation, exampleSentence, notes, chapter, displayOrder, createdAt`.

> **Changed:** `GET /collections` used to return raw `Collection` entities with no counts. `POST`/`PUT` used to
> return the raw entity (which leaked the owner's password hash) — they now return `CollectionResponse`.

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
    1. Find (by English name, case-insensitive, after Unicode/whitespace normalization) or create a `Concept` for `englishTranslation`, tagged with the submission's `category`.
    2. Upsert three `Translation` rows under that concept:
        *   **source language** — `sourceWord`, plus `pronunciation`, `note`, `exampleSentence`.
        *   **Bangla** — `banglaTranslation`, plus `note` and `exampleSentence` (these describe the concept, so they are copied here too; `pronunciation` is not, it belongs to the source word).
        *   **English** — `englishTranslation`, plus `note` and `exampleSentence`.
        The Bangla or English row is skipped only when the source language *is* Bangla or English (so the same row is not written twice). A blank translation never creates a row.
    3. The word is now returned by `GET /api/v1/translations`, `POST /api/v1/translations/search`, and image-recognition matching.
*   **Approve — Response (200 OK)** — an object, **not the bare submission** (admin UI note):
    ```json
    {
      "submission": { "id": "...", "status": "APPROVED", "reviewedAt": "...", "...": "..." },
      "conceptId": "6a8ef6eb21d2767e52a00c25",
      "translationsSaved": ["Chakma: বান", "Bangla: গাছ", "English: Tree"]
    }
    ```
*   **Approve — Response (400)** with a message string, instead of a `500`, when the submission cannot be published:
    *   the submission's source language was deleted (`"...source language no longer exists..."`).
    *   the `bn` or `en` language is not configured (`"...Bangla (code 'bn') and English (code 'en')..."`).
*   **Reject — Request Body** (`rejectionReason` required):
    ```json
    { "rejectionReason": "Duplicate of an existing entry.", "reviewerNote": "See submission #123." }
    ```
    Returns `400` if `rejectionReason` is missing or blank.

---

## 8. PDF Generation API (`/api/v1/export`)

*   **URLs**: `/api/v1/export/pdf` (`POST`), `/api/v1/export/history` (`GET`), `/api/v1/export/{id}` (`GET`, owner-only), `/api/v1/export/files/{filename}` (`GET`)
*   **Access**: authenticated, **except `GET /files/{filename}` which is public** — the filename is an unguessable UUID, so the URL doubles as a shareable "published book" link. Served `inline` (previews in browser); add `?download=1` to force a save.

### Generate a book PDF
*   **URL**: `/api/v1/export/pdf` · **Method**: `POST`
*   **Request Body**:
    ```jsonc
    {
      "collectionId": "…",
      "languageIds": ["<chakma>", "<bn>", "<en>"],  // one column per language, in this order
      "languageId": "<bn>",                          // legacy single-language alias, still works
      "headwordLanguageId": "<chakma>",              // optional — which language leads each entry (default: first)
      "includeExampleSentences": true                // optional, default true
    }
    ```
    At least one of `languageIds` / `languageId` is required (`400` otherwise).
*   **Response (200)**: `PdfExportResponse` — `{ id, collectionId, collectionName, fileName, fileUrl, fileSize, createdAt }`.
    > **Changed:** was the raw `PdfExport` entity (which leaked the owner's password hash via nested `user`). `GET /history` and `GET /{id}` return the same DTO now.
*   Chapters print in the book's `chapterOrder`; entries within a chapter follow `displayOrder` then name; each entry shows the headword, one line per language (`text (pronunciation)`), example sentences, then the personal note.
*   **Fonts:** Bangla/Chakma need TTF files in `src/main/resources/fonts/` (see that folder's README). Without them those scripts render as blank boxes and a startup warning is logged; Latin is unaffected.

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

### Activity history pagination

`GET /api/v1/activity?from=2026-09-14&to=2026-09-19&page=0&size=10`

Requires authentication. `from` and `to` are optional inclusive dates. `page` is zero-based
(default `0`); `size` defaults to `10` and must be between `1` and `100`. Invalid pagination
or a reversed date range returns HTTP 400. Results are ordered newest first, with ID as a
tie breaker, and paginated in MongoDB.

The response is now an object instead of a plain array; clients must read `content`:

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

Activity requests do not delete history. Older activities remain available through date
filters and pagination, and activity statistics count all stored history.
