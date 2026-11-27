# Notes API

A REST API for private and shared notes, built with Java 21, Spring Boot 3.5 and MySQL 8.4. An owner can grant read or edit access to another user. Each committed change to a note records who made it, when, and what the note contained.

## Run with Docker Compose

Start Docker, clone this repository and open its root directory. Run `docker compose up --build` and wait for `Started NotesApplication`. The API is available at `http://127.0.0.1:8080`. Request 01 below checks that it is listening.

Compose creates the database passwords and JWT signing key on first start. Only the API port is published, on the loopback interface. If you need a different port, set `API_PORT=8081` in a local `.env` file next to `docker-compose.yml` and adjust `baseUrl`. Stop the stack with Ctrl+C and `docker compose down`. Its named volumes retain accounts, notes and the signing key.

## Try the API with HTTP requests

Owner owns a release note. Guest first reads it, then edits it. The walkthrough ends with a conflicting edit, an audit lookup, revocation and deletion. Send one numbered request at a time.

- **IntelliJ HTTP Client.** Copy [notes-api.http](examples/notes-api.http) to a private folder outside the checkout and open your copy in the IDE. Fill in the values at the top. Use the run icon beside each request.
- **Postman.** Create a local environment with the variables below and select it. For each example choose the HTTP method, paste the URL, and add the listed headers. For JSON choose **Body → raw → JSON**. Use a single Authorization header per request. Leave it unset for steps 01–05.

| Variable | Where to get its value                                                                                                                           |
| --- |--------------------------------------------------------------------------------------------------------------------------------------------------|
| `baseUrl` | `http://127.0.0.1:8080` by default.                                                                                                              |
| `ownerLogin`, `guestLogin` | Two fresh logins, e.g. `owner.demo01` and `guest.demo01`. Change the suffix for another run.                                                     |
| `ownerPassword`, `guestPassword` | Choose two private demo passwords of 12–32 alphanumeric characters for easy substitution in JSON. Use the same values at registration and login. |
| `guestId` | Guest’s `id` from step 03.                                                                                                                       |
| `ownerToken`, `guestToken` | The raw `token` strings from steps 04 and 05, without quotes or the word Bearer.                                                                 |
| `itemId`, `initialVersion` | The note’s `id` and numeric `version` from step 06. Keep the initial value for the conflict demonstration.                                       |

Copy response values manually to your local environment or private file. Keep passwords and tokens out of Git. Requests below use `{{variable}}` placeholders. In a client without variables, replace each placeholder with its value. An expected `401`, `403`, `404` or `409` is a successful demonstration of the corresponding rule.

### Identify the two users

**01. Probe the API** — expect `401`. Leave Authorization empty. A response confirms the API is reachable.

```http
GET {{baseUrl}}/items
```

**02. Register owner** — expect `201`. The response contains id, login and createdAt.

```http
POST {{baseUrl}}/register
Content-Type: application/json

{"login": "{{ownerLogin}}", "password": "{{ownerPassword}}"}
```

**03. Register Guest** — expect `201`. Copy id to guestId. Use the UUID, not the login.

```http
POST {{baseUrl}}/register
Content-Type: application/json

{"login": "{{guestLogin}}", "password": "{{guestPassword}}"}
```

**04. Log in as owner** — expect `200`. Copy token to ownerToken. expiresIn is in seconds.

```http
POST {{baseUrl}}/login
Content-Type: application/json

{"login": "{{ownerLogin}}", "password": "{{ownerPassword}}"}
```

**05. Log in as Guest** — expect `200`. Copy token to guestToken. Keep the two tokens separate.

```http
POST {{baseUrl}}/login
Content-Type: application/json

{"login": "{{guestLogin}}", "password": "{{guestPassword}}"}
```

### Create a private note and share it

**06. Create the release note** — expect `201`. Copy id to itemId and version to initialVersion (0 on creation). Keep this original version through step 13.

```http
POST {{baseUrl}}/items
Authorization: Bearer {{ownerToken}}
Content-Type: application/json

{"title": "Release decision", "content": "Awaiting test results"}
```

**07. Check privacy before sharing** — expect `200`. The new itemId is absent from Guest’s list.

```http
GET {{baseUrl}}/items
Authorization: Bearer {{guestToken}}
```

**08. Give Guest read access** — expect `201`. The response names Guest’s userId, role VIEWER and grantedAt.

```http
POST {{baseUrl}}/items/{{itemId}}/share
Authorization: Bearer {{ownerToken}}
Content-Type: application/json

{"userId": "{{guestId}}", "role": "VIEWER"}
```

**09. Read as a viewer** — expect `200`. Find itemId. Its myRole is VIEWER.

```http
GET {{baseUrl}}/items
Authorization: Bearer {{guestToken}}
```

**10. Try to edit as a viewer** — expect `403`. The viewer can read the note but cannot change it.

```http
PATCH {{baseUrl}}/items/{{itemId}}
Authorization: Bearer {{guestToken}}
Content-Type: application/json

{"version": {{initialVersion}}, "content": "Tests passed"}
```

### Edit together and inspect the history

**11. Promote Guest to editor** — expect `200`. The existing grant now has role EDITOR. Keep using the same guestToken.

```http
POST {{baseUrl}}/items/{{itemId}}/share
Authorization: Bearer {{ownerToken}}
Content-Type: application/json

{"userId": "{{guestId}}", "role": "EDITOR"}
```

**12. Save Guest’s edit** — expect `200`. The note keeps its title. Its content is now Tests passed and version is 1.

```http
PATCH {{baseUrl}}/items/{{itemId}}
Authorization: Bearer {{guestToken}}
Content-Type: application/json

{"version": {{initialVersion}}, "content": "Tests passed"}
```

**13. Try owner’s stale edit** — expect `409`. Use the original version on purpose. The response includes currentVersion: 1.

```http
PATCH {{baseUrl}}/items/{{itemId}}
Authorization: Bearer {{ownerToken}}
Content-Type: application/json

{"version": {{initialVersion}}, "content": "Release approved"}
```

**14. Read the winning change** — expect `200`. Find itemId, version 1, myRole OWNER and content Tests passed. Review before deciding whether to resubmit an edit.

```http
GET {{baseUrl}}/items
Authorization: Bearer {{ownerToken}}
```

**15. Read the history** — expect `200`. Two entries in revision order: ADD by owner, then MOD by Guest. Compare changedBy, timestamp, title and content. Revision numbers can have gaps.

```http
GET {{baseUrl}}/items/{{itemId}}/history
Authorization: Bearer {{guestToken}}
```

### Revoke access and delete the demo note

**16. Revoke access** — expect `204`. The successful response has an empty body.

```http
DELETE {{baseUrl}}/items/{{itemId}}/share/{{guestId}}
Authorization: Bearer {{ownerToken}}
```

**17. Read history with the revoked token** — expect `403`. Guest uses his still-valid token. The active note now denies him access.

```http
GET {{baseUrl}}/items/{{itemId}}/history
Authorization: Bearer {{guestToken}}
```

**18. Delete the note** — expect `204`. The owner soft-deletes the demo note.

```http
DELETE {{baseUrl}}/items/{{itemId}}
Authorization: Bearer {{ownerToken}}
```

**19. Check history after deletion** — expect `404`. The history endpoint requires an active note, even for its owner. The database retains the audit trail.

```http
GET {{baseUrl}}/items/{{itemId}}/history
Authorization: Bearer {{ownerToken}}
```

**20. Check the final list** — expect `200`. The deleted itemId is absent. Other notes owned by or shared with owner may still appear.

```http
GET {{baseUrl}}/items
Authorization: Bearer {{ownerToken}}
```

## Endpoint reference

Send JSON request bodies with `Content-Type: application/json`. All `/items` routes require an `Authorization: Bearer <token>` header.

| Method and path | Who can call it | Body and success response |
| --- | --- | --- |
| `POST /register` | Public | `login`, `password` → `201` with `id`, `login`, `createdAt`. Duplicate login → `409`. |
| `POST /login` | Public, limited per IP | `login`, `password` → `200` with `token`, `expiresIn`. Invalid credentials → `401`. Exhausted limit → `429` with `Retry-After` in seconds. |
| `POST /items` | Authenticated user | `title`, optional `content` → `201` with the note, owner ID, timestamps and initial version `0`. |
| `GET /items` | Authenticated user | `200` with active notes the caller owns or can access. Each includes `myRole` (`OWNER`, `EDITOR`, `VIEWER`). |
| `PATCH /items/{id}` | Owner or editor | Required integer `version`, optional `title` and `content` → `200` with the updated note and version. Stale version → `409` with `currentVersion`. |
| `DELETE /items/{id}` | Owner | Soft-deletes the note → `204`. |
| `GET /items/{id}/history` | Owner, editor or viewer | `200` with snapshots containing `revision`, `revisionType`, `timestamp`, `changedBy`, `title` and `content`. |
| `POST /items/{id}/share` | Owner | `userId`, `role` (`VIEWER` or `EDITOR`) → `201` for a new grant, `200` for an existing grant. Sharing with yourself → `400`. |
| `DELETE /items/{id}/share/{userId}` | Owner | Revokes the grant → `204`. Missing grant → `404`. |

Registration accepts logins of 3–64 Unicode code points, unique without regard to case. Passwords require at least 8 code points and must fit within 72 UTF-8 bytes.

A title must contain text and fit within 255 Unicode code points. Content can be null and is limited to 65,535 UTF-8 bytes. In a PATCH, omitting a field preserves its value. Sending `"content": null` clears it. A viewer can read the full history of an active shared note. Revoking a grant takes effect even for an already issued token.

## Architecture and decisions

- **HTTP and persistence.** Controllers in `auth.web` and `item.web` validate requests and call services. For example, `PATCH /items/{id}` maps the request to an `EditItemCommand`. The editing service in `item.service` checks permission and version before updating the `item.model` entity. `item.persistence` contains the note repository and list projection. Flyway versions the schema, and Hibernate validates the mappings at startup.
- **Identity and resource access.** Passwords are hashed with BCrypt. Spring Security validates the signature, issuer, expiry and user ID of each HS256 JWT. The token identifies the caller. The `item.permission` package manages grants and checks ownership and current roles against MySQL, so a role change takes effect on the next request.
- **Concurrent writes.** JPA `@Version` detects competing edits, including two clients that submit the same previously valid version. The API returns the committed version after a conflict. Sharing, revocation and deletion lock the same item row to serialize changes to access and deletion state.
- **Audit and deletion.** The `item.history` package contains the Envers revision entity, its listener and the history query. `ItemRevisionListener` records the authenticated user's login. Before `ItemHistoryRepository` calls `AuditReader`, `ItemHistoryService` verifies that the note is active and the caller still has read access. A soft delete hides the live row from normal queries and records a `DEL` snapshot. The per-item version used for PATCH and the Envers revision number serve different purposes.
- **Login rate limit.** A Bucket4j bucket per remote IP counts every `POST /login`, including successful attempts. The default allows five attempts per 60-second refill period. The sixth request in that period receives `429`. Buckets are held in memory in each application instance, so restarting the instance resets its counters.

## Configuration and running with Maven

Application defaults are defined in [application.properties](src/main/resources/application.properties).

| Spring property | Environment variable | Default or requirement |
| --- | --- | --- |
| `spring.datasource.url` | `DB_URL` | `jdbc:mysql://localhost:3306/notes` for Maven. Compose uses `jdbc:mysql://db:3306/notes`. |
| `spring.datasource.username` | `DB_USERNAME` | `notes`. |
| `spring.datasource.password` | `DB_PASSWORD` | Empty fallback. Supply the password of the MySQL application user. Compose generates it. |
| `app.jwt.secret` | `JWT_SECRET` | Required. Base64 encoding of at least 32 random bytes. Compose generates and persists it. |
| `app.jwt.ttl-seconds` | `JWT_TTL_SECONDS` | `3600` seconds. Must be positive. |
| `app.login-rate-limit.attempts` | `LOGIN_RATE_LIMIT_ATTEMPTS` | `5` attempts per IP per refill period. Minimum `1`. |
| `app.login-rate-limit.window-seconds` | `LOGIN_RATE_LIMIT_WINDOW_SECONDS` | `60` seconds per refill period. Minimum `1`. |

The remaining settings in that file configure the framework. `spring.jpa.hibernate.ddl-auto=validate` leaves schema changes to Flyway. `spring.jpa.open-in-view=false` keeps lazy loading inside service transactions. Hibernate CDI extensions enable dependency injection into the revision listener, and Envers stores the last snapshot on deletion. Jackson rejects numeric enum values, so sharing requests must use named roles.

For Compose, `API_PORT` selects the published host port (default `8080`). Compose supplies the two application secrets through read-only files. To override the token lifetime or login limits in Compose, add the corresponding environment variables under `services.api.environment` in a Compose override file.

To run directly from your IDE, install JDK 21 and start MySQL 8.4. Create the database and application user with permission to run Flyway migrations and read/write data. In a local run configuration for `NotesApplication`, set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and `JWT_SECRET` as described above, then run the main class. Use a password manager or secure key generator for 32 random bytes encoded in Base64. Reuse that secret across starts to keep unexpired tokens valid. From a terminal with those variables already set, the equivalent command is `./mvnw spring-boot:run` (Windows PowerShell: `.\mvnw.cmd spring-boot:run`).

## Tests

Run the Maven wrapper from the repository root with JDK 21.

| Command | What runs |
| --- | --- |
| `./mvnw test` | Unit tests through Surefire. No database or Docker required. |
| `./mvnw clean verify` | Unit tests and Failsafe integration tests (`*IT`). Start Docker first. Testcontainers starts a separate MySQL instance and Flyway applies the migrations. |

The [authorization tests](src/test/java/com/example/notes/item/permission/ItemAccessPolicyTest.java) cover owner, editor, viewer and unrelated users. The [editing tests](src/test/java/com/example/notes/item/service/ItemEditingServiceTest.java) cover stale versions and concurrent write failures. The [sharing tests](src/test/java/com/example/notes/item/permission/ItemSharingServiceTest.java) cover self-sharing and replacing an existing role.

Two integration tests exercise the HTTP endpoints and their backing services. [ItemHistoryIT](src/test/java/com/example/notes/item/history/ItemHistoryIT.java) creates a note, applies several edits and verifies the authors and snapshots returned by the history endpoint. [LoginRateLimitIT](src/test/java/com/example/notes/auth/ratelimit/LoginRateLimitIT.java) sends six requests from one IP, verifies `429` and `Retry-After`, and checks that another IP can still log in. GitHub Actions also starts the Compose stack and registers a user through its published HTTP port.
