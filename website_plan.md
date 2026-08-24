# Koro — Website Plan

A plan for the website that sits on top of the existing Koro backend (Spring Boot + MongoDB, JWT auth). Covers the site map, core user flows, page-to-endpoint mapping, recommended stack, the admin dashboard, and a build order. Every page below maps to an endpoint that already exists in [`api_documentation.md`](api_documentation.md) — nothing here requires new backend work.

---

## 1. Who the site serves

The API already models four roles. The site's structure should map directly onto them.

| Audience | Role | What they do |
|---|---|---|
| Visitor | *(none)* | Learns what Koro is, browses the language list and dictionary, registers. |
| Learner / contributor | `USER` | Translates, searches, scans objects, saves words into personal books, suggests translations. |
| Language reviewer | `LANGUAGE_REVIEWER` | Everything a user can do, plus a queue of pending community submissions to approve/reject. |
| Platform operator | `ADMIN` | Manages languages, concepts, users, and moderation from a separate dashboard. |

---

## 2. Site map

### Public site (no auth required)

| Page | Route | Notes |
|---|---|---|
| Home | `/` | Mission, a live search box, featured languages. |
| Explore Languages | `/languages` | Every active language — native name, region, ISO code. |
| Language detail | `/languages/[code]` | One language's story, region, concept coverage. |
| Dictionary browser | `/dictionary` | Categories → concepts, browsable without an account. |
| About / Mission | `/about` | Why indigenous-language preservation, how review works. |
| Register / Login | `/register`, `/login` | Also: forgot-password, reset-password. |

### App (role: user, JWT required)

| Page | Route | Notes |
|---|---|---|
| Dashboard | `/app` | Today/week/month activity summary, quick actions. |
| Translate & Search | `/app/translate` | Pick source → target language, search a concept. |
| Scan an Object | `/app/scan` | Camera/upload → detected label → concept → translations. |
| Scan history | `/app/scan/history` | Past recognitions with confidence scores. |
| My Books | `/app/books` | Personal vocabulary collections, list + create. |
| Book reader | `/app/books/[id]` | Chapters, items, notes; add/reorder/remove words. |
| Export as PDF | `/app/books/[id]/export` | Choose a language, generate, download; export history. |
| Suggest a Translation | `/app/submissions/new` | Propose a word for a concept + language, add pronunciation. |
| My Submissions | `/app/submissions` | Track pending/approved/rejected, with reviewer notes. |
| My Activity | `/app/activity` | Filterable log + charts: today, week, month, year, custom range. |
| Profile & Settings | `/app/settings` | Name, avatar, native/preferred language, password. |

### Admin dashboard (role: admin / reviewer, separate shell, RBAC-gated)

| Page | Route | Notes |
|---|---|---|
| Overview | `/admin` | Platform totals and trend charts. |
| Users | `/admin/users` | Search, suspend, change roles. |
| Languages | `/admin/languages` | Add/edit/disable, native name, ISO code, region. |
| Categories & Concepts | `/admin/concepts` | Concept CRUD, reference images, category grouping. |
| Translations | `/admin/translations` | Official entries per concept × language. |
| Submission Review | `/admin/submissions` | Pending queue → approve/reject with a note. |
| Image Recognition Logs | `/admin/vision-logs` | Detected labels, confidence, match rate over time. |
| Collections oversight | `/admin/collections` | Usage patterns, most-saved concepts. |
| PDF Exports | `/admin/exports` | Export volume, file sizes, storage usage. |
| Activity & Audit | `/admin/activity` | System-wide log, filter by user/type/date. |
| Settings | `/admin/settings` | Seed data, storage paths, vision provider config. |

---

## 3. Core flows

**Learn a word**
```
Search or scan → See translation + pronunciation → Save to a book
  → Organize into chapters → Export as PDF
```

**Contribute a translation**
```
User submits a word → Status: pending → Reviewer opens queue
  → Approve or reject + note → Becomes official (if approved)
```

**Identify an object**
```
Photo captured/uploaded → Vision service labels it → Matched to a concept
  → Translations shown in every active language
```

---

## 4. Key pages mapped to endpoints

| Page | Primary endpoint | Notes |
|---|---|---|
| Translate & Search | `POST /translations/search` | Language selectors from `GET /languages` |
| Dictionary browser | `GET /categories` | Category tiles drill into concepts |
| Scan an object | `POST /images/recognize` | Multipart upload; returns label + matched concept + translations |
| My Books | `GET/POST /collections` | Chapters via `POST /collections/{id}/items` |
| Export as PDF | `POST /export/pdf` | File URL returned; history via `GET /export/history` |
| Suggest a Translation | `POST /submissions` | Status starts `PENDING` |
| Submission Review (reviewer) | `POST /admin/submissions/{id}/approve|reject` | Gated on `ROLE_ADMIN` or `ROLE_LANGUAGE_REVIEWER` |
| My Activity | `GET /activity`, `GET /activity/statistics` | Date-range query params drive the chart |
| Login / Register | `POST /auth/login`, `POST /auth/register` | Refresh via `POST /auth/refresh`, silent-refresh on 401 |

---

## 5. Recommended stack

The API is already versioned and stateless (JWT + refresh tokens), so the frontend is a free choice.

| Layer | Choice | Why |
|---|---|---|
| Framework | **Next.js** (React, App Router, TypeScript) | Server-rendered public pages for SEO; client-rendered app/admin behind auth. |
| Data & auth | **TanStack Query** + JWT refresh | Access token in memory, refresh token in an httpOnly cookie via a Next.js route handler that proxies `/auth/refresh`. |
| Styling | **Tailwind CSS** + Radix primitives | Fast to build the dictionary-card / table-heavy admin UI without a heavy design system. |
| Charts | **Recharts** (or visx) | Drives both the user activity dashboard and admin statistics off the same activity endpoints. |
| Internationalization | **next-intl** for UI chrome | English/Bangla interface language — kept separate from the indigenous-language *content* being translated. |
| Hosting | Next.js app + Spring Boot API behind one reverse proxy (Nginx, Docker) | Keeps cookies and CORS simple in production. |

---

## 6. Admin dashboard, in detail

This is a moderation tool first, a CRUD panel second — the approval queue is the piece the whole preservation model depends on.

- **Overview** — stat tiles (users, languages, concepts, translations, pending approvals, today's activity) plus trend charts for daily/monthly users, translations, and language usage, from `/activity/statistics` and simple resource counts.
- **Submission review queue** — one card per pending submission: concept, target language, suggested text + pronunciation, submitter, date. Approve/reject inline with an optional reviewer note, no page navigation required per item.
- **Language & concept management** — table + slide-over form pattern for both. Concepts show attached translations per language inline, so a reviewer can see coverage gaps at a glance.
- **Access control** — the dashboard shell reads the JWT's roles claim; nav items and routes are hidden (not just disabled) for anything the signed-in role can't reach, mirroring the API's own `ROLE_ADMIN` / `ROLE_LANGUAGE_REVIEWER` gates.

---

## 7. Build phases

The backend is already built end-to-end. What's left is the frontend, sequenced so each phase ships something usable rather than a big-bang launch.

- **Phase 0 — Backend API** ✅ *Live* — Spring Boot + MongoDB, JWT auth, all endpoints above already exist and are documented via Swagger UI.
- **Phase 1 — Public site & auth** — `/`, `/languages`, `/dictionary`, `/login`, `/register`.
- **Phase 2 — Core app** — `/app`, `/app/translate`, `/app/settings`.
- **Phase 3 — Books & PDF export** — `/app/books`, `/app/books/[id]/export`.
- **Phase 4 — Image recognition** — `/app/scan`, `/app/scan/history`.
- **Phase 5 — Community submissions** — `/app/submissions`, `/admin/submissions`.
- **Phase 6 — Admin dashboard** — `/admin`, `/admin/users`, `/admin/languages`.
- **Phase 7 — Polish** — script/font coverage for indigenous languages, accessibility pass, activity charts, rate-limit-aware error states.

---

## 8. Design notes worth deciding early

- **Script rendering** — languages like Chakma and Marma need dedicated Unicode fonts (e.g. Noto Sans Chakma). The body font stack must fall through to a script-specific face per language, not assume Latin/Bangla coverage.
- **Two languages, not one** — the UI's own display language (English/Bangla toggle) is separate from the indigenous language being looked up. Keep the two selectors visually distinct so users don't confuse "app language" with "translate to."
- **Low-bandwidth users** — many indigenous-language communities are in low-connectivity regions. Treat the app shell as a PWA with offline caching for saved books, and keep the scan flow tolerant of slow uploads.
- **Trust in the review queue** — because unverified submissions are explicitly kept separate from official translations, the UI should always show a visible "community-submitted, pending review" badge until a submission is approved — never blend the two visually.
