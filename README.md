# Challenge Platform Backend

## AI-used marker review

The private admin attempt response derives `aiMarkerDetected` from the exact saved
source code. The candidate table checks for the same marker in any submitted answer
within its date/time-zone and `asOf` snapshot. A match is displayed as `AI-used` in
the table, attempt details and Excel exports. Scores and submission acceptance are
unchanged; this is a review signal, not proof of cheating. No marker does not rule
out AI use, and copying or deliberately inserting it can cause a match.

The frontend requests a harmless Java block comment containing exactly 64 soft
hyphens (U+00AD). These characters normally render invisibly and survive both UTF8
and the local WIN1252 database. `AiSourceMarker` and `frontend/public/app.js` use
this v1 signature. Match the full comment, not arbitrary invisible characters. Detection uses stored
source, not a client-supplied flag. Existing source storage preserves the marker;
no database migration is required. Both JavaScript and backend signatures must stay
in sync. Retain older signatures when adding future versions.

This is best effort: screenshot-based tools cannot read hidden DOM text, models
can ignore the instruction, and editors or tools may reveal or strip invisible
characters. The application never adds the marker to starter code, drafts or
submitted code itself. Only the hidden question instruction contains it. No public
candidate API exposes the detection flag.

## Admin candidate regions

Apply `db/10_candidate_regions.sql` after migration 09 before deploying this backend.
It adds a nullable state classification to attempts without changing original IPs or
scores. New session answers resolve their region through the existing offline DB-IP
database. A bounded worker backfills up to 200 historical/unresolved attempts every
minute (first run five seconds after startup); disable it in fixture tests with
`challenge.regions.backfill-enabled=false`. Missing geolocation files leave entries
pending for a later restart with the database available. Unknown/private/missing IPs
are not guessed. Resolved classifications remain a stored lookup snapshot.

Authenticated overview and candidate-list endpoints accept `region=TX` (any of the
50 US state codes or DC), `NON_US`, `UNKNOWN`, or empty for all. Invalid values return
400. Each candidate's region is from their latest submission **within the selected
dates and asOf snapshot**, ordered by timestamp then attempt ID. The region selects
candidates; all their submissions in that scope still contribute to performance.
Filtering precedes cursor pagination and applies equally to totals and charts.
Candidate rows include `regionCode` and `region`; public APIs do not expose these.
No external per-IP lookup request, new paid service, or extra frontend geolocation
request is used. IP-derived states are approximate, not verified residence; VPNs,
mobile networks, proxies and database inaccuracies can change the reported state.
Excel exports include the region and filter, including the filter in the filename.
Refresh an already-open admin page after initial backfill to replace cached results.
Local migration/backfill does not modify AWS.

## Global 10-minute challenge sessions

Apply `db/09_global_challenge_sessions.sql` as the **challenge_platform** database
owner after migrations 07 and 08, before deploying this backend and frontend together.
The migration is additive; old attempts are preserved. Applying it locally does not
migrate AWS. The pipeline does not apply database migrations automatically.

The candidate supplies details before starting. One session per email/phone per
server calendar day replaces one answer per day. A developer reset closes the old
session before granting a new slot. Starting consumes the daily slot, even if no
answer is submitted. Identity is self-reported, not email-verified.

All session endpoints are POST under `/api/v1/challenge-sessions`, with no-store
responses and a random UUID capability in the JSON body (never in a URL):

- `/start`: token, fullName, email, phone, sourceCampaign; returns the first random
  question and server-controlled start/deadline. Reusing the token resumes that session.
- `/state`: token; resumes the current question, saved draft and unchanged deadline.
- `/answer`: token, ordinal, sourceCode, editorActivity; atomically saves one answer
  and returns the next non-repeating random question. Duplicate ordinal retries are
  idempotent. Deadline checks use the database clock, not client duration.
- `/draft`: token, ordinal, revision, sourceCode, editorActivity; changed drafts only,
  with monotonically increasing revisions to reject stale autosaves.
- `/finish`: token; closes the session. At expiry, only the last nonblank draft
  received by the server **before** the deadline is submitted. No late replacement is
  accepted. Unsaved/offline edits are not recoverable. An untouched starter is not
  autosaved by the frontend. Exhausting the question bank also ends the session.

Answers are committed as QUEUED before grading. A scheduled worker grades from this
durable database queue; a separate scheduler thread expires sessions even while
grading is slow. Multi-instance workers use row locks and SKIP LOCKED. Judge
infrastructure failures retry after 30 seconds, up to three tries, then show
GRADING_UNAVAILABLE for admin review. No new external queue/service is required.
Queued scores are provisional until grading completes; reload an already-open
admin view to replace its cached snapshot.

Per-question duration excludes earlier questions. Speed bonus uses elapsed time
within the global 600-second session. Admin attempt details and Excel include
session ID, question number, global start/deadline and elapsed time; timestamps
remain in the admin's local time zone. Public responses never contain scores or
hidden test results. Legacy `POST /api/v1/submit` returns 410 so it cannot bypass
session enforcement. Existing question and sample-run endpoints remain available.

Activity checkpoints now bind to challengeToken + ordinal; tracking streams from
reloads attach to the same answer. Final activity travels in the answer request
without a checkpoint wait. Drafts save every five seconds only when code changes;
immutable activity checkpoints remain on their 30-second changed-state schedule.
Both APIs share the bounded per-IP guard described below.

Local verification: `CHALLENGE_STATS_DB_TESTS=true` enables rollback-only session,
deadline, draft, duplicate-answer, grading and admin-history tests. Workers can be
disabled in tests with `challenge.sessions.workers-enabled=false`.

## Mass-input activity checkpoints

Apply `db/08_activity_checkpoints.sql` as the **challenge_platform** database owner
before deploying this backend, then deploy the frontend. It is additive and contains
no credentials. Local migration has no effect on AWS. Migration 07 is still required.

`POST /api/v1/activity-checkpoints` accepts changed editor code and compact activity
summaries. A random per-page UUID is a write/claim capability (only its SHA-256 hash
is stored), not verified candidate identity. There is no public read endpoint.
The current challengeToken + ordinal binding attaches history atomically on answer
submission and seals further writes. Historical submissions may have no checkpoints.
Only authenticated candidate-attempt admin details expose history, including server
receipt timestamps, final-code comparison and intervals longer than 90 seconds.

The frontend flags edits inserting 80+ characters, or a one-second burst of 80+
characters with little corresponding typing. Starter loading and undo/redo are
excluded. Counts, insertion size and event offsets are saved with final activity.
These are **unverified review signals**, never an automatic rejection or a claimed
DevTools paste count. Legitimate formatting, completion and assistive input can
trigger them. Gaps/mismatches can be offline/idle time or unsaved final edits.

Changed state is batched every 30 seconds (maximum 1.5-second network wait per
checkpoint). Final activity is included directly in the answer. Idle state makes no request. Checkpoints contain editor
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
by descending total score, then descending candidate ID to keep tied scores stable.
The cursor's score is resolved within the same filters' date/asOf snapshot. Null
`nextCursor` means stop, including empty results.
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
- `POST /api/v1/submit` — retired (410); use challenge-sessions/answer.
- `GET /api/v1/leaderboard`
- `GET /api/v1/stats`

The public leaderboard returns only `rank` and `displayName`. Session responses expose
progress, timing and the next public question, never scores or hidden grading results.

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

`POST /api/v1/challenge-sessions/answer` accepts optional `editorActivity` (version 1), validated recursively:
question slug must match the submitted question, counters cannot be negative, and the
timeline is capped at 5,000 categorized events with ordered offsets within a 24-hour window.
It is saved atomically with the attempt and returned only by the authenticated admin
attempt-detail endpoint. Historical/missing reports are NULL, not zero. Public responses
never contain the activity report or private grading results.

Reports contain key categories and timing, not literal typed keys or clipboard contents.
They are unverified client observations: DevTools and custom clients can bypass or forge
them. Unexplained model changes are review signals, not proof of cheating. There is no
per-keystroke API or literal-key log. Session drafts and checkpoints are stored in
bounded batches as described above. Deleting an attempt
removes its telemetry. The frontend's `EDITOR_ACTIVITY.md` documents scope and limitations.

Tests: `mvn --no-transfer-progress test`; set `CHALLENGE_STATS_DB_TESTS=true` and a local
challenge datasource to also run rollback-only PostgreSQL submission/storage tests.
