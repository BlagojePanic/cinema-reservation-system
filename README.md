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
JPA_DDL_AUTO=validate
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

## Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend runs on `http://localhost:5173`. During local development, Vite proxies `/api` requests to the backend at `http://localhost:8080`.

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
