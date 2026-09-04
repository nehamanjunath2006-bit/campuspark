@echo off
setlocal EnableExtensions
set "ROOT=%~dp0"
set "ROOT=%ROOT:~0,-1%"
set "DRIVER=%ROOT%\database\LIB\postgresql-jdbc.jar"
set "BUILD=%ROOT%\build\classes"
set "SCHEMA=%ROOT%\database\SCHEMA.SQL"
if not exist "%DRIVER%" for %%F in ("%ROOT%\database\LIB\postgresql-*.jar") do if exist "%%~fF" set "DRIVER=%%~fF"

where java >nul 2>&1 || (echo Java 21 or newer is required. & pause & exit /b 1)
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_VERSION=%%~V"
for /f "tokens=1 delims=." %%V in ("%JAVA_VERSION%") do set "JAVA_MAJOR=%%V"
if not defined JAVA_MAJOR set "JAVA_MAJOR=0"
if %JAVA_MAJOR% LSS 21 (echo Java 21 or newer is required. Found %JAVA_VERSION%. & pause & exit /b 1)

set "PSQL="
for /f "delims=" %%P in ('where psql 2^>nul') do if not defined PSQL set "PSQL=%%P"
if not defined PSQL if exist "%ProgramFiles%\PostgreSQL\18\bin\psql.exe" set "PSQL=%ProgramFiles%\PostgreSQL\18\bin\psql.exe"
if not defined PSQL if exist "%ProgramFiles%\PostgreSQL\17\bin\psql.exe" set "PSQL=%ProgramFiles%\PostgreSQL\17\bin\psql.exe"
if not defined PSQL (echo PostgreSQL psql was not found. Install PostgreSQL or add its bin folder to PATH. & pause & exit /b 1)
if not exist "%DRIVER%" (
    echo Downloading PostgreSQL JDBC driver...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; New-Item -ItemType Directory -Force -Path '%ROOT%\database\LIB' | Out-Null; Invoke-WebRequest -Uri 'https://jdbc.postgresql.org/download/postgresql-42.7.8.jar' -OutFile '%DRIVER%'"
    if errorlevel 1 (echo Could not download the PostgreSQL JDBC driver. & pause & exit /b 1)
)

if not defined CAMPUSPARK_DB_PASSWORD set /p "CAMPUSPARK_DB_PASSWORD=Enter PostgreSQL password for postgres: "
if not defined CAMPUSPARK_DB_PASSWORD (echo A PostgreSQL password is required. & pause & exit /b 1)
set "PGPASSWORD=%CAMPUSPARK_DB_PASSWORD%"
"%PSQL%" -h localhost -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='campuspark'" | findstr /r "1" >nul
if errorlevel 1 (
    echo Creating database campuspark...
    "%PSQL%" -h localhost -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE campuspark;"
    if errorlevel 1 (echo Could not create campuspark. Check PostgreSQL and credentials. & pause & exit /b 1)
)
"%PSQL%" -h localhost -U postgres -d campuspark -v ON_ERROR_STOP=1 -f "%SCHEMA%"
if errorlevel 1 (echo Database initialization failed. & pause & exit /b 1)

if exist "%BUILD%" rmdir /s /q "%BUILD%"
mkdir "%BUILD%"
javac -encoding UTF-8 -d "%BUILD%" -cp "%DRIVER%" "%ROOT%\backend\database\DBConnection.java" "%ROOT%\backend\ParkingServer.java"
if errorlevel 1 (echo Backend compilation failed. & pause & exit /b 1)

cd /d "%ROOT%"
start "CampusPark Server" cmd /k "cd /d ^"%ROOT%^" && java -cp ^"build\classes;%DRIVER%^" ParkingServer"
start "" "http://localhost:8080/"
endlocal
