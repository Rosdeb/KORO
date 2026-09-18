# KORO Role-Based API Documentation (with Example Responses)

This document outlines the role-based access control (RBAC) configured for the API endpoints in the KORO backend, along with expected JSON responses. When integrating your frontend or dashboard, refer to these required roles to conditionally render UI components (like Admin tabs, moderation buttons, etc.).

## Defined Roles
*   `ADMIN` - Full system access (User management, moderation, raw data creation/deletion).
*   `MODERATOR` - Can manage concepts, languages, translations, and review submissions, but cannot delete them or manage users.
*   `LANGUAGE_REVIEWER` - Specifically authorized to approve/reject translation submissions and view moderation histories.
*   `USER` - Any authenticated user (no special role required for basic profile or submission creation endpoints).

---

## 1. System Administration APIs
These APIs are restricted strictly to the **ADMIN** role. They handle core user and system settings.

### `GET /api/v1/admin/statistics`
**Roles:** `ADMIN`
**Description:** Retrieve high-level dashboard statistics.
**Response Example:**
```json
{
  "totalUsers": 150,
  "totalLanguages": 5,
  "totalConcepts": 300,
  "totalTranslations": 1200,
  "pendingApprovals": 45,
  "todayActivities": 12
}
```

### `GET /api/v1/admin/users`
**Roles:** `ADMIN`
**Description:** List all users in the system.
**Response Example:**
```json
[
  {
    "id": "64f1a2b3c4d5e6f7a8b9c0d1",
    "name": "Admin User",
    "email": "admin@koro.com",
    "roles": ["ROLE_USER", "ROLE_ADMIN"],
    "status": "ACTIVE",
    "createdAt": "2023-09-01T10:00:00Z"
  }
]
```

### `PUT /api/v1/admin/users/{id}/status`
**Roles:** `ADMIN`
**Query Param:** `?status=ACTIVE` (or `BANNED`, `INACTIVE`)
**Response Example:**
```json
{
  "message": "User status updated to ACTIVE"
}
```

### `PUT /api/v1/admin/users/{id}/roles`
**Roles:** `ADMIN`
**Body:** `["ADMIN", "MODERATOR"]`
**Response Example:** *(Returns updated user profile)*
```json
{
  "id": "64f1a2b3c4d5e6f7a8b9c0d1",
  "name": "Admin User",
  "email": "admin@koro.com",
  "roles": ["ROLE_USER", "ROLE_ADMIN", "ROLE_MODERATOR"],
  "status": "ACTIVE"
}
```

---

## 2. Leaderboard & Auditing APIs
These APIs track contributor activity and provide detailed audits on submissions.

### `GET /api/v1/admin/leaderboard`
**Roles:** `ADMIN`, `MODERATOR`
**Response Example:**
```json
[
  {
    "userId": "64f1a2...",
    "userName": "John Doe",
    "approvedSubmissions": 150,
    "pendingSubmissions": 5,
    "rejectedSubmissions": 2
  }
]
```

---

## 3. Submissions & Moderation APIs

### `GET /api/v1/admin/submissions/pending`
**Roles:** `ADMIN`, `MODERATOR`, `LANGUAGE_REVIEWER`
**Response Example:**
```json
[
  {
    "id": "sub_123",
    "conceptName": "Apple",
    "languageName": "Bangla",
    "proposedText": "আপেল",
    "proposedPronunciation": "Apel",
    "status": "PENDING",
    "submittedBy": "user_id_here",
    "submittedByName": "Jane Doe"
  }
]
```

### `POST /api/v1/admin/submissions/{id}/approve`
**Roles:** `ADMIN`, `MODERATOR`, `LANGUAGE_REVIEWER`
**Body:** `{ "reviewNotes": "Looks good" }`
**Response Example:**
```json
{
  "message": "Submission approved successfully!"
}
```

### `POST /api/v1/admin/submissions/approve-all`
**Roles:** `ADMIN`
**Response Example:**
```json
{
  "message": "Successfully approved 45 pending submissions."
}
```

---

## 4. Languages APIs

### `GET /api/v1/admin/languages`
**Roles:** `ADMIN`, `MODERATOR`
**Response Example:**
```json
[
  {
    "id": "lang_1",
    "name": "Bangla",
    "code": "bn",
    "nativeName": "বাংলা"
  }
]
```

### `POST /api/v1/admin/languages`
**Roles:** `ADMIN`
**Body:** `{ "name": "Spanish", "code": "es" }`
**Response Example:** *(Returns created language)*

---

## 5. Concepts & Categories APIs

### `POST /api/v1/admin/categories`
**Roles:** `ADMIN`
**Body:** `{ "name": "Fruits", "description": "Edible fruits" }`
**Response Example:** *(Returns created category object)*

### `POST /api/v1/admin/concepts`
**Roles:** `ADMIN`
**Body:** `{ "name": "Apple", "categoryId": "cat_1" }`
**Response Example:** *(Returns created concept object)*

---

## 6. Translations APIs (Direct Management)

### `POST /api/v1/admin/translations`
**Roles:** `ADMIN`
**Body:** `{ "conceptId": "c_1", "languageId": "l_1", "text": "আপেল", "pronunciation": "Apel" }`
**Response Example:** 
```json
{
  "id": "t_1",
  "conceptName": "Apple",
  "languageName": "Bangla",
  "text": "আপেল",
  "pronunciation": "Apel",
  "verified": true
}
```

---

## 7. Public & General User APIs
*Note: These do not require administrative roles.*

- **User Profile:** `GET /api/v1/users/profile` (Returns current user JSON)
- **Translations list:** `GET /api/v1/translations?page=0&size=20` (Returns paginated JSON as recently updated)
- **Translations count:** `GET /api/v1/translations/count` (Returns simple number `150`)
