# Challenge Platform Backend

Spring Boot API for the public Code Report coding challenge platform.

## Requirements

- Java 17+
- Maven 3.9+
- PostgreSQL

## Local setup

Copy `.env.example` to `.env` and provide local values. `.env` is ignored by Git.

```env
DB_URL=jdbc:postgresql://localhost:5432/challenge_platform
DB_USERNAME=challenge_app
DB_PASSWORD=<local-password>
IDENTITY_SALT=<long-random-local-value>
JUDGE_BASE_URL=<judge-api-base-url>
JUDGE_CPU_TIME_LIMIT=1.0
SERVER_PORT=8090
```

Initialize the local database from the repository root:

```powershell
psql -U postgres -d challenge_platform -f db/01_schema.sql
psql -U postgres -d challenge_platform -f db/03_roles.sql
psql -U postgres -d challenge_platform -f db/02_seed_questions.sql
```

Start the API:

```powershell
mvn spring-boot:run
```

The local API is available at `http://localhost:8090/api/v1`.

Interactive Swagger documentation is available locally at
`http://localhost:8090/api/swagger-ui.html`. The OpenAPI JSON document is served from
`http://localhost:8090/api/v3/api-docs`.

## Build and test

```powershell
mvn clean verify
```

Build the container after Maven packaging:

```powershell
docker build -t challenge-platform-backend .
```

## API

- `GET /api/v1/questions` — returns one randomly selected active question as a complete public detail object (prompt, starter code, and sample test cases), so no follow-up request is needed. Returns 404 if none are active. Selection happens in PostgreSQL; the response uses `Cache-Control: no-store`. Hidden test cases and reference solutions remain private.
- `GET /api/v1/questions/{slug}`

### Private admin statistics

The same bearer session issued by `/api/v1/admin/auth/login` protects all of:

- `GET /api/v1/admin/stats`: unique candidates with saved attempts, total attempts,
  average candidate score, and pass-rate groups aggregated over all submissions.
- `GET /api/v1/admin/stats/candidates?bucket=all&search=&size=25`: paginated
  candidate contact details, acquisition campaign and overall challenge summary. Search matches name,
  email, or phone literally, case-insensitively. Bucket keys: `all`, `zero`, `low`,
  `quarter`, `half`, `high`, `perfect`, `unavailable`.
- `GET /api/v1/admin/stats/candidates/{id}?page=0&size=10&day=2026-09-01&timeZone=Asia/Kolkata`:
  candidate information, performance and paginated attempt history for that local day,
  newest first. An optional `asOf` fixes the snapshot across history/export pages.
  Bounds are local midnight inclusive to next midnight exclusive, including DST.
  Existing candidates with no attempts that day return an empty history and zero totals.
  Omitting `day` preserves lifetime access for API clients; the admin UI always selects one day.
- `GET /api/v1/admin/stats/candidates/{id}/attempts/{attemptId}`: full saved code,
  score, duration, metadata, question/reference solution, and per-test-case results.
  The attempt must belong to that candidate or the response is 404.

Pass-rate groups are 0%, >0–25%, >25–50%, >50–75%, >75–<100%, and 100%; missing
denominators are classified separately. Groups use unrounded ratios; displayed
percentages are rounded to two decimal places. Group candidate shares use the
total unique candidate count, not the submission count. Inactive questions remain
visible in history. The question bank is not versioned; question content and test
inputs/expected outputs reflect its current values, while code and result records
are saved per attempt. Attempt-history pages are zero-based; maximum size is 100.

Overview and candidate-list filters: `search`, `campaign` (exact case-insensitive
acquisition tag), inclusive `startDate` / `endDate` (ISO dates), `timeZone` (IANA,
default UTC), and inclusive `minPercent` / `maxPercent` (0–100). Dates filter
submissions before candidate aggregation. Pass rate = sum(passed) / sum(total).
Candidate score totals/averages include every selected submission; the headline
average gives each candidate equal weight. No dates means all history.

Candidate batches use `afterId` (the preceding response's `nextCursor`) ordered
by descending candidate ID. Null `nextCursor` means stop, including empty results.
Pass the same `asOf` ISO instant to the overview and every list batch to exclude
submissions arriving after that view's snapshot. Filters are validated and bound
SQL parameters; no candidate-submitted text is interpolated into SQL.

The frontend caches authenticated GET responses in tab memory for at most one hour,
with automatic deletion and earlier clearing on logout/admin-session expiry.
Server responses remain `Cache-Control: no-store` to prevent browser/proxy caching.
Public APIs are unchanged and never expose these admin aggregates.

These read-only endpoints use `Cache-Control: no-store`, parameterized queries,
and existing application-role permissions. No new DB schema migration is needed.
Never reuse these DTOs for candidate-facing endpoints. AWS still needs the existing
admin schema/session migrations and a provisioned admin account if not already set up.

Local PostgreSQL integration tests are opt-in: set `CHALLENGE_STATS_DB_TESTS=true`
before `mvn test`. They verify the local server/database before inserting fixtures;
all fixture rows roll back (identity sequences may advance). Default tests need no DB.
- `POST /api/v1/run`
- `POST /api/v1/submit`
- `GET /api/v1/leaderboard`
- `GET /api/v1/stats`

The public leaderboard returns only `rank` and `displayName`. Successful submissions
return only a confirmation `message`; scores, timings, and grading results are stored
internally in PostgreSQL and are not included in candidate-facing responses.

Swagger uses same-origin, relative server URLs, so it works locally and behind the AWS
reverse proxy without storing infrastructure addresses or credentials in the codebase.

## Environments and security

Local development reads configuration from the ignored `.env` file. The AWS dev environment
injects database credentials and the identity salt from Secrets Manager at runtime.

The application connects with the least-privilege `challenge_app` database role. That role can
read the question bank and write candidate/attempt data, but it cannot administer the database.
Never commit credentials, salts, AWS identifiers, or private service endpoints.

## Dev delivery pipeline

Pushes to `dev` trigger the non-production pipeline:

1. Source from the backend `dev` branch.
2. Run `mvn clean verify` in CodeBuild.
3. Build and publish an ARM64 container image.
4. Deploy the image to the non-production ECS service.

Deployment commands are defined in `buildspec.yml`; runtime secrets remain outside Git.

## Admin daily-limit reset (local setup)

The admin page is `/reset-admin-aria`. It supports one email or a batch of up to
100 emails, with explicit confirmation. Unauthenticated visitors go to
`/reset-admin-aria/login`. The login API verifies the admin email/password and
issues a random 30-minute bearer session. Only a SHA-256 token hash is stored in
`challenge_platform_admin.admin_session`; the browser keeps the opaque token in
session storage for the current tab/environment, never the password. Each admin
request checks server expiry and account enabled status. Logout revokes the token.

Run these SQL files as the local database owner, in order:

```powershell
psql -h 127.0.0.1 -U postgres -d challenge_platform -v ON_ERROR_STOP=1 -f db/04_admin_schema.sql
psql -h 127.0.0.1 -U postgres -d challenge_platform -v ON_ERROR_STOP=1 -f db/05_seed_local_admin.sql
psql -h 127.0.0.1 -U postgres -d challenge_platform -v ON_ERROR_STOP=1 -f db/06_admin_sessions.sql
```

The separate `challenge_platform_admin` schema contains `admin_user` (bcrypt
passwords, enabled flag and timestamps) and `daily_limit_reset_audit` (actor,
candidate ID, date, removed-lock count and timestamp). The application role can
read admin accounts and append audit events, but cannot edit accounts or erase audits.
The local-only seed creates `admin@codereport.com` with the supplied development
password. Re-running it does not overwrite an existing account. It refuses remote
database connections. Never deploy these local credentials to AWS; provision unique
credentials separately, require HTTPS, and apply authentication rate limiting before exposure.

`POST /api/v1/admin/daily-limit/reset` accepts
an `Authorization: Bearer <session-token>` header and
`{"emails":["candidate@example.com"],"confirmed":true}`. It uses the same email
normalization as submissions and clears both EMAIL and PHONE locks for matching
candidates on the server's current date. Results distinguish `RESET`, `NO_LIMIT`
and `NOT_FOUND`. Attempts, scores, candidates and historical locks are preserved.
The reset is transactional with its audit entries and coordinates with in-flight
submissions via PostgreSQL advisory locks. A busy submission can return HTTP 409;
no reset is applied in that case. Candidates should reload the challenge after reset.

Authentication endpoints: `POST /api/v1/admin/auth/login` accepts explicit HTTP
Basic credentials and returns `token`, `email`, `expiresAt`. `POST /auth/session`
and `POST /auth/logout` under the same admin prefix require the bearer token.
Basic credentials cannot be used directly on the reset endpoint.
