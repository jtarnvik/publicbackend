# publicbackend

Backend service for a personal Stockholm commuter dashboard. It handles user authentication,
user settings persistence, and AI-powered interpretation of public transit deviation messages.
Access is invite-only — a small group of friends use it alongside the developer.

## Live

- **Backend:** https://tarnvik.onrender.com
- **Frontend:** https://jtarnvik.github.io/sl-dashboard/

## What it does

- **Authentication** — Google OAuth2 login with an email whitelist. Sessions are stored in the
  database so users stay logged in across backend redeployments.
- **User settings** — Stores each user's selected transit stop so the dashboard remembers it
  across devices and sessions.
- **Deviation interpretation** — Sends SL transit deviation texts to the Claude API to classify
  their importance and relevance. Results are cached in the database to avoid redundant API calls.
- **Shared routes** — Lets users save and share a planned journey via a short link.
- **Admin tools** — Approve or reject access requests, manage allowed users, and view usage statistics.

## Tech Stack

- **Java 21** / **Spring Boot 4**
- **Spring Security** with Google OAuth2
- **Spring Session JDBC** for persistent sessions
- **Liquibase** for database schema management
- **PostgreSQL** (Supabase) in production, **MySQL 8** locally
- **Claude AI** (Anthropic) for deviation text interpretation
- Deployed on **Render.com** via Docker

## Local Development

**1. Create local properties file**

Create `src/main/resources/application-local.properties` (gitignored):

```properties
spring.security.oauth2.client.registration.google.client-id=<your-client-id>
spring.security.oauth2.client.registration.google.client-secret=<your-client-secret>

spring.datasource.url=jdbc:mysql://localhost:3306/commuter
spring.datasource.username=<your-username>
spring.datasource.password=<your-password>
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

app.allowed-emails=<comma-separated-email-whitelist>
app.frontend-url=http://localhost:5173
anthropic.api-key=<your-key>
```

**2. Run with the local profile**

In IntelliJ, add `-Dspring.profiles.active=local` as a VM option, or from the command line:

```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=local"
```

The app starts at `http://localhost:8080`. Liquibase runs all schema migrations automatically on startup.

## License

Private project — not licensed for public use.
