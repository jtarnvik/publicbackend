# publicbackend

Backend service for a personal Stockholm commuter dashboard. Proxies [SL Trafiklab](https://www.trafiklab.se/) APIs and serves real-time transit data to a React frontend.

## Live

- **Backend:** https://tarnvik.onrender.com
- **Frontend:** https://jtarnvik.github.io/sl-dashboard/

## Tech Stack

- **Java 21** / **Spring Boot 4.0.3**
- **Spring Security** with Google OAuth2 login
- **Spring Session JDBC** for persistent sessions
- **Liquibase** for database schema management
- **PostgreSQL** (Supabase) in production
- **MySQL 8** for local development
- Deployed on **Render.com** via Docker

## Features

- Google OAuth2 authentication with email whitelist
- Persistent sessions stored in PostgreSQL (survive redeployments)
- SL Trafiklab API proxy (in progress)
- Deviation message parsing via Claude API (planned)

## Prerequisites

- Java 21+
- Maven 3.9+
- MySQL 8.x running locally
- Google Cloud Console project with OAuth2 credentials

## Local Development

**1. Clone the repository**
```bash
git clone https://github.com/jtarnvik/publicbackend.git
cd publicbackend
```

**2. Create local properties file**

Create `src/main/resources/application-local.properties` (this file is gitignored):

```properties
spring.security.oauth2.client.registration.google.client-id=<your-client-id>
spring.security.oauth2.client.registration.google.client-secret=<your-client-secret>

spring.datasource.url=jdbc:mysql://localhost:3306/commuter
spring.datasource.username=<your-mysql-username>
spring.datasource.password=<your-mysql-password>
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

app.allowed-emails=<comma-separated-email-whitelist>
app.frontend-url=http://localhost:5173
```

**3. Create local MySQL database**
```sql
CREATE DATABASE commuter CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

**4. Run the application**

In IntelliJ, add the following VM option to your run configuration:
```
-Dspring.profiles.active=local
```

Or from the command line:
```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=local"
```

The app starts at `http://localhost:8080`.

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/ping` | Public | Health check |
| GET | `/api/auth/me` | Optional | Current user info or 401 |
| POST | `/api/auth/logout` | Optional | Invalidate session |

## Authentication

Login is handled via Google OAuth2. After authenticating with Google, the user's email is checked against a whitelist. Only whitelisted emails are granted access.

The frontend uses a silent auth check pattern:
1. Call `GET /api/auth/me` on page load
2. If `200` → user is logged in, show dashboard
3. If `401` → show login button
4. Login button navigates to `/oauth2/authorization/google`
5. Logout calls `POST /api/auth/logout`

## Database

Schema is managed by Liquibase. Changelogs are in `src/main/resources/db/changelog/`.

On startup, Liquibase automatically applies any pending changesets. Never modify existing changesets — always add new ones.

## Deployment

The app is deployed on Render.com and builds automatically on every push to `main` via the included `Dockerfile`.

### Required environment variables on Render

| Variable | Description |
|----------|-------------|
| `GOOGLE_CLIENT_ID` | Google OAuth2 client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 client secret |
| `ALLOWED_EMAILS` | Comma-separated email whitelist |
| `DB_URL` | PostgreSQL JDBC URL (Supabase pooler) |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `FRONTEND_URL` | Allowed CORS origin (GitHub Pages URL) |

## Project Structure

```
src/
├── main/
│   ├── java/com/tarnvik/publicbackend/
│   │   ├── config/
│   │   │   └── SecurityConfig.java
│   │   ├── controller/
│   │   │   ├── AuthController.java
│   │   │   └── HealthController.java
│   │   └── PublicbackendApplication.java
│   └── resources/
│       ├── db/changelog/
│       │   ├── db.changelog-master.xml
│       │   └── changes/
│       ├── application.properties
│       └── application-local.properties  ← gitignored
Dockerfile
pom.xml
CLAUDE.md
```

## License

Private project — not licensed for public use.
