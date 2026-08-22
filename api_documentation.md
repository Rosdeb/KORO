# Koro — REST API Documentation

This document lists all available REST API endpoints for the **Koro Multilingual & Indigenous Language Platform**.

---

## 💻 Swagger UI & OpenAPI Specs
When the application is running locally:
*   **Interactive Swagger UI**: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
*   **OpenAPI Specs (JSON)**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

---

## 🔐 Authorization
All protected endpoints require a `Bearer <JWT_TOKEN>` in the `Authorization` header.

*   `ROLE_USER`: Required for standard features.
*   `ROLE_ADMIN`: Required for language and concept settings, user management, and stats.
*   `ROLE_LANGUAGE_REVIEWER` & `ROLE_ADMIN`: Allowed to approve or reject suggested translations.

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
      "preferredLanguage": "Chakma",
      "roles": ["user"] 
    }
    ```
    *Note: Roles can be `user`, `admin`, `mod`, or `reviewer`.*
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
    {
      "refreshToken": "4a15993e-..."
    }
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
*   **Access**: Authenticated

---

## 2. User Profile API (`/api/v1/users`)

### Get Current User Profile
*   **URL**: `/api/v1/users/profile`
*   **Method**: `GET`
*   **Access**: Authenticated (`ROLE_USER`)
*   **Response (200 OK)**:
    ```json
    {
      "id": "603d2b...",
      "name": "John Doe",
      "email": "user@koro.com",
      "profileImage": null,
      "nativeLanguage": "Bangla",
      "preferredLanguage": "Chakma",
      "roles": ["ROLE_USER"],
      "status": "ACTIVE",
      "createdAt": "2026-08-22T17:15:00",
      "updatedAt": "2026-08-22T17:15:00",
      "lastLoginAt": "2026-08-22T18:00:00"
    }
    ```

### Update User Profile
*   **URL**: `/api/v1/users/profile`
*   **Method**: `PUT`
*   **Access**: Authenticated (`ROLE_USER`)
*   **Request Body**:
    ```json
    {
      "name": "John Doe Junior",
      "profileImage": "avatar.png",
      "nativeLanguage": "English",
      "preferredLanguage": "Chakma"
    }
    ```

---

## 3. Language & Category API (`/api/v1`)

### Get Active Languages
*   **URL**: `/api/v1/languages`
*   **Method**: `GET`
*   **Access**: Authenticated (`ROLE_USER`)

### Create Language
*   **URL**: `/api/v1/admin/languages`
*   **Method**: `POST`
*   **Access**: Admin (`ROLE_ADMIN`)
*   **Request Body**:
    ```json
    {
      "name": "Marma",
      "nativeName": "মারমা",
      "code": "mru",
      "region": "Chittagong Hill Tracts",
      "active": true,
      "description": "Indigenous language spoken by the Marma people."
    }
    ```

### Get Concept Categories
*   **URL**: `/api/v1/categories`
*   **Method**: `GET`
*   **Access**: Authenticated (`ROLE_USER`)

---

## 4. Translation & Search API (`/api/v1/translations`)

### Search & Cross-Language Translation
*   **URL**: `/api/v1/translations/search`
*   **Method**: `POST`
*   **Access**: Authenticated (`ROLE_USER`)
*   **Request Body**:
    ```json
    {
      "sourceLanguageId": "66c752...", // e.g. English ID
      "targetLanguageId": "66c758...", // e.g. Chakma ID
      "query": "Tree"
    }
    ```
*   **Response (200 OK)**:
    ```json
    [
      {
        "id": "66c79a...",
        "conceptId": "66c78b...",
        "conceptName": "Tree",
        "categoryName": "Nature",
        "languageId": "66c758...",
        "languageName": "Chakma",
        "text": "বান",
        "pronunciation": "ban",
        "verified": true,
        "notes": null
      }
    ]
    ```

### Add Official Translation
*   **URL**: `/api/v1/admin/translations`
*   **Method**: `POST`
*   **Access**: Admin (`ROLE_ADMIN`)
*   **Request Body**:
    ```json
    {
      "conceptId": "66c78b...",
      "languageId": "66c758...",
      "text": "বান",
      "pronunciation": "ban",
      "notes": "Standard word for tree"
    }
    ```

---

## 5. Saved Vocabulary Collections (`/api/v1/collections`)

### Get My Collections (Books)
*   **URL**: `/api/v1/collections`
*   **Method**: `GET`
*   **Access**: Authenticated (`ROLE_USER`)

### Create Collection (Book)
*   **URL**: `/api/v1/collections`
*   **Method**: `POST`
*   **Access**: Authenticated (`ROLE_USER`)
*   **Request Body**:
    ```json
    {
      "name": "My Nature Book",
      "description": "Vocabulary book for nature terms."
    }
    ```

### Add Word to Collection
*   **URL**: `/api/v1/collections/{id}/items`
*   **Method**: `POST`
*   **Access**: Owner (`ROLE_USER`)
*   **Request Body**:
    ```json
    {
      "conceptId": "66c78b...",
      "languageId": "66c758...",
      "notes": "Hard to remember",
      "chapter": "Chapter 1 — Flora",
      "displayOrder": 1
    }
    ```

---

## 6. AI Vision API (`/api/v1/images`)

### Upload Image for Object Recognition
*   **URL**: `/api/v1/images/recognize`
*   **Method**: `POST`
*   **Access**: Authenticated (`ROLE_USER`)
*   **Multipart Form Data**:
    *   `file`: The image file (e.g. `tree.jpg`)
*   **Response (200 OK)**:
    ```json
    {
      "id": "66c89c...",
      "imageUrl": "/api/v1/images/files/abc-123.jpg",
      "detectedLabel": "Tree",
      "confidence": 0.965,
      "conceptId": "66c78b...",
      "conceptName": "Tree",
      "categoryName": "Nature",
      "translations": [
        {
          "id": "66c79a...",
          "conceptId": "66c78b...",
          "conceptName": "Tree",
          "languageName": "Chakma",
          "text": "বান",
          "pronunciation": "ban"
        }
      ]
    }
    ```

---

## 7. Crowd-Sourced Submissions (`/api/v1/submissions`)

### Submit suggested translation
*   **URL**: `/api/v1/submissions`
*   **Method**: `POST`
*   **Access**: Authenticated (`ROLE_USER`)
*   **Request Body**:
    ```json
    {
      "conceptId": "66c78b...",
      "languageId": "66c758...",
      "suggestedTranslation": "বান",
      "pronunciation": "ban",
      "notes": "Spoken dialect variant"
    }
    ```

### Approve Suggested Translation
*   **URL**: `/api/v1/admin/submissions/{id}/approve`
*   **Method**: `POST`
*   **Access**: Reviewer/Admin (`ROLE_ADMIN` or `ROLE_LANGUAGE_REVIEWER`)
*   **Request Body** (Optional):
    ```json
    {
      "reviewerNote": "Verified by community linguist."
    }
    ```

---

## 8. PDF Generation API (`/api/v1/export`)

### Export Collection Book as PDF
*   **URL**: `/api/v1/export/pdf`
*   **Method**: `POST`
*   **Access**: Owner (`ROLE_USER`)
*   **Request Body**:
    ```json
    {
      "collectionId": "66c9bb...",
      "languageId": "66c758..."
    }
    ```
*   **Response (200 OK)**:
    ```json
    {
      "id": "66c9ff...",
      "fileUrl": "/api/v1/export/files/My_Nature_Book.pdf",
      "fileName": "My_Nature_Book.pdf",
      "fileSize": 18542,
      "createdAt": "2026-08-22T18:05:00"
    }
    ```
