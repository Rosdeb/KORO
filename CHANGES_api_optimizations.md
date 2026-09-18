# API Optimizations & Changes Log

This document tracks recent changes made to the backend APIs to improve performance. Please review these changes to ensure your frontend (website/dashboard) correctly handles the updated responses.

## 1. Translations API Changes

### **New Endpoint: Translations Count**
Added a fast endpoint to get the total number of translations without fetching the actual data. Use this on the admin dashboard instead of downloading the whole array!

*   **Endpoint:** `GET /api/v1/translations/count`
*   **Response:** `150` (Returns a raw number)

### **Updated Endpoint: Paginated Translations**
The main translations API was fetching every record in the database, causing severe slowdowns. It now supports optional pagination.

*   **Endpoint:** `GET /api/v1/translations`
*   **Query Parameters added:** `page` (e.g., 0) and `size` (e.g., 20)

**Important Frontend Change:**
If you pass `page` and `size`, the response format changes from a flat array to a paginated object.

*Old response (still works if you don't pass page/size):*
```json
[
  { "id": "1", "text": "Apple", ... },
  { "id": "2", "text": "Banana", ... }
]
```

*New response (when passing `?page=0&size=20`):*
```json
{
  "content": [
    { "id": "1", "text": "Apple", ... },
    { "id": "2", "text": "Banana", ... }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1500,
  "totalPages": 75,
  "hasNext": true,
  "hasPrevious": false
}
```
**Action Required:** Update your frontend table/list components to read from `response.data.content` instead of just `response.data` when using pagination.

---

## 2. Database Indexing (Performance Boosts)

Several fields across the database have been indexed to drastically speed up search queries and admin dashboard loading times. You don't need to change any frontend code for these, but you will notice faster load times!

**Indexed Fields:**
*   **`Translation`**: `text`, `pronunciation`, `concept`, `language`
*   **`TranslationSubmission`**: `status`, `submittedBy`, `reviewedBy`, `category`, `sourceLanguage`
*   **`User`**: `roles`, `status`
*   **`Concept`**: `category`
*   **`ActivityLog`**: `user`, `activityType`, `createdAt`

**Action Required:** None for the frontend. (If you wiped your local database recently, simply restart the Spring Boot backend and it will automatically build these indexes).
