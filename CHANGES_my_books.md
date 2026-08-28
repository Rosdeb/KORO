# Changes — "My Books" (Collections) & PDF export

Backend changes for the My Books feature. The website (`KORO-Website`) and admin panel
(`KORO-Admin`) consume these; **breaking** items are called out per section.

Verified end-to-end against a running server (create book → add/bulk-add → PATCH item →
rename/reorder chapters → multi-language PDF → public file link).

DB: one new field, `Collection.chapterOrder` (`List<String>`, nullable). No migration needed —
absent means "order chapters by name".

---

## 1. `GET /api/v1/collections` now returns summaries with counts  — BREAKING (shape)

Was: raw `Collection[]` (`id, name, description, createdAt, updatedAt`).
Now: `CollectionSummaryResponse[]`:

```jsonc
[
  { "id": "…", "name": "Travel words", "description": "…",
    "itemCount": 42, "chapterCount": 4,
    "createdAt": "…", "updatedAt": "…" }
]
```

**Website:** the My Books grid can show real word / chapter counts instead of the "Open book →"
fallback. Update the `RawCollection` type / list mapper to read `itemCount`, `chapterCount`.

`POST` and `PUT /collections/{id}` also changed: they used to return the raw entity (which
**included the owner `User` object with its bcrypt password hash**). They now return
`CollectionResponse` (`id, name, description, chapterOrder, createdAt, updatedAt, items`).
`items` is `[]` from create/update.

---

## 2. Editing collection items — new endpoints

### `PATCH /api/v1/collections/{id}/items/{itemId}`  (owner)
Partial update; send any subset:
```jsonc
{ "chapter": "Chapter 2 — Food", "displayOrder": 5, "notes": "formal register", "languageId": "<id>" }
```
- `notes: ""` clears the note.
- `chapter: ""` / blank → reset to `"General"`.
- changing `languageId` → `409` if the book already has that concept in the target language.
- Returns the updated `CollectionItemResponse`.

### `PUT /api/v1/collections/{id}/chapters`  (owner) — bulk rename + reorder
```jsonc
{ "chapters": [ { "from": "General", "to": "Introduction" }, { "from": "Food" } ] }
```
- `to` omitted → no rename, just position.
- The **list order becomes the book's chapter order** (persisted to `Collection.chapterOrder`,
  honoured by `GET /collections/{id}` and the PDF). Chapters not listed are appended, ordered by name.
- Returns the full `CollectionResponse`.

### `POST /api/v1/collections/{id}/items/bulk`  (owner) — add many at once
```jsonc
{ "languageId": "<id>", "conceptIds": ["c1", "c2", "c3"], "chapter": "Intro", "notes": "" }
```
Always `200`:
```jsonc
{ "added": [ /* CollectionItemResponse */ ],
  "skipped": [ { "conceptId": "c2", "reason": "already in collection" },
               { "conceptId": "c9", "reason": "concept not found" } ] }
```
**Website:** the "Add words" dialog can fire one bulk call instead of one request per word.

### `displayOrder` on single add
`POST /collections/{id}/items` now defaults `displayOrder` to *end of that chapter*
(`max + 1`) instead of `0`, so a freshly built book already has a sensible order.

---

## 3. Duplicate add is now `409`, not `400`

`POST /collections/{id}/items` returns **`409 Conflict`** (`{ "message": "Error: Vocabulary
already saved in this collection." }`) when the concept+language is already in the book.
The website can treat `409` as "already added" without string-matching the message.

---

## 4. `GET /api/v1/collections/{id}` — ordering

`items` come back sorted by `(chapter per chapterOrder, displayOrder, concept name)`.
The response includes the effective `chapterOrder` (author order + any leftover chapters by name),
so every client renders the book the same way. Also returns `404` (not a 500) for a missing id.

`CollectionItemResponse` gained **`exampleSentence`** (from the item's translation).

---

## 5. PDF export (`POST /api/v1/export/pdf`) — BREAKING (request + response)

### Request
```jsonc
{
  "collectionId": "…",
  "languageIds": ["<chakma>", "<bn>", "<en>"],  // NEW — one printed column per language, in order
  "languageId": "<bn>",                          // still accepted (legacy single-language alias)
  "headwordLanguageId": "<chakma>",              // optional — language that leads each entry (default: first)
  "includeExampleSentences": true                // optional, default true
}
```
At least one of `languageIds` / `languageId` is required → `400` with a message otherwise.

### Response — was the raw `PdfExport` entity (leaked owner password hash via nested `user`)
Now `PdfExportResponse`:
```jsonc
{ "id": "…", "collectionId": "…", "collectionName": "…",
  "fileName": "Travel_words.pdf", "fileUrl": "/api/v1/export/files/<uuid>.pdf",
  "fileSize": 1915, "createdAt": "…" }
```
`GET /export/history` and `GET /export/{id}` return the same DTO (list / single).

### PDF content fixes
- **Chapters** print in `chapterOrder` (was `HashMap` = random order).
- **Words** within a chapter follow `displayOrder` then name (was insertion order).
- **Multiple languages** — one labelled line per language per entry; missing translation shows `—`.
- **Headword** — configurable via `headwordLanguageId` (was always the English concept name).
- **Example sentences** — printed under each entry unless `includeExampleSentences:false`.

### Fonts — ACTION REQUIRED for non-Latin
The PDF used Helvetica (no Unicode) so Bangla/Chakma exported as blank boxes. The code now
loads embedded Noto fonts per script, **but the .ttf files are not in the repo**. Drop these
into `src/main/resources/fonts/` (see the README there):
`NotoSans-Regular.ttf`, `NotoSans-Bold.ttf`, `NotoSansBengali-Regular.ttf`,
`NotoSansBengali-Bold.ttf`, `NotoSansChakma-Regular.ttf` (all SIL OFL, from Google Noto).
Until then non-Latin still fails and a warning is logged at startup.

---

## 6. Publish = public PDF link  (the "publish" scope chosen was: PDF share link only)

`GET /api/v1/export/files/{filename}` is now **public** (`permitAll`). The filename is an
unguessable UUID, so `fileUrl` from an export response is a shareable "published book" link
that opens without signing in. Served `Content-Disposition: inline` (previews in the browser);
append `?download=1` to force a save.

Not included (would be the "full public books" feature): `Collection.visibility`,
publish/unpublish endpoints, a public book catalogue, moderation of published books.

---

## 7. Admin panel (`KORO-Admin`)

- `adminApi` has no collection/export calls today — nothing to change there for items 1–4.
- If the admin panel ever lists exports, use the new `PdfExportResponse` shape (no `user`).

---

## Before this is live: restart the backend
Changes are committed/compiled but the running server serves the old code until restarted
(`./gradlew bootRun` or Rebuild + Rerun in the IDE).
