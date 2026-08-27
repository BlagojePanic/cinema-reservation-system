# CryptoCinema

CryptoCinema is a cinema reservation and ticketing web application developed as a Bachelor's thesis project. This repository is initialized as a monorepo with a Spring Boot backend and a React + TypeScript + Vite frontend.

## Project Structure

```text
backend/   Spring Boot API
frontend/  React + TypeScript client
```

## Prerequisites

- Java 17 or newer
- Maven
- Node.js and npm
- PostgreSQL

## PostgreSQL

Create a local database and user, or adjust the environment variables below for your existing PostgreSQL setup.

```sql
CREATE DATABASE cryptocinema;
CREATE USER cryptocinema WITH PASSWORD '<your-local-password>';
GRANT ALL PRIVILEGES ON DATABASE cryptocinema TO cryptocinema;
```

Expected backend environment variables:

```text
DB_URL=jdbc:postgresql://localhost:5432/cryptocinema
DB_USERNAME=cryptocinema
DB_PASSWORD=<your-local-password>
JPA_DDL_AUTO=update
JWT_SECRET=<at-least-32-characters>
JWT_EXPIRATION=3600000
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=<your-local-admin-password>
```

Do not commit real passwords or local `.env` files.

## Backend

```bash
cd backend
mvn spring-boot:run
```

The backend runs on `http://localhost:8080` by default.

Health check:

```bash
curl http://localhost:8080/api/health
```

Expected response:

```json
{"status":"UP"}
```

## Authentication

Public endpoints:

```text
POST /api/auth/register
POST /api/auth/login
GET  /api/health
```

Protected test endpoints:

```text
GET /api/user/test   USER or ADMIN
GET /api/admin/test  ADMIN only
```

Register:

```bash
curl -X POST http://localhost:8080/api/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"firstName\":\"Petar\",\"lastName\":\"Petrovic\",\"email\":\"petar@example.com\",\"password\":\"password123\"}"
```

Login:

```bash
curl -X POST http://localhost:8080/api/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"email\":\"petar@example.com\",\"password\":\"password123\"}"
```

Use the returned token for protected endpoints:

```bash
curl http://localhost:8080/api/user/test -H "Authorization: Bearer <token>"
curl http://localhost:8080/api/admin/test -H "Authorization: Bearer <token>"
```

Development admin seeding is enabled only when both `ADMIN_EMAIL` and `ADMIN_PASSWORD` are set. The seed is idempotent: if that email already exists, no new admin is created.

## Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend runs on `http://localhost:5173`. During local development, Vite proxies `/api` requests to the backend at `http://localhost:8080`.

Open these local routes to test auth manually:

```text
http://localhost:5173/register
http://localhost:5173/login
```

## Build

Backend:

```bash
cd backend
mvn package
```

Frontend:

```bash
cd frontend
npm run build
```
