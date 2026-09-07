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

- `GET /api/v1/questions`
- `GET /api/v1/questions/{slug}`
- `POST /api/v1/run`
- `POST /api/v1/submit`
- `GET /api/v1/leaderboard`
- `GET /api/v1/stats`

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
