# Configuration and operations

All deployable secrets are injected through environment variables. Copy `.env.example` for a local checklist if useful, but do not commit the populated file. Spring Boot does not load `.env` by itself; export values through the shell, a process manager, or the deployment secret manager.

| Variable | Required when | Purpose |
|---|---|---|
| `SUNO_JWT_SECRET` | every application startup | JWT HMAC signing and validation key |
| `PAYMENT_CALLBACK_SECRET` | every application startup | payment callback signature validation |
| `SUNO_DB_URL`, `SUNO_DB_USERNAME`, `SUNO_DB_PASSWORD` | `mysql`、`staging`、`prod` profiles | least-privilege MySQL URL and account |
| `SUNO_REDIS_HOST`, `SUNO_REDIS_PORT`, `SUNO_REDIS_PASSWORD`, `SUNO_REDIS_DATABASE` | `redis` profile | optional non-critical query cache |
| `BAIDU_IMAGE_AUDIT_ENDPOINT`, `BAIDU_IMAGE_AUDIT_ACCESS_TOKEN`, `LOGISTICS_ENDPOINT`, `LOGISTICS_API_KEY` | `staging`、`prod` and real provider mode | external-provider endpoints and credentials |

## Profiles

The default profile starts against an in-memory H2 database and runs Flyway migrations. `dev` additionally loads development seed migrations. `mysql` points to a locally reachable MySQL instance and requires the database variables above. `staging` and `prod` also require an external MySQL URL and credentials, and force real image-audit and logistics providers; they cannot inherit the H2 or mock-provider defaults. Add `redis` only when a Redis service is available; Phase 0 treats it as a cache, so readiness remains based on application state, database connectivity, and Flyway validation.

```bash
export SUNO_JWT_SECRET="$(openssl rand -base64 48)"
export PAYMENT_CALLBACK_SECRET="$(openssl rand -hex 32)"
./mvnw -pl suno-bootstrap -am package -DskipUnitTests=true
java -jar suno-bootstrap/target/suno-bootstrap-0.0.1-SNAPSHOT.jar
```

For MySQL, provision a database and an application user first, then export its credentials and select the profile:

```bash
export SUNO_DB_USERNAME=suno
export SUNO_DB_PASSWORD="$(openssl rand -hex 24)"
java -jar suno-bootstrap/target/suno-bootstrap-0.0.1-SNAPSHOT.jar --spring.profiles.active=mysql
```

## Actuator

The public probe endpoints are `GET /actuator/health/liveness` and `GET /actuator/health/readiness`. The readiness response is `UP` only when Spring readiness, the database, and Flyway validation are all healthy. Aggregate health, info, metrics, and Flyway endpoints require an authenticated `ADMIN`; details are shown only to authenticated callers.

## Troubleshooting

**Docker unavailable.** Docker-backed MySQL integration tests use Testcontainers with the MySQL 8.4 OCI index digest `sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb` (resolved on 2026-08-01). Start Docker Desktop or the Docker daemon, verify it with `docker version`, then rerun `./mvnw --batch-mode verify`. H2-only tests can still be targeted with the Maven Wrapper.

**Flyway checksum mismatch.** Do not edit an applied migration. Restore the committed migration contents, or create a new corrective migration after confirming the target schema. Run `./mvnw -pl suno-bootstrap -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=FlywayH2MigrationTest test` before retrying a deployment.

**Invalid production secret.** A missing `SUNO_JWT_SECRET` or `PAYMENT_CALLBACK_SECRET` prevents startup. Provide both from the deployment secret manager; the JWT key must be sufficiently long for HS256 and must never be a demo value.

**Java or Maven mismatch.** This repository requires Java 25 and its committed Maven Wrapper. Check `./mvnw --version`, select JDK 25, and use `./mvnw --batch-mode verify` instead of a system Maven installation.
