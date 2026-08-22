# Koro — Multilingual & Indigenous Language Platform

Koro is a dynamic, database-driven language preservation and learning platform designed to document, search, and preserve indigenous languages. This backend is built with Spring Boot, Spring Data MongoDB, Spring Security, JWT session management, Google Vision mock classifier, and OpenPDF compilation.

---

## 📂 Project Directory Structure

Here is the structural map of files and packages in the project under `src/main/java/com/koro/app/`:

```text
com/koro/app/
│
├── KoroApplication.java            # Main entry point to run the Spring Boot application.
│
├── config/                         # Configuration files
│   ├── DatabaseInitializer.java    # Seed data loader (creates default users, languages, concepts).
│   ├── MongoConfig.java            # Configures MongoDB auditing (auto-populates timestamps).
│   ├── OpenApiConfig.java          # OpenAPI / Swagger UI setup with JWT bearer security scheme.
│   ├── SecurityConfig.java         # Security filters, access control lists (RBAC), and stateless session.
│   └── StorageConfig.java          # Initializes local folders for image/PDF uploads on startup.
│
├── auth/                           # Authentication & Session package
│   ├── controller/
│   │   └── AuthController.java     # Signup, Login, Token Refresh, Password Reset endpoints.
│   ├── dto/                        # Request/Response payloads (LoginRequest, JwtResponse, etc.)
│   ├── entity/
│   │   └── RefreshToken.java       # MongoDB document representing active refresh token sessions.
│   ├── repository/
│   │   └── RefreshTokenRepository.java # Mongo Repository queries for RefreshTokens.
│   ├── security/
│   │   ├── AuthEntryPointJwt.java  # Custom 401 Unauthorized JSON error response handler.
│   │   ├── AuthTokenFilter.java    # Interceptor validating Bearer JWTs per request.
│   │   ├── CustomUserDetails.java  # Spring Security custom UserDetails wrapper.
│   │   ├── CustomUserDetailsService.java # Service loading UserDetails from the database.
│   │   └── JwtUtils.java           # Helper utilities for signing, parsing, and verifying JWTs.
│   └── service/
│       └── RefreshTokenService.java # Session expiration and cleanup manager.
│
├── user/                           # User Profile package
│   ├── controller/
│   │   └── UserController.java     # Current profile details and password changes endpoints.
│   ├── dto/                        # Profile DTOs (ProfileUpdateRequest, ChangePasswordRequest, etc.)
│   ├── entity/
│   │   ├── Role.java               # Enum representing RBAC roles (USER, ADMIN, REVIEWER).
│   │   ├── User.java               # User document mapping (preferred languages, native, status).
│   │   └── UserStatus.java         # Enum representing account states (ACTIVE, SUSPENDED).
│   └── repository/
│       └── UserRepository.java     # Mongo Repository queries for Users.
│
├── language/                       # Language Management package
│   ├── controller/
│   │   └── LanguageController.java # Public listing and Admin CRUD operations for languages.
│   ├── entity/
│   │   └── Language.java           # Language documents (ISO code, native name, active status).
│   └── repository/
│       └── LanguageRepository.java # Mongo Repository queries for Languages.
│
├── concept/                        # Categories & Concepts package
│   ├── controller/
│   │   └── ConceptController.java  # Group listings and concept dictionary dictionary entries.
│   ├── entity/
│   │   ├── Category.java           # Categorization headers (e.g. Nature, Animals).
│   │   └── Concept.java            # Semantic base concepts (e.g. Tree, Water).
│   └── repository/
│       ├── CategoryRepository.java # Mongo Repository queries for Categories.
│       └── ConceptRepository.java  # Mongo Repository queries for Concepts.
│
├── translation/                    # Translation Dictionary package
│   ├── controller/
│   │   └── TranslationController.java # Search and cross-language translation endpoints.
│   ├── dto/
│   │   ├── TranslationResponse.java   # Mapped translations with phonetic guides.
│   │   └── TranslationSearchRequest.java # Search payload parameters.
│   ├── entity/
│   │   └── Translation.java        # Translation mappings with unique indexes.
│   └── repository/
│       └── TranslationRepository.java # Mongo Repository queries for Translations.
│
├── submission/                     # Crowd-Sourced Translations package
│   ├── controller/
│   │   └── SubmissionController.java # Suggested translations reviews and approval flows.
│   ├── dto/
│   │   ├── SubmissionRequest.java    # Suggestion submission payload.
│   │   └── SubmissionReviewRequest.java # Reviewer notes payload.
│   ├── entity/
│   │   ├── SubmissionStatus.java     # Enum status states (PENDING, APPROVED, REJECTED).
│   │   └── TranslationSubmission.java # User submitted language entries documents.
│   └── repository/
│       └── TranslationSubmissionRepository.java # Mongo Repository queries for Submissions.
│
├── collection/                     # Saved Vocabulary Collections package
│   ├── controller/
│   │   └── CollectionController.java # Digital books items additions and removals.
│   ├── dto/
│   │   ├── CollectionItemRequest.java
│   │   ├── CollectionItemResponse.java
│   │   ├── CollectionRequest.java
│   │   └── CollectionResponse.java
│   ├── entity/
│   │   ├── Collection.java         # Document representing user vocabulary collections.
│   │   └── CollectionItem.java     # Document linking concepts, languages, and chapter names.
│   └── repository/
│       ├── CollectionItemRepository.java # Mongo Repository queries for items.
│       └── CollectionRepository.java # Mongo Repository queries for collections.
│
├── image/                          # AI Vision recognition log package
│   ├── controller/
│   │   └── ImageController.java    # Upload files, trigger AI Vision, fetch scan histories.
│   ├── dto/
│   │   └── ImageRecognitionResponse.java
│   ├── entity/
│   │   └── ImageRecognitionResult.java # Logs of AI labels, confidence scores, and matches.
│   └── repository/
│       └── ImageRecognitionResultRepository.java # Mongo Repository queries for vision logs.
│
├── activity/                       # Auditing & User logs package
│   ├── controller/
│   │   └── ActivityController.java # Fetch audit logs and aggregation charts endpoints.
│   ├── entity/
│   │   ├── ActivityLog.java        # Action metadata, device agents, and IP addresses.
│   │   └── ActivityType.java       # Action enum (LOGIN, TRANSLATION, SAVE_WORD, etc.)
│   ├── repository/
│   │   └── ActivityLogRepository.java # Mongo Repository queries for audit logs.
│   └── service/
│       └── ActivityLogService.java # Contextual logging helper.
│
├── export/                         # PDF book exports package
│   ├── controller/
│   │   └── ExportController.java   # Trigger exports and download PDF binary files.
│   ├── dto/
│   │   └── PdfExportRequest.java   # PDF export parameters payload.
│   ├── entity/
│   │   └── PdfExport.java          # File path links and sizes record documents.
│   ├── repository/
│   │   └── PdfExportRepository.java # Mongo Repository queries for export records.
│   └── service/
│       └── PdfExportService.java   # Document layout compiler using OpenPDF.
│
└── integration/                    # External Vision & File Storage interfaces
    ├── storage/
    │   ├── LocalStorageService.java # Local filesystem storage driver.
    │   └── StorageService.java     # Abstract storage interface.
    └── vision/
        ├── VisionResult.java       # DTO containing vision tags.
        ├── VisionService.java      # Vision classifier interface.
        └── VisionServiceImpl.java  # Mock vision tag builder for out-of-the-box testing.
```

---

## 🚀 Setup & Run Instructions

### Prerequisites
*   **Java**: JDK 21 (configured in toolchain)
*   **MongoDB**: Run a local instance on port `27017` (e.g. via `mongod` or Docker container)

### Run the Application
Start the Spring Boot server using Gradle:
```bash
./gradlew bootRun
```

### Accessing Swagger UI API docs
Once the server is running, you can explore the APIs interactively here:
👉 **[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)**

---

## 🔑 Default Seed Accounts

The application automatically inserts three user profiles with specific roles on startup for rapid verification:

| Role | Email | Password | Access Privileges |
|---|---|---|---|
| **Admin** | `admin@koro.com` | `password` | Administrator CRUD APIs (`/api/v1/admin/**`) |
| **Reviewer** | `reviewer@koro.com` | `password` | Submissions Gateways (`/api/v1/admin/submissions/**`) |
| **Regular User** | `user@koro.com` | `password` | Standard Translate, Search, and Saved Books features |

---

## 📖 API Documentation Reference
For the detailed API endpoints request bodies and response JSON structures, see:
👉 **[`api_documentation.md`](file:///Users/rosdebkoch/IdeaProjects/koro/api_documentation.md)**
