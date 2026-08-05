# CLAUDE.md — publicbackend

This file provides context for AI-assisted development of the `publicbackend` project.

`{{BASE_PACKAGE}}` = `com.tarnvik.publicbackend.commuter`

## Session start

Before generating any Java code, read `src/main/resources/checkstyle.xml` to avoid style violations.

## Sensitive Files

**Never read any gitignored `src/main/resources/application-*.properties` file unless explicitly instructed.**
This currently means `application-local.properties` and `application-travel.properties`, and any future
sibling — the rule is the pattern, not the list, because a new profile file will not announce itself.

They contain real secrets: API keys (Anthropic, Samtrafiken, Pushover), database passwords, and OAuth
credentials. `application.properties` and `src/test/resources/application-test.properties` are tracked in git
and safe to read.

If instructed to read one, first warn that its contents will be visible in the conversation and may be
retained in Anthropic's systems, then wait for confirmation before proceeding.

To check what a profile configures without exposing values, read the *keys* only — e.g.
`grep -o '^[^=]*' src/main/resources/application-travel.properties` — or check which properties the code
expects via `application.properties`, which holds the same keys with `${ENV_VAR}` placeholders.

## Code Conventions

### Records vs classes
Use records only for types with **3 or fewer fields**. For 4+ fields, use `@Value` + `@Builder` instead —
positional constructors become unreadable at that size and named builder parameters make call sites
self-documenting.

## Project Overview

Personal Stockholm commuter dashboard backend. Handles Google OAuth2 authentication, user management (access requests, allowed users), settings persistence, and AI interpretation of SL deviation messages via the Claude API. Serves the developer and a few friends.

- **Backend:** Spring Boot 4.1.0 (Java 25)
- **Frontend:** React SPA on GitHub Pages at `https://jtarnvik.github.io/sl-dashboard/`
- **Production hosting:** Render.com
- **Database:** Supabase (PostgreSQL) in production, MySQL 8.x locally

---

## Spring Boot 4 — Important Notes

This project uses **Spring Boot 4.0.x**, which introduced significant modularization
compared to Spring Boot 3.x. Many autoconfiguration classes moved to new packages and
new module-specific starters were introduced. Key differences to be aware of:

- Liquibase autoconfiguration is in `spring-boot-liquibase`, not bundled in the main starter.
  Both `spring-boot-liquibase` AND `org.liquibase:liquibase-core` are required.
- Spring Session JDBC autoconfiguration requires both `spring-boot-session-jdbc` AND
  `org.springframework.session:spring-session-jdbc`.
- The old `spring.autoconfigure.exclude` path for DataSource changed to:
  `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`
- Hibernate 7 is bundled — MySQL8Dialect was removed, use `MySQLDialect` or omit entirely
  (Hibernate 7 auto-detects).
- Jackson 3.x is used (tools.jackson groupId, not com.fasterxml.jackson).
- Spring Security 7 and Spring Framework 7 are included.
- `UrlBasedCorsConfigurationSource` uses `registerCorsConfiguration()` (not `registerCorsMapping()`).

When adding new dependencies, check if Spring Boot 4 has a dedicated `spring-boot-*` module
for autoconfiguration in addition to the underlying library dependency.

---

## Architecture

```
React (GitHub Pages)
        ↓ HTTPS
Render.com (Spring Boot Docker container)
        ↓ JDBC (PostgreSQL, SSL, connection pooler)
Supabase (PostgreSQL)
```

Authentication flow:
1. Frontend calls `GET /api/auth/me` silently on load
2. If 401 → show login button
3. Login button navigates to `/oauth2/authorization/google`
4. Google redirects back to `/login/oauth2/code/google` (handled by Spring Security)
5. Email whitelist check in `AuthenticationSuccessHandler`
6. Session stored in Supabase via Spring Session JDBC
7. Browser holds `SESSION` cookie for subsequent requests

---

## Infrastructure

### Render.com
- Free tier web service (may sleep after inactivity — UptimeRobot pings `/ping` every 5 min)
- Deployed via Dockerfile (multi-stage build: Maven build → JRE Alpine runtime image)
- Auto-deploys on push to GitHub main branch
- Service URL: `https://tarnvik.onrender.com`
- Health check path: `/ping`

### Supabase
- Free tier PostgreSQL (no expiration unlike Render's own PostgreSQL)
- Region: EU West (Ireland)
- Connection via **Supavisor connection pooler** (required — direct connection is IPv6 only,
  Render does not support IPv6)
- Pooler host: `aws-1-eu-west-1.pooler.supabase.com`
- Port: `5432`
- Username format: `postgres.<project-ref>` (not just `postgres`)
- Always use `?sslmode=require` in JDBC URL

### Local Development (MySQL)
- MySQL 8.x at `192.168.1.204:3306`, database: `commuter`
- Activated via Spring profile `local` (IntelliJ VM option: `-Dspring.profiles.active=local`)
- Local config in `application-local.properties` (gitignored)

---

## Environment Variables

### Render (production)
| Variable | Description |
|---|---|
| `GOOGLE_CLIENT_ID` | Google OAuth2 client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 client secret |
| `ALLOWED_EMAILS` | Comma-separated whitelist of allowed email addresses |
| `DB_URL` | `jdbc:postgresql://aws-1-eu-west-1.pooler.supabase.com:5432/postgres?sslmode=require` |
| `DB_USERNAME` | `postgres.<project-ref>` |
| `DB_PASSWORD` | Supabase database password |
| `FRONTEND_URL` | `https://jtarnvik.github.io` |
| `ANTHROPIC_API_KEY` | API key for Claude AI deviation interpretation |
| `PUSHOVER_API_TOKEN` | Pushover app token for error notifications |
| `PUSHOVER_USER_KEY` | Pushover user key for error notifications |

### Local (`application-local.properties`, never committed)
```properties
server.port=8080
spring.security.oauth2.client.registration.google.client-id=<value>
spring.security.oauth2.client.registration.google.client-secret=<value>
spring.datasource.url=jdbc:mysql://192.168.1.204:3306/commuter
spring.datasource.username=jesper
spring.datasource.password=<value>
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
app.allowed-emails=<comma separated emails>
app.frontend-url=http://localhost:5173
anthropic.api-key=<value>
```

---

## Key Dependencies (pom.xml)

```xml
<!-- Core -->
spring-boot-starter-data-jpa
spring-boot-starter-security
spring-boot-starter-security-oauth2-client
spring-boot-starter-webmvc
spring-boot-starter-validation
lombok
org.mapstruct:mapstruct

<!-- Database -->
spring-boot-liquibase                          <!-- SB4 autoconfiguration module -->
org.liquibase:liquibase-core                   <!-- Liquibase classes -->
org.postgresql:postgresql (runtime)
com.mysql:mysql-connector-j (runtime)
com.h2database:h2 (runtime, test profile)

<!-- Session -->
spring-boot-session-jdbc                       <!-- SB4 autoconfiguration module -->
org.springframework.session:spring-session-jdbc

<!-- AI -->
com.anthropic:anthropic-java
```

---

## Database Schema Management (Liquibase)

Changelogs live in `src/main/resources/db/changelog/`:
```
db/
  changelog/
    db.changelog-master.xml      ← master file, includes all changesets
    changes/
      001-create-sessions-table.xml
      002-fix-session-attributes-bytes.xml
      ...
```

**Important:** Changeset 001 created the session tables with `BLOB` type which mapped to
`OID` in PostgreSQL (wrong). Changeset 002 (PostgreSQL only, `dbms="postgresql"`) drops
and recreates the tables with correct `BYTEA` type. MySQL uses `LONGBLOB` from `BLOB`
and works correctly.

When adding new changesets:
- Never modify existing changesets — always add new ones
- Use `dbms="postgresql"` or `dbms="mysql"` attributes when SQL differs between databases
- Use Liquibase abstract types (`BLOB`, `CLOB`, `BOOLEAN` etc.) for cross-database compatibility
- Increment the changeset ID sequentially (003, 004, ...)
- `spring.jpa.hibernate.ddl-auto=none` — Liquibase owns the schema, Hibernate does not

---

## Spring Session JDBC

Sessions are stored in Supabase/MySQL rather than in memory, so sessions survive
backend redeployments. Users do not need to re-login after a Render redeploy.

Tables: `spring_session` and `spring_session_attributes`
Cookie name: `SESSION` (not `JSESSIONID`)
Config: `spring.session.jdbc.initialize-schema=never` — Liquibase creates the tables

---

## Security Configuration

- Google OAuth2 login via Spring Security
- Email whitelist enforced in `AuthenticationSuccessHandler` (not via roles/authorities)
- `/ping` and `/api/public/**` are public
- `/api/auth/me` is permitAll but returns 401 when unauthenticated (no auto-redirect)
- All `/api/**` paths return 401 for unauthenticated requests instead of redirecting to OAuth2 login (configured via `exceptionHandling().defaultAuthenticationEntryPointFor()` with `PathPatternRequestMatcher`)
- `/api/protected/**` requires authentication
- CSRF disabled (SPA + CORS provides equivalent protection)
- CORS configured for `${FRONTEND_URL}` only, credentials allowed
- Logout at `POST /api/auth/logout` — invalidates session, clears cookie, redirects to `/ping`

---

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/ping` | Public | Health check, returns "ok" |
| POST | `/api/public/access-request` | Public | Submit an access request |
| GET | `/api/auth/me` | Optional | Returns user info (with settings) or 401 |
| POST | `/api/auth/logout` | Optional | Clears session and cookie |
| GET | `/api/protected/gtfs/route-groups` | User | List selectable monitored route groups (transportMode, routeGroup, displayName) |
| GET | `/api/protected/gtfs/status` | User | GTFS data availability: `date`, `status` (enum name), `staticDataAvailable` (derived from in-memory dataset) |
| GET | `/api/protected/gtfs/route-group-stops` | User | Every stop of every route group, for the favourite stop picker. Empty when the GTFS dataset is not loaded |
| GET | `/api/protected/gtfs/route-data` | User | Live data for one route group. Params: `transportMode`, `routeGroup`, `focused` (forced true for `onlyFocused` groups). Returns `RouteDataResponse`: `status`, `liveTrip` (the stop chain, cropped when focused, sent once), `vehicles` rebased onto it, and `focus` (truncation flags + approaching counts) |
| PUT | `/api/protected/settings` | User | Save stop point settings and favourite stops. `favouriteStops` null means unchanged, `[]` clears |
| DELETE | `/api/protected/account` | User | Delete own account (cascade removes all data, invalidates session). Returns 409 if last admin. |
| POST | `/api/protected/deviations/interpret` | User | Interpret a list of deviation texts via Claude AI |
| POST | `/api/protected/deviations/{id}/hide` | User | Hide a deviation by its DB id |
| DELETE | `/api/protected/deviations/hidden` | User | Clear all hidden deviations for the current user |
| POST | `/api/protected/routes` | User | Create a shared route link; returns `{ id }`. Increments `ROUTES_SHARED` stat. |
| GET | `/api/admin/access-requests/count` | Admin | Count pending access requests |
| GET | `/api/admin/access-requests` | Admin | List pending access requests |
| POST | `/api/admin/access-requests/{id}/approve` | Admin | Approve an access request |
| DELETE | `/api/admin/access-requests/{id}` | Admin | Reject/delete an access request |
| GET | `/api/admin/users` | Admin | List allowed users |
| DELETE | `/api/admin/users/{id}` | Admin | Delete an allowed user |
| GET | `/api/admin/statistics` | Admin | Usage statistics (`routesShared`, `aiInterpretationQueries`, `userCount`) |
| GET | `/api/admin/gtfs/status` | Admin | Most recent `gtfs_download_log` entry (phase timestamps, error message) |
| POST | `/api/admin/gtfs/run-pipeline` | Admin | Run the GTFS pipeline manually |
| POST | `/api/admin/gtfs/reset` | Admin | Reset the most recent entry to `DOWNLOAD_DONE` and clear the GTFS tables. 409 while a download is in flight |
| GET | `/api/public/routes/{id}` | Public | Fetch a shared route by ID; returns `{ routeData }` (serialized Journey JSON) |

---

## Scheduled Jobs

Live in `{{BASE_PACKAGE}}.port.incoming.scheduled`.

| Class | Schedule | What it does |
|---|---|---|
| `PendingUserCleanupJob` | `0 0 0 * * *` | Deletes `pending_user` rows older than 7 days (users who failed OAuth2 login and never requested access) |
| `DeviationInterpretationCleanupJob` | `0 0 0 * * *` | Archives `deviation_interpretations` rows older than 28 days to `deviation_history`, then deletes them along with their `deviation_interpretation_errors` rows |
| `DashboardRefreshJob` | `0 0 * * * *` | Repaints the terminal dashboard hourly. The only clock in that feature — see the Terminal dashboard section |

`spring.task.scheduling.pool.size=3`: the memory monitor and the dashboard refresh must both be
able to run while the GTFS pipeline holds a thread for a long stretch.

---

## AI Deviation Interpretation

Deviation texts from the frontend are interpreted by Claude AI and cached in the database.

**Flow:**
1. Frontend POSTs a list of deviation texts to `/api/protected/deviations/interpret`
2. Each text is SHA-256 hashed and looked up in `deviation_interpretations`
3. Cache hit with no error → use existing result
4. Cache miss or AI error → call Claude API (concurrently via virtual threads)
5. Result returned: DB id, importance (`LOW`/`MEDIUM`/`HIGH`/`UNKNOWN`), and action

**Actions returned:**
- `SHOWN` — display normally
- `HIDDEN_ACCESSIBILITY` — deviation only concerns accessibility (elevators, escalators)
- `HIDDEN_BY_USER` — user previously hid this deviation
- `UNKNOWN` — AI interpretation failed

**Error handling:** Repeated failures for the same hash are tracked in `deviation_interpretation_errors`. After 5 failures the hash is locked for 24 hours and a Pushover notification is sent.

**Concurrency:** A `ConcurrentHashMap<String, CompletableFuture<DeviationInterpretation>>` keyed by hash ensures only one Claude call per unique deviation text even under concurrent requests.

**`AllowedUser` injection:** Controllers receive `AllowedUser` as a method parameter resolved by `AllowedUserArgumentResolver` (registered in `WebMvcConfig`). It looks up the user by email from the `OAuth2User` principal and throws 401 if not found.

**Prefer `AllowedUser` over email strings in services:** When a controller already has an `AllowedUser` parameter, pass it directly to service methods rather than extracting the email and re-looking up the user inside the service. This avoids redundant DB lookups and removes the need for services to validate that the user exists — the resolver already guarantees that. Service methods that operate on behalf of the current user should accept `AllowedUser`, not `String email`.

---

## Integration Tests

Tests use `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` with an H2 in-memory database. Liquibase runs all changesets on startup, including seed data.

**Seeded users in H2:** Changeset 004 inserts `jtarnvik@gmail.com` and `htarnvik@gmail.com` into `allowed_user`. Changeset 008 sets `jtarnvik@gmail.com` to role `ADMIN`. Tests that check admin counts or need a clean user state must account for this — use a `@BeforeEach` to temporarily demote pre-existing admins and restore them in `@AfterEach`. See `DeleteAccountTest` for the pattern.

**OAuth2 principal:** Endpoints that use `AllowedUserArgumentResolver` (i.e. receive `AllowedUser` as a controller parameter) require a real `OAuth2User` principal — `@WithMockUser` is insufficient. Use `oauth2Login().attributes(attrs -> attrs.put("email", email))` as a MockMvc post-processor.

---

## Local Development

1. Ensure MySQL is running at `192.168.1.204:3306` with database `commuter`
2. Ensure `application-local.properties` exists with correct values (see above)
3. Run with IntelliJ using VM option: `-Dspring.profiles.active=local`
4. OAuth2 redirect URI for local: `http://localhost:8080/login/oauth2/code/google`
5. Frontend dev server expected at `http://localhost:5173`

## Deployment

Two targets exist while the move off Render is in progress. The Mac Mini will **fully replace**
Render — they are not meant to run side by side. The work lives on the branch `local_host` and is
deliberately **not merged to `main` until Render is decommissioned**, because Render builds `main`
and would pick up deployment-only settings (notably `server.port=8081`).

### Render (current production)

Push to GitHub main branch → Render auto-detects and builds via Dockerfile →
Liquibase runs migrations on startup → app serves traffic.

Build command is handled entirely by the Dockerfile (multi-stage Maven build).
No separate build command needed in Render config.

### Mac Mini (`just`)

Tag-driven: bump and tag on the dev machine, then pull/build/start on the Mini. The Mini keeps its
own clone (e.g. `~/develop/production/publicbackend`) that only ever sits on a tag. Mirrors the
`cl-media` setup.

**1. Bump the version (dev machine), from the repo root:**
```bash
./change-version.sh <version>      # e.g. 1.0.0 — no leading "v"
```
`mvn versions:set` → regenerates `build.just` via `mvn process-resources` → `mvn versions:commit` →
`git add pom.xml build.just` → commit → `git tag -a v<version>`. It does **not** push; it copies the
push command to the clipboard.

**2. Push the commit *and* the tag** — the Mini keys off the tag, not the commit:
```bash
git push && git push origin v<version>
```

**Prerequisites on the Mini:** `just`, Maven, **JDK 25** (the LTS — deliberately not 24, which is
end-of-life), and a PostgreSQL with the `commuter` database and the role named in `DB_URL`.
`just build` shells out to plain `mvn`/`java`, so whichever JDK the shell resolves is the one that
builds — with several installed, 25 has to win or the `--release 25` compile fails.

**First time only, on the Mini:** `just pull` is `fetch` + `checkout`, so the clone has to exist
first, and the secrets file is never in git:
```bash
git clone git@github.com:jtarnvik/publicbackend.git
cd publicbackend
cp deployment/publicbackend.env.example deployment/publicbackend.env
chmod 600 deployment/publicbackend.env      # then fill in the real values
```
The env file survives later `just pull`/`just build` runs — it is gitignored, so `checkout` leaves
it alone, and `prepare_release` only wipes `deployment/bin`.

**3. On the Mac Mini, from the repo root:**
```bash
just pull     # git fetch --tags, checkout the highest version tag (sort -version:refname)
just build    # build_release (mvn verify → release/) + prepare_release (→ deployment/bin)
just start    # foreground; deployment/bin/start-publicbackend.sh
```
Verify with `curl localhost:8081/ping` → `ok`, not through the frontend — the deployed frontend
still points at Render.
`just doit` chains all three. It re-invokes `just` per step on purpose: `pull` rewrites the justfiles
and `just` parses them once at startup, so a single-process chain would build the newly checked-out
source using the *previous* version's jar name.

**Generated file:** `build.just` is produced from `build.just.template` by the `maven-antrun-plugin`
(execution `generate-justfile`, bound to `process-resources`), substituting `@project.version@`.
Edit the template, never `build.just`. It is **committed** deliberately — the Mini checks out a tag
and builds immediately, so the jar name for that version must already be in the tag. The antrun copy
is `failonerror="false"` because the Docker build context has no template; `change-version.sh`
checks the output exists instead.

**Secrets** live in `deployment/publicbackend.env` on the Mini only (gitignored, `chmod 600`), copied
from the committed `deployment/publicbackend.env.example`. `start-publicbackend.sh` sources it, so
the same variable names Render uses resolve the `${DB_URL}`-style placeholders in
`application.properties` unchanged. Heap is `JAVA_OPTS` from that file (default `-Xmx2g`).

**Run directory** is `deployment/bin`, not `target/` — `mvn clean` must not be able to delete the jar
out from under a running instance. `deployment/bin`, `deployment/logs`, `deployment/*.env` and
`release/` are gitignored.

### Profiles

Four profiles, and every one of them is named. There is no unprofiled mode.

| Profile | Where | Properties file | Tracked in git |
|---|---|---|---|
| `production` | Mac Mini deployment, set by `start-publicbackend.sh` | `application-production.properties` | yes |
| `local` | Development on the dev machine | `application-local.properties` | no (secrets) |
| `travel` | Development away from the home network | `application-travel.properties` | no (secrets) |
| `test` | Integration tests (`@ActiveProfiles("test")`) | `src/test/resources/application-test.properties` | yes |

*Why `production` is explicit rather than "no profile active"* (which is what it was until the
terminal dashboard work): deployment-only behaviour then gates **positively** — `@Profile("production")`,
`<springProfile name="production">`. The alternative was `!local & !travel & !test`, a negation that
every future development profile would have to be added to, in Java annotations and in
`logback-spring.xml`, forever — and which is *true* under the integration tests, so anything gated
that way would try to start during a test run.

`server.port` deliberately stays at **8081** in `application.properties` rather than moving into
`application-production.properties`: it is the default so that a deployment accidentally started
without the profile still binds the deployed port instead of silently falling back to Boot's 8080.
`application-local.properties` overrides it to 8080 so development keeps the OAuth2 redirect URI
already registered with Google.

### Logging

`logback-spring.xml` imports Spring Boot's own `defaults.xml` / `console-appender.xml` /
`file-appender.xml` rather than restating patterns and rolling policy. Under `production` both
CONSOLE and FILE are attached; under every other profile, CONSOLE only.

The file path and rolling limits are properties, not XML: `logging.file.name` in
`application-production.properties` resolves `${FOLDER_BASE}/logs/publicbackend.log`, where
`FOLDER_BASE` is exported by `start-publicbackend.sh`. That is the same path `just logs` tails.
Setting `logging.file.name` is also what *activates* the FILE appender, since Boot's
`file-appender.xml` reads the `LOG_FILE` derived from it.

The start script does **not** pipe through `tee` — logback owns the file. Piping would make stdout
a pipe rather than a TTY, and the terminal dashboard needs a real terminal to detect a usable
screen. The CONSOLE appender is declared explicitly (rather than left to Boot's programmatic
default) so the dashboard can detach it by a name this project owns when it takes over the screen.

### Terminal dashboard

Under `production` the backend draws a status board on the terminal it was started from, in
`{{BASE_PACKAGE}}.port.outgoing.terminal` (jline). Modelled on the `overlord` project's dashboard,
with the notification mechanism rebuilt on Spring events.

**One thread owns the screen.** Every terminal write happens on a single virtual render thread.
Event listeners and the WINCH handler never draw — they set a flag and wake it via a capacity-1
queue, so a burst of events collapses into one repaint. This is not a poll loop: the thread blocks
on `take()` and consumes nothing while idle. The reason it exists rather than `@Async` listeners is
`printAt`, which addresses the cursor and then writes as two calls — two threads interleaving those
pairs would print each other's text at each other's coordinates.

**Event-driven, with one clock.** Listeners are synchronous and do no I/O, so they cannot block the
publisher (an HTTP request thread for user activity, the poll-loop virtual thread for realtime
state).

| Event | Published by | Listener phase |
|---|---|---|
| `RealtimePollingStateChangedEvent` | `GtfsRealtimeService.GtfsRealtimeCache` start/stop | `@EventListener` (no transaction) |
| `UserActivityEvent` | `AllowedUserService.recordLogin()` | `@TransactionalEventListener(AFTER_COMMIT)` — the repaint recounts, and inside the transaction that count could read pre-commit state |

`DashboardRefreshJob` covers the one thing no event can: a user leaves the 14-day active window
because time passed, and nothing runs at that moment to say so.

**Items** live in `…/terminal/items`, are ordinary `@Component`s ordered by `@Order`, and are
assigned a start row by `DashboardService` from each item's `rowCount()` — inserting one never
requires editing its neighbours. `refresh()` is for expensive state (a query) and runs only on
data events; `redraw()` also runs on a bare resize and must stay cheap. Current items: `VersionItem`
(`BuildProperties`), `RealtimePollingItem`, `ActiveUsersItem`.

**Console logging is detached** once the board is up, by removing the `CONSOLE` appender named in
`logback-spring.xml`. It stays attached through startup on purpose: a deployment that fails before
`ApplicationReadyEvent` still prints diagnostics to the terminal, and since the board lives in the
alternate screen buffer that output is still there after exit.

**Shutdown needs two paths, and both were verified rather than assumed.**

*A SIGINT handler is mandatory.* Building a jline **system** terminal installs native signal
handlers, and those take SIGINT away from the JVM. Measured: without a handler, Ctrl-C kills the
process with no shutdown hook, no bean destruction and no terminal restore, leaving the shell on
the alternate screen with an invisible cursor — needing a manual `reset`. `DashboardService`
therefore handles `Signal.INT` and closes the context explicitly. SIGTERM is unaffected and still
goes through Spring's shutdown hook, so both routes end in `@PreDestroy`.

*The restore is written to `System.out`, not through the jline terminal.* By the time bean
destruction runs the terminal can already be closed, and writing through it throws "Terminal has
been closed". The escape sequence is resolved from terminfo at startup — while the terminal is
definitely open — and replayed at shutdown, with xterm-family fallbacks if terminfo has no entry.

If the terminal is not usable — stdout piped or redirected, type `dumb`, zero columns — the board
disables itself, logs why, and leaves console logging alone.

### Version at runtime

The `build-info` goal of `spring-boot-maven-plugin` generates `META-INF/build-info.properties`,
which Boot auto-configures into a `BuildProperties` bean — inject it to read the running version.
Preferred over a filtered `version.properties`: no resource-filtering configuration, and it cannot
drift out of step with the pom.

The GTFS in-memory dataset used to be gated on the `local` profile, which is why live traffic never
worked on Render. That gate is gone on this branch — see I5 in the frontend `CLAUDE.md` for why it
existed and why merging this branch to `main` while Render still runs would bring the OOM kills back.

## Database development

### Row Level Security (PostgreSQL)

All tables created in PostgreSQL must have row level security enabled. Include the following SQL in every Liquibase changeset that creates a table, using `dbms="postgresql"`:

```xml
<sql dbms="postgresql">ALTER TABLE <table_name> ENABLE ROW LEVEL SECURITY;</sql>
```

This must be a separate `<sql>` tag within the same changeset, not a separate changeset.

## GTFS Static and Realtime Data

This chapter documents findings from manual exploration of the Samtrafiken GTFS Regional
feed and the SL GTFS-RT VehiclePositions feed, using Stockholm pendeltåg line 43 as the
reference line throughout.

### Data Sources

- **Static feed:** GTFS Regional (Stockholm) from Trafiklab — downloaded periodically, roughly weekly updates
- **Realtime feed:** GTFS Regional Realtime — VehiclePositions (Protobuf format) from Trafiklab
- **API library:** `com.google.transit:gtfs-realtime-bindings:0.0.8` for Protobuf parsing

### Key Identifiers

| Value | Meaning |
|---|---|
| `9011001004300000` | `route_id` for line 43 pendeltåg |
| `9022001xxxxxxxxx` | `stop_id` format used by Samtrafiken |
| `9021001xxxxxxxxx` | `parent_station` format (station grouping stops/platforms) |

---

### GTFS Static Feed

#### File Overview and Sizes (Stockholm Regional Feed)

| File | Size | Purpose |
|---|---|---|
| `agency.txt` | ~1KB | Transit operators in the feed |
| `attributions.txt` | ~2.8MB | Legal attribution requirements |
| `booking_rules.txt` | ~4KB | Rules for bookable/flex services |
| `calendar.txt` | ~26KB | Weekly service patterns (all zeros — not used by Samtrafiken) |
| `calendar_dates.txt` | ~180KB | Explicit date-based service definitions |
| `feed_info.txt` | tiny | Feed metadata, version, validity dates |
| `routes.txt` | ~31KB | All routes across all operators |
| `shapes.txt` | ~147MB | Route polyline geometry |
| `stop_times.txt` | ~140MB | Scheduled times at each stop per trip — largest file |
| `stops.txt` | ~1.5MB | Stop names and coordinates |
| `transfers.txt` | ~1.1MB | Interchange rules between routes |
| `trips.txt` | ~6MB | All trips across all operators (~88,600 rows) |

**Important:** `stop_times.txt` is ~140MB. Always filter by `trip_id` early — never load it fully into memory.

#### File Relationships and Join Chain

```
routes.txt       →  route_id
                        ↓
trips.txt        →  trip_id, service_id, shape_id, direction_id
                        ↓                    ↓
stop_times.txt   →  stop_id, stop_sequence,  calendar_dates.txt
                    arrival_time,            → which dates this trip runs
                    shape_dist_traveled
                        ↓
stops.txt        →  stop_name, stop_lat, stop_lon
```

#### routes.txt

**Key fields:** `route_id`, `agency_id`, `route_short_name`, `route_long_name`, `route_type`

**Finding:** Samtrafiken uses extended GTFS route types (not standard small integers):
- `100` = Railway/Rail service (pendeltåg)
- `700` = Bus
- `900` = Tram
- Metro (lines 17/18/19): type code **not yet verified** — do not assume `401`; check `routes.txt` before filtering.

Standard GTFS types (2=rail, 3=bus) are not used. Filter by `route_short_name` to find a line by its public number.

#### trips.txt

**Key fields:** `route_id`, `service_id`, `trip_id`, `direction_id`, `shape_id`, `samtrafiken_internal_trip_number`

**Findings:**
- Each row is one specific scheduled journey (one train, one direction, one departure)
- Line 43 has **1348 trips** in the full feed (all days, both directions)
- `trip_headsign` and `trip_short_name` are **empty** — do not rely on them
- `shape_id` is populated — use for drawing route geometry from `shapes.txt`
- `direction_id`: 0 or 1 distinguishing the two directions
- `samtrafiken_internal_trip_number` is a Samtrafiken extension field, not standard GTFS

#### calendar.txt and calendar_dates.txt

**Finding:** Samtrafiken does **not** use the weekday pattern columns in `calendar.txt` — every row has `0,0,0,0,0,0,0` for all days. Ignore `calendar.txt` entirely.

All scheduling is done via `calendar_dates.txt` with explicit dates:
- `exception_type = 1` → service runs on this date
- `exception_type = 2` → service removed on this date

The feed contains **707 unique service_ids** shared across ~88,600 trips. Line 43 alone uses **152 different service_ids**, of which **~41 are active on any given weekday**. Swedish public holidays are already excluded from weekday service_ids — Samtrafiken handles this.

**To find trips running today:**
1. Query `calendar_dates.txt` for today's date with `exception_type = 1` → collect active `service_id` values
2. Filter `trips.txt` by `route_id` AND active `service_id` values

For line 43 this yields approximately **230 trips on a normal weekday** (both directions, full day).

#### stop_times.txt

**Key fields:** `trip_id`, `arrival_time`, `departure_time`, `stop_id`, `stop_sequence`, `stop_headsign`, `pickup_type`, `drop_off_type`, `shape_dist_traveled`, `timepoint`

**Findings:**
- `stop_sequence` is populated and sequential — the primary ordering field for the schematic
- `shape_dist_traveled` is populated (meters along route) — use for proportional stop spacing in the schematic
- `stop_headsign` contains the **destination name** (last stop) and is consistent across all stops in a trip — reliable for display as "Train to X"
- `timepoint = 1` means exact scheduled times (not interpolated)
- Terminus behaviour via `pickup_type`/`drop_off_type`: first stop = pickup only, last stop = drop-off only

**Line 43 example trip** (trip_id `14010000656749468`):
- 20 stops, Bålsta → Västerhaninge
- Duration: ~75 minutes (08:24 → 09:39)
- Distance: 74,168 meters

#### stops.txt

**Key fields:** `stop_id`, `stop_name`, `stop_lat`, `stop_lon`, `location_type`, `parent_station`, `platform_code`

**Findings:**
- `location_type = 0` = individual stop/platform
- `parent_station` groups platforms under a station entity
- `platform_code` is populated — available for display if needed
- Coordinates are reliable and match real-world positions

---

### GTFS-RT VehiclePositions Feed

#### Parsing

```java
FeedMessage feed;
try (FileInputStream fis = new FileInputStream("/tmp/VehiclePositions.pb")) {
    feed = FeedMessage.parseFrom(fis);
}
```

The Stockholm regional feed contains approximately **1750 vehicle entities** at any given time.

#### Field Availability — Critical Findings

| Field | Populated? | Notes |
|---|---|---|
| `trip.route_id` | ❌ Never | Cannot filter by route_id in RT feed |
| `trip.trip_id` | ✅ Always | Primary join key to static data |
| `trip.direction_id` | ⚠️ Unreliable | Present but not trustworthy — derive from static data |
| `position.latitude` | ✅ Always | |
| `position.longitude` | ✅ Always | |
| `position.bearing` | ⚠️ Sometimes | Often 0.0 — treat 0.0 as missing |
| `current_stop_sequence` | ❌ Never | Always 0 — cannot use for stop placement |
| `stop_id` | ❌ Never | Always empty |
| `current_status` | ✅ Always | `IN_TRANSIT_TO` or `STOPPED_AT` |
| `timestamp` | ✅ Always | Unix timestamp of position report |

#### Matching RT to Static Data

Since `route_id` is not present in the RT feed, matching is done via `trip_id`:

```
RT trip_id → trips.txt → confirms route_id (is this line 43?)
RT trip_id → stop_times.txt → ordered stop sequence for this trip
RT lat/lon  → geometric matching against stop coordinates → position on schematic
```

**To find all line 43 vehicles:**
1. Load all line 43 `trip_id` values into a `HashSet` (from cached trips table)
2. For each RT entity, check if `trip.getTripId()` is in the set
3. At 17:34 on a weekday, line 43 had **17 active vehicles**

#### Vehicle Placement on Schematic

Since `current_stop_sequence` is never populated, vehicle position on the schematic must be derived geometrically:

1. Get the ordered stop sequence for the vehicle's `trip_id` from `stop_times.txt`
2. Get coordinates for each stop from `stops.txt`
3. For each consecutive stop pair, calculate distance from vehicle lat/lon to that segment
4. The segment with minimum distance determines which two stops the vehicle is between
5. Use `shape_dist_traveled` from `stop_times.txt` for proportional placement within the segment

Use the Haversine formula for lat/lon distance calculations (available in standard libraries — do not implement from scratch).

---

### Manual File Analysis — Shell Commands Reference

```bash
# Find route_id for a line
head -1 routes.txt && grep ",43," routes.txt

# Count trips for a route
grep 9011001004300000 trips.txt | wc -l

# Inspect trip structure
head -1 trips.txt && grep 9011001004300000 trips.txt | head -1

# Get unique service_ids for a route
grep 9011001004300000 trips.txt | cut -d',' -f2 | sort -u

# Save all service_ids for a route
grep 9011001004300000 trips.txt | cut -d',' -f2 | sort -u > line43_service_ids.txt

# Find which service_ids are active today
grep "^" line43_service_ids.txt | while read sid; do grep "^$sid,20260415,1" calendar_dates.txt; done

# Count trips running today for a route
grep "20260415,1" calendar_dates.txt | cut -d',' -f1 > today_service_ids.txt
grep 9011001004300000 trips.txt | grep -F -f <(sed 's/.*/,&,/' today_service_ids.txt) | wc -l

# Save all trip_ids for a route
grep 9011001004300000 trips.txt | cut -d',' -f3 > alltrips.txt

# Inspect stop_times for a trip
head -1 stop_times.txt && grep 14010000656749468 stop_times.txt

# Look up stops by stop_id
head -1 stops.txt && grep "9022001006101001\|9022001006171002" stops.txt
```

---

### Database Caching Strategy

Static GTFS data for the monitored lines is cached in PostgreSQL via the pipeline below, then loaded into
the in-memory `GtfsDataset` on startup. `/tmp` is the working area for download and unzip; the DB is the
durable store. All GTFS tables use natural GTFS keys — no synthetic IDs, no timestamp columns. RLS enabled
on all tables per project rule.

**Pending:** `feed_version` column on `gtfs_download_log` — populate from `feed_info.txt` during parse,
show in the GTFS status admin view.

## GTFS Pipeline

`GtfsDownloadJob` (`port.incoming.scheduled`) fires at 05:00 and on `ApplicationReadyEvent`. It is guarded
by `gtfs_download_log` (one attempt per date); the local profile adds a cap of 15 downloads per 30 days.
`GtfsPipelineService.runPipeline()` orchestrates the phases: download → unzip → parse, then rebuilds the
in-memory dataset and calls `verifyRealtimeFeed()` — a single realtime fetch whose result is discarded, to keep
the realtime API exercised and to surface credential/quota/format problems in a log that is being read anyway.
Each phase writes start/end timestamps to `gtfs_download_log`. `GtfsDownloadException` (unchecked) aborts the pipeline;
`RestExceptionHandler` returns HTTP 500 for any that reach a controller. Pipeline failures trigger a Pushover
notification via `PushoverProvider.sendGtfsPipelineErrorNotification(phase, message)`.

`gtfs_monitored_route` is the source of truth for which lines are tracked. Seeded via Liquibase:
43/44 (TRAIN), 112/117 (BUS), 17/18/19 (METRO). Variant matching (e.g. 43X) is a uniform regex rule in
`GtfsNameUtil`, not a per-row flag. 112 exists to exercise route presentation logic — not shown in the
deviation pane. `@Profile("!test")` prevents the job from running in integration tests.

### Service responsibilities

Five services, deliberately split by phase so each has one reason to change.

| Service | Owns |
|---|---|
| `GtfsPipelineService` | Orchestration only: `recover → download → unzip → parse → rebuildDataset`. Plus `resetToDownloadDone()` (clears the 5 GTFS tables + unzip dir in one transaction). No logic of its own. |
| `GtfsDownloadService` | `/tmp/sl-gtfs-cache` and the zip. Download (once per date), unzip, crash recovery from a stuck `PARSE_START`. |
| `GtfsParseService` | CSV → DB. Filters the feed to monitored routes and writes the 5 GTFS tables. Owns the batching and `entityManager` lifecycle (class Javadoc). |
| `GtfsAccessService` | DB → memory. Holds the `AtomicReference<GtfsDataset>` and serves the read endpoints (`route-groups`, `status`). Never touches files. |
| `GtfsRealtimeService` | The live side: RT positions joined to the static dataset by `trip_id`. WIP — see the C-block in the frontend `CLAUDE.md`. |

Two stateless utils sit alongside: `GtfsNameUtil` (line-name/variant matching, shared by parse and access)
and `GtfsGeometryUtil` (`locateOnRoute()` — projection + Haversine, no Spring dependency).

**Read direction:** static data flows one way — file → DB → `GtfsDataset` — and only `GtfsAccessService`
crosses the DB→memory boundary. Everything serving a request reads from the in-memory dataset, never the
GTFS tables. Keep it that way; it is what makes the nightly rebuild atomic from a caller's point of view.
