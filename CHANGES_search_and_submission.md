# Changes — translation search accuracy & submission approval

These notes cover everything that changed in the backend so the **website** and **admin panel**
can be updated. Nothing in the database schema changed; the `Translation` model already had a
field for every value we now store.

---

## 1. Translation search (`POST /api/v1/translations/search`)

### What changed
- The query is now **Unicode-normalized (NFC)** and zero-width joiners are stripped before
  matching. Bangla words that were typed with a different but canonically-equivalent code-point
  sequence (a common reason Bangla search returned nothing while English worked) now match.
- The query is matched against **three fields** instead of one:
  1. the translation `text`
  2. the translation `pronunciation`
  3. the parent concept's `name` / `description`
  So `water`, `পানি`, `pani` and `Water` all find the same concept.
- Results are **de-duplicated** by translation id.
- All stored translation `text` / `pronunciation` values are normalized once on server startup
  (see section 4), and every new write is normalized (section 3), so query and data stay comparable.

### Request body (unchanged shape)
```json
{ "query": "পানি", "sourceLanguageId": "<id>", "targetLanguageId": "<id>" }
```
Only `query` is required.

| `sourceLanguageId` | `targetLanguageId` | behaviour |
|---|---|---|
| set | set | match `query` in the source language, return the matching concepts' entries **in the target language** (e.g. type a Bangla word, get the Chakma word) |
| — | set | search inside the target language only |
| — | — | global search across every language |

### Response (unchanged)
`200 OK` with a JSON array of `TranslationResponse` (`id, conceptId, conceptName, categoryName,
languageId, languageName, text, pronunciation, verified, notes, exampleSentence`).

### Frontend action
- **No breaking change** to the endpoint itself. But today the website does **not call this
  endpoint at all** for the dictionary — it downloads every concept + translation and filters
  in the browser. That is why "search with no category" and Bangla searches return nothing.
  See **section 6** for the required website changes.
- The search box can send raw user input as-is — normalization is server-side.
- The endpoint is **public** (`permitAll` on `/api/v1/translations/**`) — do not send an auth
  token requirement; an unauthenticated visitor must be able to search.

---

## 2. Submission approval (`POST /api/v1/admin/submissions/{id}/approve`)

### What changed
- On approval the backend still creates **three dictionary rows** under one concept
  (source word, Bangla, English) — but the **Bangla and English rows now also receive the
  submission's `note` and `exampleSentence`** (previously only the source-language row did).
  `pronunciation` still goes only on the source-word row.
- The concept name (= `englishTranslation`) is Unicode/whitespace-normalized before the
  find-or-create, so `"How are you"` and `"How are you "` no longer create two concepts.
- A blank Bangla/English value never creates an empty row.
- Failure cases that used to throw a `500` now return **`400` with a message**:
  - the submission's source language was deleted since it was submitted
  - the `bn` or `en` language is not configured

### Response — **BREAKING for the admin panel**
Previously: the bare submission object.
Now:
```json
{
  "submission":   { "id": "...", "status": "APPROVED", "reviewedBy": "...", "reviewedAt": "...", "..." : "..." },
  "conceptId":    "6a8ef6eb21d2767e52a00c25",
  "translationsSaved": ["Chakma: বান", "Bangla: গাছ", "English: Tree"]
}
```
**Admin panel action:** read the submission from `response.submission` (not `response`).
`translationsSaved` is a human-readable list you can show as a success toast /
"3 dictionary entries created".

### Reject endpoint — unchanged
`POST /api/v1/admin/submissions/{id}/reject`, `rejectionReason` still required, still `400` if missing.

### Why approval is still required
Submissions stay `PENDING` until a reviewer approves them — the `banglaTranslation` /
`englishTranslation` you enter on the form are stored on the **submission** immediately
(`translation_submissions` collection) but only reach the searchable `translations`
collection on approval. This was kept deliberately (moderation step).

To approve: sign in as a user with `ROLE_ADMIN`, `ROLE_MODERATOR`, or `ROLE_LANGUAGE_REVIEWER`
(seed accounts: `admin@koro.com`, `moderator@koro.com`, `reviewer@koro.com`, password `password`),
then:
```
GET  /api/v1/admin/submissions/pending          # find the id
POST /api/v1/admin/submissions/{id}/approve      # body optional: { "reviewerNote": "..." }
```

---

## 3. Admin translation create / update — normalization only

`POST /api/v1/admin/translations` and `PUT /api/v1/admin/translations/{id}` now
Unicode-normalize (`NFC`, zero-width stripped, trimmed) the `text` and `pronunciation`
before saving. Same for the crowd-submission source word.

**Frontend action:** none. Request/response shapes are unchanged. Be aware the stored
value may be trimmed / normalized versus exactly what was typed.

---

## 4. Startup data migration (automatic, no action needed)

On every application start `DatabaseInitializer`:
- normalizes `text` / `pronunciation` on all existing `translations` rows (logs the count fixed);
- logs a **warning** for any `translations` row missing its `language` or `concept` reference —
  these are invisible to search (the endpoints filter on `language != null && concept != null`).
  Fix or delete those rows in the database. Example seen in this project:
  a row with `text: "বিতা"` and **no `language` field**.

---

## 5. Database model

No schema migration required.

| submission field | where it lands on approval |
|---|---|
| `categoryId` | `concept.category` |
| `sourceLanguageId` + `sourceWord` | source-language `Translation.text` |
| `banglaTranslation` | Bangla `Translation.text` |
| `englishTranslation` | English `Translation.text` **and** `concept.name` |
| `pronunciation` | source-language `Translation.pronunciation` |
| `exampleSentence` | `Translation.exampleSentence` on all three rows |
| `note` | `Translation.notes` on all three rows |

`Translation` fields: `id, concept, language, text, pronunciation, verified, notes,
exampleSentence, createdAt, updatedAt` — unchanged.

---

## 6. Website integration — make the dictionary use the real search endpoint

### The problem today
`KORO-Website` treats search as a client-side substring filter over the full concept list:

| Code | What it does now |
|---|---|
| `src/features/dictionary/hooks.ts` → `useConceptsByCategory` | `GET /concepts` + `GET /translations` (everything), then `c.name.toLowerCase().includes(q)` — **English concept name only**, scoped to one category |
| `src/features/search/hooks.ts` → `useGlobalSearch` | same full download, then `c.name.includes(q) || c.translations.some(t => t.text.includes(q))` — no `pronunciation`, no Unicode normalization |
| `src/lib/api/endpoints.ts` → `translationsApi.search` | already declared, but `targetLanguageId` is **required** and it is marked `{ auth: true }` |

Result: a query with no category, a Bangla query, a pronunciation query, or a source-language
word returns "No words found".

### Contract to build against

`POST /api/v1/translations/search` — **public, no auth token.**

Request body (only `query` required):
```jsonc
{
  "query": "পান",              // raw user input; server normalizes (NFC + trim + strip ZWJ)
  "sourceLanguageId": "<id>",   // optional
  "targetLanguageId": "<id>"    // optional
}
```

| `sourceLanguageId` | `targetLanguageId` | use it for |
|---|---|---|
| — | — | **global dictionary search** (all languages) — this is the no-category case |
| — | set | search within one language |
| set | set | "type a word in language A, get language B" |

Matches, case-insensitively, as a substring, against: translation `text`, translation
`pronunciation`, and the parent concept `name` / `description`.

Response `200`: `TranslationResponse[]`
```jsonc
[
  {
    "id": "6a8c49ff…", "conceptId": "6a8c49fe…", "conceptName": "Tree",
    "categoryName": "Nature & Environment",
    "languageId": "6a8f401d…", "languageName": "Koch",
    "text": "Pan", "pronunciation": "পান",
    "verified": true, "notes": "…", "exampleSentence": null
  }
]
```
`""` / whitespace-only `query` → `400` with body `"Query string is empty"`.

### Required changes in `KORO-Website`

1. **`src/lib/api/endpoints.ts`** — loosen `translationsApi.search`:
   ```ts
   search: (payload: { query: string; sourceLanguageId?: string; targetLanguageId?: string }) =>
     apiClient.post<RawTranslation[]>("/translations/search", payload), // drop { auth: true }
   ```
   (`RawTranslation` already matches `TranslationResponse`.)

2. **New hook** `useDictionarySearch(query, opts?)` (e.g. in `src/features/search/hooks.ts`):
   - debounce `query` ~300 ms; skip the call when trimmed `query` is empty.
   - `translationsApi.search({ query })` for global; add `targetLanguageId` when a language
     filter is active.
   - **group the flat rows by `conceptId`** into `{ conceptId, conceptName, categoryName, translations: RawTranslation[] }` for card display (same shape `ConceptCard` already consumes, minus slugs — derive slugs with the existing `slugify`).
   - `react-query` key: `["translations", "search", query, targetLanguageId ?? null]`.

3. **`src/app/(public)/dictionary/[category]/page.tsx`** — when the search box has a value,
   call `useDictionarySearch(debounced)` and then keep only rows whose `categoryName` matches
   the current category (or show all + a "in other categories" section). When the box is empty,
   keep the existing `useConceptsByCategory` browse list.

4. **`src/features/search/hooks.ts` → `useGlobalSearch`** — replace the concept-substring
   filter with `useDictionarySearch(q)` for the "Concepts & Translations" section. Keep the
   existing client-side filters for the "Languages" and "Categories" sections (those aren't
   part of this endpoint).

5. Delete the stale comment *"The backend has no generic search endpoint"* in both hook files.

### Admin panel (`KORO-Admin`) — approve response changed

`adminApi.submissions.approve` now resolves to
`{ submission: RawSubmission; conceptId: string; translationsSaved: string[] }`, **not**
`RawSubmission`. Read `res.submission` where the code currently reads `res`. `translationsSaved`
is a ready-to-display list (`["Bangla: গাছ", "English: Tree", …]`) for a success toast.
Also handle the new `400` (plain string body) for "source language deleted" / "bn|en not configured".

### Before any of this works: restart the backend
The improvements in sections 1–4 are compiled but a running server started earlier will still
serve the old behaviour. Rebuild and restart (`./gradlew bootRun`, or Rebuild + Rerun in the
IDE). Quick check that the new code is live:
```
curl -s -X POST http://localhost:8080/api/v1/translations/search \
  -H 'Content-Type: application/json' -d '{"query":"tri"}'
```
should return the Tree/English row (matched on its pronunciation `tri:`); on the old build it returns `[]`.
