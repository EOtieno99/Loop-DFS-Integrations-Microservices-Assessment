# Country information integration service

The project resolves a sentence-cased country name through `CountryISOCode`, calls `FullCountryInfo`, and atomically persists the complete country and its languages in MySQL.

Flyway owns the schema; Hibernate validates it on startup.

## Run locally

Requires Java 17+; Maven Wrapper downloads Maven. Docker is required for the Compose stack and real MySQL tests.

```sh
cp .env.example .env
# Set two different passwords in .env before starting.
docker compose up --build -d
curl http://localhost:8081/actuator/health
./scripts/smoke-test.sh
```

Alternatively start only MySQL with `docker compose up -d mysql`, export `DB_PASSWORD` matching `.env`, then run `./mvnw spring-boot:run`. Defaults are `DB_URL=jdbc:mysql://localhost:3306/loop_assessment` and `DB_USERNAME=loop`. No password is committed. `docker compose down` preserves the database volume.

The database uses HikariCP with a maximum of 10 connections per application instance and a minimum of 2 idle connections. Pool settings can be overridden using the `DB_POOL_*` variables in `.env.example`; Compose forwards them to the application. For Maven runs, load your existing `.env` first with `set -a; source .env; set +a`.

The connection acquisition timeout defaults to 3 seconds, validation timeout to 1 second, idle timeout to 10 minutes, and maximum connection lifetime to 30 minutes. Keep minimum idle at or below the maximum pool size and validation timeout below the acquisition timeout. Set maximum lifetime below any database or network connection lifetime limit, and budget the total connections across all application instances against MySQL's connection limit.

## REST contract

| Method | Path | Behavior |
| --- | --- | --- |
| POST | `/api/v1/countries` | Import from SOAP; accepts `{"name":"Tanzania"}`; 201 with Location |
| GET | `/api/v1/countries?page=0&size=20` | All stored countries through bounded pages; maximum size 100 |
| GET | `/api/v1/countries/{id}` | Fetch one stored aggregate |
| PUT | `/api/v1/countries/{id}` | Replace editable data with the latest `version`; 200 |
| DELETE | `/api/v1/countries/{id}` | Delete country and dependent languages; 204 |

Response shape:

```json
{
  "id": 1,
  "version": 0,
  "createdAt": "2026-10-07T12:00:00Z",
  "updatedAt": "2026-10-07T12:00:00Z",
  "country": {
    "isoCode": "TZ",
    "name": "Tanzania",
    "capitalCity": "Dodoma",
    "phoneCode": "255",
    "continentCode": "AF",
    "currencyIsoCode": "TZS",
    "countryFlag": "http://example.com/tz.png",
    "languages": [{"isoCode":"swa","name":"Swahili"}]
  }
}
```

POST stores the provider's canonical name. Input conversion follows sentence case exactly: `UNITED STATES` becomes `United states`. Leading/trailing and repeated whitespace is normalized using locale-independent casing.

For PUT, use the latest GET response's `version` and `country`:

```sh
curl -X PUT http://localhost:8080/api/countries/1 \
  -H 'Content-Type: application/json' \
  -d '{"version":0,"country":{"isoCode":"TZ","name":"Tanzania","capitalCity":"Dodoma","phoneCode":"255","continentCode":"AF","currencyIsoCode":"TZS","countryFlag":"","languages":[{"isoCode":"swa","name":"Swahili"}]}}'
```

Country ISO codes are uppercase two-letter codes; language codes are distinct within each country. Update all fields; this is PUT, not a partial PATCH. Language removals and changes persist with the parent transaction. Responses contain DTOs, avoiding lazy proxy serialization and recursive entity graphs.

Successful fresh POST returns `X-Data-Stale: false`. A repeated successful import is **409**, enforcing country ISO/name uniqueness. When SOAP fails, an existing country matching the normalized name can be returned with **200** and `X-Data-Stale: true`; its timestamps show when data was last stored. Unknown countries do not use fallback. GET always serves stored data without SOAP calls.

Errors are `application/problem+json` with `status`, `title`, `detail`, `code`, and `requestId`. Validation errors also include a field `errors` map. Invalid input is 400, missing country 404, duplicate/stale write 409, invalid provider XML/SOAP fault 502, and exhausted transient failures/open circuit/full bulkhead or database unavailability 503 (`Retry-After: 30`). Unexpected failures return a generic 500; logs retain diagnostics. Send a safe `X-Request-ID` to correlate logs; otherwise the service generates one.

## Tests and build

```sh
./mvnw test
./mvnw verify
```

The default suite includes normalization and service unit tests; secure namespace-aware SOAP codec tests; local HTTP provider tests for the two-call protocol, bounded retries, timeout and circuit opening; and MockMvc + H2 integration tests exercising real Flyway migrations, validation, persistence, language updates, optimistic conflicts, and HTTP errors. No test calls the public SOAP service.

`MySqlCountryApiTest` reuses the API tests against a MySQL 8.4 Testcontainer when Docker is running. Without Docker it is explicitly skipped. Run `./mvnw -Dtest=MySqlCountryApiTest test` with Docker to verify MySQL-specific behavior. CI runs Java 17 tests and builds the container on Ubuntu. Reports are in `target/surefire-reports`; executable JAR is `target/Loop_Assessment-0.0.1-SNAPSHOT.jar`.
