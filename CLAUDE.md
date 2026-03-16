# CLAUDE.md — publicbackend

This file provides context for AI-assisted development of the `publicbackend` project.

## Project Overview

Personal Stockholm commuter dashboard backend. Proxies SL Trafiklab APIs and parses
deviation messages using the Claude API. Serves 4 users: the developer and family/friends.

- **Backend:** Spring Boot 4.0.3 (Java 21)
- **Frontend:** React SPA on GitHub Pages at `https://jtarnvik.github.io/sl-dashboard/`
- **Production hosting:** Render.com
- **Database:** Supabase (PostgreSQL) in production, MySQL 8.x locally

---

## Spring Boot 4 — Important Notes

This project uses **Spring Boot 4.0.3**, which introduced significant modularization
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

### Local (`application-local.properties`, never committed)
```properties
spring.security.oauth2.client.registration.google.client-id=<value>
spring.security.oauth2.client.registration.google.client-secret=<value>
spring.datasource.url=jdbc:mysql://192.168.1.204:3306/commuter
spring.datasource.username=jesper
spring.datasource.password=<value>
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
app.allowed-emails=<comma separated emails>
app.frontend-url=http://localhost:5173
```

---

## Key Dependencies (pom.xml)

```xml
<!-- Core -->
spring-boot-starter-data-jpa
spring-boot-starter-security
spring-boot-starter-security-oauth2-client
spring-boot-starter-webmvc
lombok

<!-- Database -->
spring-boot-liquibase                          <!-- SB4 autoconfiguration module -->
org.liquibase:liquibase-core                   <!-- Liquibase classes -->
org.postgresql:postgresql (runtime)
com.mysql:mysql-connector-j (runtime)

<!-- Session -->
spring-boot-session-jdbc                       <!-- SB4 autoconfiguration module -->
org.springframework.session:spring-session-jdbc
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
- `/api/protected/**` requires authentication
- CSRF disabled (SPA + CORS provides equivalent protection)
- CORS configured for `${FRONTEND_URL}` only, credentials allowed
- Logout at `POST /api/auth/logout` — invalidates session, clears cookie, redirects to `/ping`

---

## API Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/ping` | Public | Health check, returns "ok" |
| GET | `/api/auth/me` | Optional | Returns user info or 401 |
| POST | `/api/auth/logout` | Optional | Clears session and cookie |

---

## Planned Features (not yet implemented)

- SL Trafiklab API proxy endpoints (protected)
- Deviation message parsing via Claude API (Haiku model)
- Deviation caching table (SHA-256 hash of message → parsed JSON columns)
- React frontend integration

---

## Local Development

1. Ensure MySQL is running at `192.168.1.204:3306` with database `commuter`
2. Ensure `application-local.properties` exists with correct values (see above)
3. Run with IntelliJ using VM option: `-Dspring.profiles.active=local`
4. OAuth2 redirect URI for local: `http://localhost:8080/login/oauth2/code/google`
5. Frontend dev server expected at `http://localhost:5173`

## Deployment

Push to GitHub main branch → Render auto-detects and builds via Dockerfile →
Liquibase runs migrations on startup → app serves traffic.

Build command is handled entirely by the Dockerfile (multi-stage Maven build).
No separate build command needed in Render config.

## Database development

### Add timestamp columns
All tables should have the following two columns.

```java
@CreationTimestamp
@Column(name = "create_date")
private LocalDateTime createDate;

@UpdateTimestamp
@Column(name = "latest_update")
private LocalDateTime latestUpdate;
```
The only exceptions are tables matching concepts owned by outside librarie, eg `spring_session`.

### Package structure

```
Base pacakge com.tarnvik.publicbackend.commuter
model/
  domain/
    entity/     // Database entites
    repository/ // Repositories
    dao/        // Rarely used, but if used, they live here     
```

### Entity ids
A typical entity id should be a long, generated as such
```java
@TableGenerator(name = "id_generator_<TABLE_NAME>", table = "id_gen", pkColumnName = "gen_name", valueColumnName = "gen_value",
pkColumnValue = "<TABLE_NAME>_gen", initialValue = 10000, allocationSize = 1)
@GeneratedValue(strategy = GenerationType.TABLE, generator = "id_generator_<TABLE_NAME>")
@Column(name = "id")
@Id
private Long id;
```
Tables should normally have a id column.

### Repositories
Prefer JpaRepository over CrudRepository.

## Java style guide

### Lombok
Prefer lombok annotations over explicit accessors and contructors.

### REST Controllers
Shall be concerned with verifiing parameters. Lives in the package `com.tarnvik.publicbackend.commuter.port.incoming.rest`

### Services
Shall be concerned with business logic. Typically called from restcontroller and other services. 
Lives in the package `com.tarnvik.publicbackend.commuter.service` 