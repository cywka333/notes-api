# Notes API

A REST API for private and shared notes, built with Java 21, Spring Boot 3.5 and MySQL 8.4. An owner can grant read or edit access to another user. Each committed change to a note records who made it, when, and what the note contained.

## Run with Docker Compose

1. Install Docker with the Compose plugin and start the Docker engine.
2. Clone the repository and start the API and MySQL from the project root.

   ```bash
   git clone https://github.com/cywka333/notes-api.git
   cd notes-api
   docker compose up --build
   ```

3. Wait for `Started NotesApplication`. In a second terminal, check the protected endpoint.

   ```bash
   curl -i http://127.0.0.1:8080/items
   ```

   Expect **401 Unauthorized** for this request without a token. The walkthrough below creates an account and makes authenticated requests.

Compose generates the database passwords and JWT signing key on first start. MySQL is reachable inside the Compose network. Only the API port is published, on the loopback interface. If port 8080 is occupied, start with `API_PORT=8081 docker compose up --build` in Bash and use port 8081 in the requests. Stop the stack with Ctrl+C and `docker compose down`. The named volumes retain notes, accounts and the signing key for the next start.

## Try the API

Run these blocks in order in the **same Bash session** (Git Bash also works). The examples use curl 7.82 or later, jq and OpenSSL. Set `BASE_URL` to your API address if you changed the port.

**1. Register an owner and an editor, then log in as both.** Registration returns a user ID. Login returns a bearer token and its lifetime in seconds (`expiresIn`). A random suffix lets you repeat the walkthrough against the same database.

```bash
BASE_URL=${BASE_URL:-http://127.0.0.1:8080}
run_id=$(openssl rand -hex 4)
account_json() {
  jq -nc --arg login "$1.$run_id" --arg password "$(openssl rand -hex 16)" \
    '{login: $login, password: $password}'
}
owner=$(account_json owner)
editor=$(account_json editor)

curl -fsS --json "$owner" "$BASE_URL/register" | jq
editor_id=$(curl -fsS --json "$editor" "$BASE_URL/register" | jq -ej '.id')
owner_token=$(curl -fsS --json "$owner" "$BASE_URL/login" | jq -ej '.token')
editor_token=$(curl -fsS --json "$editor" "$BASE_URL/login" | jq -ej '.token')
```

**2. Create a note and grant the editor access.** Keep the initial version from the create response. The editor will submit it with the first edit.

```bash
created=$(curl -fsS -H "Authorization: Bearer $owner_token" \
  --json '{"title":"Release checklist","content":"Run tests"}' "$BASE_URL/items")
item_id=$(jq -ej '.id' <<< "$created")
version=$(jq -ej '.version' <<< "$created")
share=$(jq -nc --arg id "$editor_id" '{userId: $id, role: "EDITOR"}')

curl -fsS -H "Authorization: Bearer $owner_token" \
  --json "$share" "$BASE_URL/items/$item_id/share" | jq
curl -fsS -H "Authorization: Bearer $editor_token" "$BASE_URL/items" | jq
```

The editor's list now contains the note with `myRole: "EDITOR"`.

**3. Edit the note, try a stale write, then inspect the history.**

```bash
patch=$(jq -nc --argjson v "$version" '{version: $v, content: "Tests passed"}')
curl -fsS -X PATCH -H "Authorization: Bearer $editor_token" \
  --json "$patch" "$BASE_URL/items/$item_id" | jq

# The owner still holds the original version. This request returns 409.
curl -sS -i -X PATCH -H "Authorization: Bearer $owner_token" \
  --json "$patch" "$BASE_URL/items/$item_id"

curl -fsS -H "Authorization: Bearer $owner_token" "$BASE_URL/items/$item_id/history" | jq
```

The edit returns version `1`. The conflict response includes `"currentVersion": 1`. A client can reload the note through `GET /items`, review the new content and decide whether to reapply its changes. The history starts with an `ADD` snapshot by the owner, followed by a `MOD` snapshot by the editor. The rejected write produces no revision.

**4. Revoke access and delete the demo note.**

```bash
curl -sS -i -X DELETE -H "Authorization: Bearer $owner_token" \
  "$BASE_URL/items/$item_id/share/$editor_id"
curl -sS -i -H "Authorization: Bearer $editor_token" "$BASE_URL/items/$item_id/history"
curl -sS -i -X DELETE -H "Authorization: Bearer $owner_token" "$BASE_URL/items/$item_id"
```

Expect `204`, `403`, then `204`. Once the owner deletes the note, its history endpoint returns `404`. The database retains the audit trail.

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

- **HTTP and persistence.** Packages are grouped around authentication, users, items and permissions. Controllers validate request DTOs and delegate to services. Services own transactions and access decisions. Spring Data JPA repositories handle storage, Flyway versions the schema, and Hibernate validates the mappings at startup.
- **Identity and resource access.** Passwords are hashed with BCrypt. Spring Security validates the signature, issuer, expiry and user ID of each HS256 JWT. The token identifies the caller. Ownership and current grants are checked against MySQL per item, so changing a role takes effect on the next request.
- **Concurrent writes.** JPA `@Version` detects competing edits, including two clients that submit the same previously valid version. The API returns the committed version after a conflict. Sharing, revocation and deletion lock the same item row to serialize changes to access and deletion state.
- **Audit and deletion.** Hibernate Envers audits `Item`. A custom revision entity and listener associate each revision with the authenticated user's login. `AuditReader` retrieves snapshots in revision order. A soft delete hides the live row from normal queries and records a `DEL` snapshot. The per-item version used for PATCH and the Envers revision number serve different purposes.
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

To run the API directly with Maven, install **JDK 21**, start MySQL 8.4, and create the `notes` database and an application user with privileges to run Flyway migrations and read/write data in that database. With the default database name and user, run the following in Bash.

```bash
read -rsp 'MySQL password for notes: ' DB_PASSWORD && echo
export DB_PASSWORD
export JWT_SECRET=$(openssl rand -base64 32)
./mvnw spring-boot:run
```

Generate the JWT key once for the session. Reuse the same key on subsequent starts to keep existing unexpired tokens valid. In PowerShell, use `.\mvnw.cmd` for Maven commands and set variables through `$env:`.

## Tests

Run the Maven wrapper from the repository root with JDK 21.

| Command | What runs |
| --- | --- |
| `./mvnw test` | Unit tests through Surefire. No database or Docker required. |
| `./mvnw clean verify` | Unit tests and Failsafe integration tests (`*IT`). Start Docker first. Testcontainers starts a separate MySQL instance and Flyway applies the migrations. |

The [authorization tests](src/test/java/com/example/notes/item/permission/ItemAccessPolicyTest.java) cover owner, editor, viewer and unrelated users. The [editing tests](src/test/java/com/example/notes/item/service/ItemEditingServiceTest.java) cover stale versions and concurrent write failures. The [sharing tests](src/test/java/com/example/notes/item/permission/ItemSharingServiceTest.java) cover self-sharing and replacing an existing role.

Two integration tests exercise the HTTP endpoints and their backing services. [ItemHistoryIT](src/test/java/com/example/notes/item/ItemHistoryIT.java) creates a note, applies several edits and verifies the authors and snapshots returned by the history endpoint. [LoginRateLimitIT](src/test/java/com/example/notes/auth/ratelimit/LoginRateLimitIT.java) sends six requests from one IP, verifies `429` and `Retry-After`, and checks that another IP can still log in. GitHub Actions also starts the Compose stack and registers a user through its published HTTP port.
