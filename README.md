# CAMPUSPARK — Complete Version

CampusPark is a Java + PostgreSQL + HTML/CSS/JavaScript campus parking system.

## Features
# CAMPUSPARK

Smart Campus Parking Management System using Java 21, PostgreSQL 18, and a plain HTML/CSS/JavaScript frontend.

## REQUIREMENTS

- Java 21 or newer
- PostgreSQL 18

`psql` must be available on PATH. It is normally in `C:\Program Files\PostgreSQL\18\bin`.

## SETUP

1. Create the PostgreSQL `campuspark` database/user if required. The starter creates the database when the `postgres` user can create databases.
2. Make sure the PostgreSQL service is running.
3. Double-click `START_CAMPUSPARK.bat`.
4. Enter the PostgreSQL password for `postgres` when requested.
5. The browser opens automatically at `http://localhost:8080/`.

The starter downloads the JDBC driver into `database/LIB/` if it is missing, initializes missing tables and sample slots without dropping data, compiles the backend, and starts the server.

## MANUAL RUN

From the project root in PowerShell:

```powershell
$env:CAMPUSPARK_DB_PASSWORD = "your-postgres-password"
New-Item -ItemType Directory -Force build/classes | Out-Null
javac -encoding UTF-8 -d build/classes -cp database/LIB/postgresql-jdbc.jar backend/database/DBConnection.java backend/ParkingServer.java
psql -h localhost -U postgres -d campuspark -v ON_ERROR_STOP=1 -f database/SCHEMA.SQL
java -cp "build/classes;database/LIB/postgresql-jdbc.jar" ParkingServer
```

Test the API in another PowerShell window:

```powershell
Invoke-WebRequest http://localhost:8080/api/test
```

The Java server also serves the frontend at `http://localhost:8080/`.

## ADMIN DEMO ACCOUNT

- Email: `admin@campuspark.com`
- Student ID: `ADMIN001`
- Password: `admin123`
CREATE DATABASE campuspark;
