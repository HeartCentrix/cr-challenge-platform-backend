# Challenge Platform Backend

## Mass-input activity checkpoints

Apply `db/08_activity_checkpoints.sql` as the **challenge_platform** database owner
before deploying this backend, then deploy the frontend. It is additive and contains
no credentials. Local migration has no effect on AWS. Migration 07 is still required.

`POST /api/v1/activity-checkpoints` accepts changed editor code and compact activity
summaries. A random per-page UUID is a write/claim capability (only its SHA-256 hash
is stored), not verified candidate identity. There is no public read endpoint.
`activityToken` on submission attaches the history to that attempt atomically and
seals further writes. Historical/old-client submissions have no checkpoints.
Only authenticated candidate-attempt admin details expose history, including server
receipt timestamps, final-code comparison and intervals longer than 90 seconds.

The frontend flags edits inserting 80+ characters, or a one-second burst of 80+
characters with little corresponding typing. Starter loading and undo/redo are
excluded. Counts, insertion size and event offsets are saved with final activity.
These are **unverified review signals**, never an automatic rejection or a claimed
DevTools paste count. Legitimate formatting, completion and assistive input can
trigger them. Gaps/mismatches can be offline/idle time or unsaved final edits.

Changed state is batched every 30 seconds, with a final best-effort flush (maximum
1.5-second network wait). Idle state makes no request. Checkpoints contain editor
code, not OS clipboard contents, literal keystrokes or personal-detail fields.
Failures never stop grading; retry payloads are immutable and sequence-numbered.
Each session allows at most 120 checkpoints during its first hour, separated by at
least five seconds (exact duplicate retries are safe). Code is capped at 32,000
characters and each compact report at 100 notable events / 20,000 JSON characters.
Oversized editor code skips checkpoints but remains submittable.

The application permits 120 checkpoint requests/IP/minute/instance with bounded
rate-limit memory. Configure an edge/WAF limit for aggregate/distributed abuse
before public rollout; the local guard alone is not DDoS protection. Preserve the
trusted-proxy configuration so unrelated candidates are not grouped by proxy IP.
Abandoned, unlinked histories expire after 24 hours (hourly cleanup). Attached
history is retained with the attempt and cascades on attempt deletion. Before
rollout, ensure candidate-facing assessment/privacy terms disclose editor-history
collection and define the retention/access policy; admin flags require human review.

Verification: `CHALLENGE_STATS_DB_TESTS=true` enables rollback-only local PostgreSQL
checkpoint/submission/admin integration tests; never point these at the dev database.

## Candidate IP map

`GET /api/v1/stats` now also returns `activityMap: {status, points}`. Each point has
only `latitude`, `longitude` and `candidates`. No IPs, names, emails, IDs or exact city
coordinates are public. Each candidate counts once using their latest submission IP.
Locations are resolved offline with DB-IP City Lite, grouped into half-degree cells
and cached for five minutes per backend instance. The frontend reuses its existing
stats request and snaps coarse coastal locations onto the original US land geometry.
Unknown/private IPs, non-US candidates, Alaska and Hawaii are not shown on this
continental-US map. Empty/unavailable results never create decorative activity dots.
IP geolocation is approximate: VPNs, shared networks and database errors affect it.

See `geoip/README.md` for the pinned database release, checksums and attribution.
The binary is git-ignored and loaded locally; the Docker build includes a verified
copy for deployment, with no per-candidate external lookup or paid API dependency.
Memory-mapped lookups and the aggregate cache add no calls to submission grading.
No database migration or candidate-record backfill is required.

For local/direct access, `TRUSTED_PROXY_HOPS=0` ignores spoofable forwarded headers.
The AWS Docker image uses `2` for the existing restricted CloudFront -> ALB -> backend
chain and selects the visitor from the right of X-Forwarded-For, ignoring any
user-prepended addresses. Direct origin access must remain restricted; change the
setting if that topology changes. Old stored IPs cannot be retroactively authenticated.
Set `CHALLENGE_GEOIP_TESTS=true` to exercise the downloaded database during tests.

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

## Editor activity telemetry

Before starting the updated backend, apply `db/07_editor_activity.sql` as the owner of
the **challenge_platform** database. The migration adds one nullable activity column to
`challenge_platform.attempt`; it does not alter existing candidates, scores or daily limits.
Apply this migration before the backend deployment, then deploy the frontend.

`POST /api/v1/submit` accepts optional `editorActivity` (version 1), validated recursively:
question slug must match the submitted question, counters cannot be negative, and the
timeline is capped at 5,000 categorized events with ordered offsets within a 24-hour window.
It is saved atomically with the attempt and returned only by the authenticated admin
attempt-detail endpoint. Historical/missing reports are NULL, not zero. Public responses
still contain only the acknowledgement; this feature does not change grading.

Reports contain key categories and timing, not literal typed keys or clipboard contents.
They are unverified client observations: DevTools and custom clients can bypass or forge
them. Unexplained model changes are review signals, not proof of cheating. There is no
per-keystroke API, background beacon or abandoned-session record. Deleting an attempt
removes its telemetry. The frontend's `EDITOR_ACTIVITY.md` documents scope and limitations.

Tests: `mvn --no-transfer-progress test`; set `CHALLENGE_STATS_DB_TESTS=true` and a local
challenge datasource to also run rollback-only PostgreSQL submission/storage tests.
