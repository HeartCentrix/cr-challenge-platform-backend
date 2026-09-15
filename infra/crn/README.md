# CRN matching Lambda (nonprod)

`challenge-platform-crn-nonprod` runs privately in AWS Lambda, separately from the
Challenge API and shared ECS hosts. EventBridge invokes it every minute. The former
ECS service `challenge-platform-crn-worker-nonprod` is retired (desired 0).
No frontend/backend web deployment or Git push is part of Lambda deployment.

## Data flow

Migration `db/16_candidate_crn.sql` is independent of migrations 12-15 and compatible
with the existing dev backend. A candidate INSERT queues a CRN request atomically
in `challenge_platform.candidate_crn_match`. Changing email/phone clears old flags
and increments the request version. Database triggers make no network calls.

Lambda drains this **database-backed queue** once per minute. There is no SQS queue,
public function URL, candidate PII in invocation payloads, or Fion connection in the
web API. Normal matching latency is approximately a minute plus processing time;
backlogs or dependency failures can take longer. Empty queues never query Fion.

`FOR UPDATE SKIP LOCKED` prevents duplicate work. Each result commits separately;
crashes roll back the active request. A run is capped at 100 checks, stops starting
new work with less than 35 seconds left, and has a 60-second timeout. Reserved
concurrency is 1 to protect the databases. Failed source lookups retry after 1, 2,
4 and 8 minutes (five attempts maximum). Exhausted rows remain ERROR, not false
negatives. Reviewed errors can be retried by an owner resetting attempts and
next_attempt_at. Existing candidates are not automatically backfilled.

## Security boundary

- Fion `challenge_crn_reader`: SELECT only email and phone_number on
  `codereport.mst_user_profile`; read-only transactions. No Fion application-data writes.
- Challenge `challenge_crn_writer`: reads candidate ID/email/phone and CRN requests;
  updates only match status, flags, timestamp and retry metadata.
- Challenge API `challenge_app`: reads CRN results but cannot write them.
- Dedicated Secrets Manager secrets are read by the Lambda execution role.
  No secret values are stored in Lambda configuration, code, logs or frontend.
  Resource policies deny other runtime roles, including ECS, even with broader
  identity policies. The named deployment operator/root retain recovery access.
- Old ECS credentials are rotated during cutover and its execution policy removed.
- Lambda uses private subnets without NAT. Its security group permits only
  PostgreSQL to the two database groups and HTTPS to its Secrets Manager endpoint.
  No inbound rule or public invocation URL. The specific EventBridge rule can
  invoke Lambda; authorized AWS operators can also invoke it.
- The interface endpoint has private DNS **disabled**. Only Lambda uses its explicit
  URL, leaving existing ECS/service DNS unchanged. Its policy permits only this
  Lambda role and these two secrets.
- One endpoint AZ is a nonprod cost/availability tradeoff. Lambda has two private
  subnets but Secrets Manager access still depends on that single endpoint AZ.
- DB connections validate the RDS certificate and exact expected nonprod host,
  database and username. Secret caching expires after five minutes; failed
  invocations clear it. Credential cutover refreshes the environment immediately.

This does not fix unrelated existing Fion security-group rules, shared Judge
infrastructure or broad shared ECS IAM permissions. Those resources are unchanged.
Endpoints are not passwords; isolation relies on IAM, networking and restricted
credentials. Fion flags are existence checks, not proof of identity.

## Matching and admin display

Emails use trimmed case-insensitive equality. Phones remove formatting and compare
all 7-15 digits, preserving country codes; no suffix/fuzzy matching or country guess.
Blank values never match. Email and phone can match different Fion profiles. Only
the two booleans are copied, never a Fion profile or candidate list.

Only authenticated admin DTOs contain `crn`. Green means either/both matched; red
means neither matched in a completed check. Gray means pending/error/not checked.
Details and Excel exports identify matching fields. Timestamps use the admin's local
timezone. The one-hour table cache remains: earlier status can persist until reload
or cache expiry. CRN reflects latest identity, not the selected historical attempt.
These web UI/API changes require their own deployment.

## Deployment

Python 3.13 ARM64, 256 MB. Install `requirements.txt` into an isolated dependency
directory (pure-Python packages only); boto3 comes from the Lambda runtime.
For local tests install boto3 as well, then run:

```text
python -m unittest discover -s infra/crn -p "test_*.py" -v
python infra/crn/deploy_lambda.py prepare
python infra/crn/deploy_lambda.py deploy --dependencies <directory> --ca <AWS-RDS-global-bundle.pem>
```

The ZIP contains only dependencies, worker.py, lambda_function.py and the public CA.
No .env or application YAML. Deployment initially disables the schedule. Wait for
Active, invoke manually, and verify database permissions before cutover:

```text
python infra/crn/deploy_lambda.py retire-worker
```

Wait for the old ECS task to stop. Rotate both dedicated database passwords and
Secrets Manager values using owner credentials privately, then refresh Lambda's
environment to discard cached credentials. Verify a synthetic candidate traverses
the trigger and Lambda, then enable:

```text
python infra/crn/deploy_lambda.py enable
```

Verify a second candidate is processed by the schedule without manual invocation;
remove only synthetic fixtures. Never deploy dev-ak web code to dev as part of this.

## Monitoring and rollback

CloudWatch `/aws/lambda/challenge-platform-crn-nonprod` retains 14 days of aggregate
logs only. Alarms `challenge-platform-crn-nonprod-errors` and `-matching` report
invocation failures, lookup errors, exhausted retries and requests over ten minutes
old. Alarms are visible in CloudWatch; no email/SNS recipient is configured.

Disable EventBridge rule `challenge-platform-crn-nonprod-minute` to pause matching.
Pending requests remain durable; registrations continue. Do not restart the old ECS
task: its credentials and secret access are revoked. Retired ECS/ECR artifacts are
retained for audit. They run no compute, though image storage remains.
