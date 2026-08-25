# Koro — REST API Documentation

This document lists **every** REST API endpoint in the **Koro Multilingual & Indigenous Language Platform**, including which role(s) can call it.

---

## 💻 Swagger UI & OpenAPI Specs
When the application is running locally:
*   **Interactive Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
*   **OpenAPI Specs (JSON)**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 🔐 Roles & the two admin roles

| Role | Who | Scope |
|---|---|---|
| `ROLE_USER` | Every registered account | Standard app features: translate, search, scan, save, submit, export. |
| `ROLE_LANGUAGE_REVIEWER` | Content moderator | Everything `ROLE_USER` can do, **plus** the submission review queue (approve/reject suggested translations). No access to user management, language/concept/translation CRUD, or platform settings. |
| `ROLE_ADMIN` | Platform operator | Full control: everything above, plus user management (status + role grants), language/category/concept/translation CRUD, and dashboard statistics. |
| `ROLE_MODERATOR` | Reserved | Defined in the `Role` enum for future use; no endpoint currently checks for it. |

This is the standard "least privilege" split: **reviewers only ever touch the moderation queue**, admins have full platform control. It is enforced identically at two layers:
1. **`SecurityConfig`** (`/api/v1/admin/submissions/**` → `ADMIN` or `LANGUAGE_REVIEWER`; every other `/api/v1/admin/**` → `ADMIN` only) as a defense-in-depth request-level filter.
2. **`@PreAuthorize`** on each controller method, so the rule travels with the code even if the URL changes.

**Role assignment is admin-only.** `POST /api/v1/auth/register` always creates a plain `ROLE_USER` account — it no longer accepts a client-supplied `roles` field (previously anyone could self-register as `admin` or `reviewer`; this has been fixed). The **only** way to grant `ROLE_ADMIN` or `ROLE_LANGUAGE_REVIEWER` is for an existing admin to call `PUT /api/v1/admin/users/{id}/roles`.

All protected endpoints require a `Bearer <JWT_TOKEN>` in the `Authorization` header.

---

## 📋 Full permission matrix

| Endpoint | Method | Public | User | Reviewer | Admin |
|---|---|:---:|:---:|:---:|:---:|
| `/api/v1/auth/register` | POST | ✅ | – | – | – |
| `/api/v1/auth/login` | POST | ✅ | – | – | – |
| `/api/v1/auth/refresh` | POST | ✅ | – | – | – |
| `/api/v1/auth/logout` | POST | | ✅ | ✅ | ✅ |
| `/api/v1/auth/forgot-password` | POST | ✅ | – | – | – |
| `/api/v1/auth/reset-password` | POST | ✅ | – | – | – |
| `/api/v1/users/profile` | GET / PUT | | ✅ | ✅ | ✅ |
| `/api/v1/users/change-password` | PUT | | ✅ | ✅ | ✅ |
| `/api/v1/languages` | GET | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/languages/{id}` | GET | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/categories` | GET | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/concepts`, `/concepts/{id}` | GET | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/translations` | GET | ✅ | ✅ | ✅ | ✅ |
| `/api/v1/translations/search` | POST | | ✅ | ✅ | ✅ |
| `/api/v1/collections/**` | GET/POST/PUT/DELETE | | ✅ own | ✅ own | ✅ own |
| `/api/v1/images/recognize` | POST | | ✅ | ✅ | ✅ |
| `/api/v1/images/history` | GET | | ✅ | ✅ | ✅ |
| `/api/v1/images/files/{name}` | GET | | ✅ | ✅ | ✅ |
| `/api/v1/export/pdf` | POST | | ✅ | ✅ | ✅ |
| `/api/v1/export/history`, `/export/{id}` | GET | | ✅ own | ✅ own | ✅ own |
| `/api/v1/export/files/{name}` | GET | | ✅ | ✅ | ✅ |
| `/api/v1/submissions` | GET / POST | | ✅ | ✅ | ✅ |
| `/api/v1/activity`, `/activity/statistics` | GET | | ✅ own | ✅ own | ✅ own |
| `/api/v1/admin/submissions/pending` | GET | | | ✅ | ✅ |
| `/api/v1/admin/submissions/{id}` | GET | | | ✅ | ✅ |
| `/api/v1/admin/submissions/{id}/approve` | POST | | | ✅ | ✅ |
| `/api/v1/admin/submissions/{id}/reject` | POST | | | ✅ | ✅ |
| `/api/v1/admin/statistics` | GET | | | | ✅ |
| `/api/v1/admin/users` | GET | | | | ✅ |
| `/api/v1/admin/users/{id}/status` | PUT | | | | ✅ |
| `/api/v1/admin/users/{id}/roles` | PUT | | | | ✅ |
| `/api/v1/admin/languages` | POST | | | | ✅ |
| `/api/v1/admin/languages/{id}` | PUT / DELETE | | | | ✅ |
| `/api/v1/admin/categories` | POST | | | | ✅ |
| `/api/v1/admin/concepts` | POST | | | | ✅ |
| `/api/v1/admin/translations` | POST | | | | ✅ |

> **Known gap, not yet built:** there is currently no `PUT`/`DELETE` for `/api/v1/admin/categories`, `/api/v1/admin/concepts`, or `/api/v1/admin/translations` — only create (`POST`) exists for those three resources today. Say the word if you want those added; they'd follow the same `ADMIN`-only pattern as `/api/v1/admin/languages/{id}`.

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

---

## 4. Translation & Search API (`/api/v1/translations`)

### List / Search Translations
*   **URL**: `/api/v1/translations` (`GET`), `/api/v1/translations/search` (`POST`)
*   **Access**: `GET` is public; `POST /search` requires authentication (any role)

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

### Submit / List My Submissions
*   **URL**: `/api/v1/submissions`
*   **Method**: `POST` / `GET`
*   **Access**: Authenticated (any role)

### Review Queue — Reviewer or Admin
*   **URLs**:
    ```http
    GET  /api/v1/admin/submissions/pending
    GET  /api/v1/admin/submissions/{id}
    POST /api/v1/admin/submissions/{id}/approve
    POST /api/v1/admin/submissions/{id}/reject
    ```
*   **Access**: `ROLE_ADMIN` or `ROLE_LANGUAGE_REVIEWER`
*   **Request Body** (approve/reject, optional):
    ```json
    { "reviewerNote": "Verified by community linguist." }
    ```

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

### Create Language / Category / Concept / Translation
*   **URLs**:
    ```http
    POST /api/v1/admin/languages
    PUT  /api/v1/admin/languages/{id}
    DELETE /api/v1/admin/languages/{id}
    POST /api/v1/admin/categories
    POST /api/v1/admin/concepts
    POST /api/v1/admin/translations
    ```
*   **Access**: `ROLE_ADMIN` only

---

## 🧪 Seeded test accounts

`DatabaseInitializer` seeds three accounts on first run (empty database) so you can exercise every role immediately:

| Email | Password | Roles |
|---|---|---|
| `admin@koro.com` | `password` | `ROLE_ADMIN`, `ROLE_USER` |
| `reviewer@koro.com` | `password` | `ROLE_LANGUAGE_REVIEWER`, `ROLE_USER` |
| `user@koro.com` | `password` | `ROLE_USER` |
