# Chronos Platform — Setup Guide

Complete instructions to run the full stack locally:
PostgreSQL → Keycloak → Python Cleaner → Spring Boot → React

---

## Prerequisites

Install these before starting:

| Tool | Version | Download |
|------|---------|----------|
| Java JDK | 21 | https://adoptium.net |
| Maven | 3.9+ | https://maven.apache.org |
| Node.js | 18+ | https://nodejs.org |
| Python | 3.11+ | https://python.org |
| Docker Desktop | latest | https://docker.com |

Verify each one:
```bash
java  --version     # should say 21.x.x
mvn   --version     # should say 3.9.x
node  --version     # should say 18.x.x or higher
python3 --version   # should say 3.11.x or higher
docker --version    # should say 24.x.x or higher
```

---

## Project Folder Structure

Organise all delivered files like this:

```
chronos/
│
├── docker-compose.yml              # starts PostgreSQL + Keycloak
│
├── db/
│   ├── chronos_schema.sql          # v1 schema (run first)
│   └── chronos_schema_v2_patch.sql # v2 patch (run second)
│
├── chronos-python-cleaner/         # data cleaning service
│   ├── main.py
│   ├── config.py
│   ├── requirements.txt
│   ├── .env.example
│   ├── clean/
│   │   ├── __init__.py
│   │   ├── date_utils.py
│   │   ├── validators.py
│   │   ├── employee_time_cleaner.py
│   │   └── org_assignment_cleaner.py
│   ├── load/
│   │   ├── __init__.py
│   │   └── db_loader.py
│   └── models/
│       ├── __init__.py
│       └── clean_records.py
│
├── chronos-backend/                # Spring Boot API
│   ├── pom.xml
│   └── src/main/java/com/chronos/
│       ├── ChronosApplication.java
│       ├── config/
│       │   └── KeycloakSecurityConfig.java
│       ├── controller/
│       │   ├── ReportController.java
│       │   └── DashboardController.java
│       ├── dto/
│       │   ├── internal/
│       │   └── response/
│       ├── entity/          (all 22 entity files)
│       ├── enums/           (ProjectStatus, TimesheetStatus, BillingMode)
│       ├── exception/
│       │   ├── ChronosException.java
│       │   └── GlobalExceptionHandler.java
│       ├── repository/      (all repository interfaces)
│       └── service/
│           ├── ReportGenerationService.java
│           ├── EmployeeTimeQueryService.java
│           ├── EmployeeCapacityService.java
│           └── CsvExportService.java
│   └── src/main/resources/
│       └── application.yml
│
└── chronos-frontend/               # React UI
    ├── package.json
    ├── vite.config.js
    └── src/
        ├── main.jsx
        └── ChronosApp.jsx
```

---

## Step 1 — Start Infrastructure (PostgreSQL + Keycloak)

From the root `chronos/` folder:

```bash
docker compose up -d
```

Wait about 30 seconds for both containers to be healthy, then verify:

```bash
docker compose ps
# Both should show "healthy" or "running"
```

PostgreSQL is now listening on **localhost:5432**
Keycloak is now listening on **localhost:8080**

---

## Step 2 — Create the Database Schema

Connect to PostgreSQL and run the two SQL files in order:

```bash
# Connect to the chronos database
psql -h localhost -U postgres -d chronos

# Inside psql, run the schema files
\i /path/to/db/chronos_schema.sql
\i /path/to/db/chronos_schema_v2_patch.sql

# Verify tables were created
\dt

# Should list: activity, activity_nature, accounting_code,
# billing_entity, client, company, company_member,
# country_calendar, employee, employee_by_activity_nature,
# employee_by_product, employee_time, excluded_organizational_unit,
# iteration, lot, month_period, organizational_assignment,
# organizational_unit, organizational_unit_member, phase, product, project

\q
```

If you prefer a GUI, use **DBeaver** or **pgAdmin** — connect with:
- Host: localhost
- Port: 5432
- Database: chronos
- User: postgres
- Password: postgres

---

## Step 3 — Configure Keycloak

Open http://localhost:8080 in your browser.

### 3.1 Log in to the admin console

- Click **Administration Console**
- Username: `admin`
- Password: `admin`

### 3.2 Create the Chronos realm

1. Click the dropdown in the top-left (shows "Keycloak" by default)
2. Click **Create Realm**
3. Realm name: `chronos`
4. Click **Create**

### 3.3 Create the backend client (resource server)

1. Go to **Clients** → **Create client**
2. Client type: `OpenID Connect`
3. Client ID: `chronos-backend`
4. Click **Next**
5. **Client authentication**: ON
6. **Authorization**: OFF
7. Click **Next** then **Save**
8. On the **Credentials** tab → copy the **Client Secret** (you'll need it later)

### 3.4 Create the frontend client

1. Go to **Clients** → **Create client**
2. Client type: `OpenID Connect`
3. Client ID: `chronos-frontend`
4. Click **Next**
5. **Client authentication**: OFF  ← public client, no secret
6. Click **Next**
7. Valid redirect URIs: `http://localhost:3000/*`
8. Web origins: `http://localhost:3000`
9. Click **Save**

### 3.5 Create roles

1. Go to **Realm roles** → **Create role**
2. Create three roles one by one:
   - `DATA_ADMIN`
   - `FINANCIAL_OFFICER`
   - `PRODUCT_MANAGER`

### 3.6 Create a test user

1. Go to **Users** → **Add user**
2. Username: `fatma.admin`
3. Email: `fatma@chronos.local`
4. Click **Create**
5. On the **Credentials** tab:
   - Set password: `chronos123`
   - Temporary: OFF
   - Click **Save password**
6. On the **Role mapping** tab:
   - Click **Assign role**
   - Select `DATA_ADMIN`
   - Click **Assign**

### 3.7 Get the issuer URI

Note down this URL — you'll need it for Spring Boot:
```
http://localhost:8080/realms/chronos
```

---

## Step 4 — Run the Python Cleaning Service

This loads your CSV data into PostgreSQL.

```bash
cd chronos-python-cleaner

# Create and activate virtual environment
python3 -m venv venv
source venv/bin/activate          # Mac/Linux
# OR: venv\Scripts\activate       # Windows

# Install dependencies
pip install -r requirements.txt

# Set up environment
cp .env.example .env
```

Edit `.env` with your database URL:
```env
CHRONOS_DB_URL=postgresql+psycopg2://postgres:postgres@localhost:5432/chronos
BATCH_SIZE=500
LOG_LEVEL=INFO
ANOMALIES_OUTPUT_DIR=./output
```

Run a dry-run first to check for data issues:
```bash
python main.py \
  --employee-time  /path/to/Book1.xlsx \
  --org-assignment /path/to/Organizational_Assignment.xlsx \
  --dry-run
```

Review the anomalies CSV in `./output/`. When satisfied:
```bash
python main.py \
  --employee-time  /path/to/Book1.xlsx \
  --org-assignment /path/to/Organizational_Assignment.xlsx
```

Expected output:
```
08:42:11  INFO     chronos.main — Chronos cleaning service starting
08:42:11  INFO     chronos.main —   employee_time    : Book1.xlsx
08:42:11  INFO     chronos.main —   org_assignment   : Organizational_Assignment.xlsx
08:42:13  INFO     chronos.main — Found 14 rows → employee_times=14, employees=8
...
08:42:15  INFO     chronos.main — LOAD SUMMARY
08:42:15  INFO     chronos.main —   employee            14 rows inserted
08:42:15  INFO     chronos.main —   company_member      14 rows inserted
...
08:42:15  INFO     chronos.main — Pipeline completed successfully.
```

---

## Step 5 — Configure and Run Spring Boot

### 5.1 Place source files

Copy all Java files from `chronos-backend/` into:
```
src/main/java/com/chronos/
```

Make sure `ChronosApplication.java` is at the root of the package — not inside any subfolder.

### 5.2 Edit application.yml

File is at `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/chronos
    username: postgres
    password: postgres

  jpa:
    hibernate:
      ddl-auto: validate       # schema is managed by SQL files, not Hibernate
    show-sql: false

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/chronos

server:
  port: 8081
```

### 5.3 Run the backend

```bash
cd chronos-backend
mvn clean install -DskipTests    # first run downloads all dependencies (~2 min)
mvn spring-boot:run
```

You should see:
```
  .   ____          _
 /\\ / ___'_ __ _ _(_)_ __  __ _
( ( )\___ | '_ | '_| | '_ \/ _` |
 \\/  ___)| |_)| | | | | || (_| |
  '  |____| .__|_| |_|_| |_\__, |
 =========|_|==============|___/
 :: Spring Boot ::          (v3.2.3)

...Started ChronosApplication in 4.2 seconds
```

Test the health endpoint:
```bash
curl http://localhost:8081/actuator/health
# {"status":"UP"}
```

Test the API (should return 401 without a token — that's correct):
```bash
curl http://localhost:8081/api/dashboard/summary
# {"type":"...","status":401,...}
```

Swagger UI is available at:
```
http://localhost:8081/swagger-ui.html
```

---

## Step 6 — Run the React Frontend

```bash
cd chronos-frontend

# Install dependencies
npm install

# Place these files in src/
# - main.jsx
# - ChronosApp.jsx

# Start dev server
npm run dev
```

Open http://localhost:3000

You should see the Chronos login screen.

---

## Step 7 — Verify the Full Flow

Work through this checklist:

```
[ ] PostgreSQL running   — docker compose ps shows healthy
[ ] Keycloak running     — http://localhost:8080 loads admin console
[ ] Schema loaded        — psql \dt shows 22 tables
[ ] Test user created    — fatma.admin / chronos123 exists with DATA_ADMIN role
[ ] Python cleaner ran   — no ERRORs in output, data visible in psql
[ ] Spring Boot running  — http://localhost:8081/actuator/health returns UP
[ ] React running        — http://localhost:3000 shows login screen
[ ] Login works          — "Sign in with Keycloak" redirects and returns to dashboard
[ ] Period selection     — month grid shows, can select July 2024
[ ] Dashboard loads      — stat cards show real data from the database
[ ] Generate report      — button triggers POST /api/reports/generate
[ ] CSV downloads        — both Report CSV and Anomalies CSV download
```

---

## Startup Order (every time)

Always start in this order:

```bash
# 1. Infrastructure
docker compose up -d

# 2. Backend (new terminal)
cd chronos-backend && mvn spring-boot:run

# 3. Frontend (new terminal)
cd chronos-frontend && npm run dev
```

The Python cleaner only runs when you have new CSV data to import —
not on every startup.

---

## Port Summary

| Service | Port | URL |
|---------|------|-----|
| PostgreSQL | 5432 | jdbc:postgresql://localhost:5432/chronos |
| Keycloak | 8080 | http://localhost:8080 |
| Spring Boot | 8081 | http://localhost:8081 |
| React (Vite) | 3000 | http://localhost:3000 |
| Swagger UI | 8081 | http://localhost:8081/swagger-ui.html |

---

## Common Issues

**Spring Boot fails to start with "relation does not exist"**
→ The JPA `ddl-auto` is set to `validate`. This means Hibernate checks the schema
but does not create it. Make sure you ran both SQL scripts in Step 2.
Run `\dt` in psql to confirm all 22 tables exist.

**Spring Boot fails with "issuer does not match"**
→ The issuer URI in `application.yml` must exactly match what Keycloak reports.
Check: `curl http://localhost:8080/realms/chronos/.well-known/openid-configuration | python3 -m json.tool | grep issuer`
Copy the exact value into `application.yml`.

**Python cleaner fails with "connection refused"**
→ PostgreSQL container is not running or not yet healthy.
Run `docker compose ps` and wait for the `healthy` status.

**React shows blank screen**
→ Check browser console for errors. Most common cause:
the `src/main.jsx` file is missing or `ChronosApp.jsx` is not in `src/`.

**Keycloak 401 on API calls from React**
→ This is expected until you wire Keycloak login into the React app.
The current frontend simulates login with a button. API integration
(adding Authorization headers) is the next development step.

**Port already in use**
→ Check what is using the port: `lsof -i :8081` (Mac/Linux)
or `netstat -ano | findstr :8081` (Windows).

---

## What Has Been Built

| Layer | Files delivered | Status |
|-------|----------------|--------|
| PostgreSQL schema | chronos_schema.sql + v2 patch | ✅ Ready to run |
| JPA entities (22) | chronos-entities.zip | ✅ Ready |
| Python cleaner | chronos-python-cleaner.zip | ✅ Ready |
| Spring Boot API | chronos-springboot.zip | ✅ Ready |
| React frontend | ChronosApp.jsx | ✅ Ready |
| Keycloak config | This guide §3 | ✅ Manual steps |
| API integration | Keycloak token in React | ⬜ Next |
| LLM assistant | Pending platform stable | ⬜ Later |
