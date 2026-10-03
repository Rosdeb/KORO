# Concept detail performance

`GET /api/v1/concepts/{id}` now fetches the concept and its category in one
MongoDB aggregation request. Previously, `findById` loaded the concept and
Spring's eager `@DocumentReference` resolution fetched the category separately.

The pipeline matches the indexed concept `_id` first, joins `categories` by its
indexed `_id`, and preserves concepts with absent or deleted categories. A
dedicated response DTO keeps the existing fields (`id`, `name`, `description`,
`category`, `referenceImage`) without triggering reference resolution during
mapping. Existing write APIs and their reference storage format are unchanged.

No cache or data migration is required. This reduces database network round
trips; it does not establish a particular response-time improvement without a
production measurement. Authenticated requests still perform the existing user
lookup in the authentication filter.

## Validation

Run `./gradlew test --tests 'com.koro.app.concept.*'`.
Tests cover ObjectId and string ID mapping, the single-request lookup pipeline,
the JSON response fields including nulls, missing categories, and missing concepts.
These are mapping and unit tests, not a live MongoDB benchmark.

## Measure after deployment

Temporarily enable
`logging.level.com.koro.app.concept.service.ConceptReadService=DEBUG`.
The service logs database request duration, including result mapping. It excludes
authentication and client-to-backend network time. Disable DEBUG after measuring.

Compare several consecutive requests to the same ID, both anonymously and with
authentication. Record the first request after inactivity separately to identify
Render startup delays. Compare database duration with browser request duration.
If database time remains high, check the Render and MongoDB Atlas regions and
Atlas query execution statistics before changing instance sizes or adding caching.
