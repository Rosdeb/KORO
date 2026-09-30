# Email OTP authentication

The backend uses the official Resend Java SDK: https://resend.com/java.
Set these process environment variables before starting the application:

```sh
export RESEND_API_KEY='your-new-resend-key'
export RESEND_FROM='Koro <auth@your-verified-domain.com>'
```

Use a verified sending domain in Resend. Never put the API key in frontend code or commit it. Rotate the key previously shared in chat. No live email was sent during implementation.

## Frontend flow

All endpoints below use JSON and the `/api/v1/auth` prefix.

1. `POST /register` with the existing registration fields:
   `{"name":"Example","email":"person@example.com","password":"your-password","nativeLanguage":"bn","preferredLanguage":"en"}`.
   A `202` means the request was accepted; it does **not** mean an account exists yet.
2. Show an OTP input. `POST /verify-email` with
   `{"email":"person@example.com","otp":"123456"}`.
   On `200`, take the user to login. OTPs are strings to preserve leading zeros.
3. To resend, `POST /resend-verification` with `{"email":"person@example.com"}`.
4. For recovery, `POST /forgot-password` with `{"email":"person@example.com"}`.
5. Show OTP and new-password inputs. `POST /reset-password` with
   `{"email":"person@example.com","otp":"123456","newPassword":"your-new-password"}`.
   On `200`, take the user to login.

**Breaking changes:** registration now requires verification; reset-password accepts email + otp + newPassword instead of token + newPassword. Forgot-password never returns a code/token. Existing accounts continue to work without retroactive verification.

## Expiry and storage

`email_challenges` holds the email, BCrypt-hashed OTP, deadline, counters and (for registration) the pending profile with a BCrypt-hashed password. The `users` collection is populated only after successful verification, always with ROLE_USER.

Challenges expire 15 minutes after the first request. Resends replace the code but preserve the original profile, expiration and attempt budget. To change pending registration details, wait for expiration and register again. Each window allows five sends, five verification attempts, and at least 60 seconds between sends. Registration and recovery codes are purpose-separated and atomically consumed once. Limits persist across restarts and multiple server instances.

The startup configuration creates a MongoDB TTL index on `expiresAt` even if automatic index creation is disabled. MongoDB deletes expired rows asynchronously, normally on its periodic TTL sweep; physical removal is not guaranteed at exactly 15 minutes. API verification rejects expired codes immediately. Expired pending email addresses can register again even before the sweep. No scheduler or manual cleanup is required while MongoDB is running. Database backups and Resend delivery records follow their own retention policies.

The startup configuration also ensures the existing unique users.email index. Resolve any pre-existing duplicate emails before rollout if this index does not already exist. New email addresses are lowercased and login/recovery lookups are case-insensitive; audit legacy addresses differing only by case before rollout.

## Errors and operations

- `400`: invalid, expired, exhausted, or already-used OTP; request a fresh registration/recovery after the window if exhausted.
- `429`: cooldown/send/attempt limit reached.
- `503`: email configuration missing or delivery failed. Failed delivery retains the temporary row and cooldown; retry after 60 seconds. There is no automatic delivery retry.
- Registration/recovery return generic acceptance messages for already-existing/missing accounts; conditional throttling and delivery latency may still disclose account state.

Successful password reset revokes refresh tokens. Already-issued access JWTs remain valid until their existing expiry. If immediate session termination is required, add token version checks or an access-token denylist.

Apply ingress rate limiting to the public authentication endpoints in production, including limits per client IP, to limit abuse across many email addresses. The implemented limits are per email and purpose.

Challenge consumption and account/password writes use separate MongoDB operations. If a database failure occurs after consumption, the user must request a fresh code; codes fail closed and cannot be replayed. The implementation does not require MongoDB multi-document transactions.

## Tests

`./gradlew test --tests '*EmailVerificationServiceTest'`

These unit tests mock MongoDB and Resend and never send emails. Validate TTL deletion and end-to-end delivery against a separate staging database and a verified Resend sender before deployment.
