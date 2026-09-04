$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Driver = Join-Path $Root 'database\LIB\postgresql-jdbc.jar'
$Build = Join-Path $Root 'build\classes'
$Schema = Join-Path $Root 'database\SCHEMA.SQL'
if (-not (Test-Path $Driver)) {
    $existingDriver = Get-ChildItem (Join-Path $Root 'database\LIB\postgresql-*.jar') -File -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($existingDriver) { $Driver = $existingDriver.FullName }
}

$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) { throw 'Java 21 or newer is required.' }
$versionText = (& java -version 2>&1 | Select-Object -First 1)
if ($versionText -match '"(\d+)') { $major = [int]$Matches[1] } else { throw 'Could not determine the Java version.' }
if ($major -lt 21) { throw "Java 21 or newer is required. Found Java $major." }

$psql = (Get-Command psql -ErrorAction SilentlyContinue).Source
if (-not $psql) {
    foreach ($candidate in @("$env:ProgramFiles\PostgreSQL\18\bin\psql.exe", "$env:ProgramFiles\PostgreSQL\17\bin\psql.exe")) {
        if (Test-Path $candidate) { $psql = $candidate; break }
    }
}
if (-not $psql) { throw 'PostgreSQL psql was not found. Install PostgreSQL or add its bin folder to PATH.' }
if (-not (Test-Path $Driver)) {
    Write-Host 'Downloading PostgreSQL JDBC driver...'
    New-Item -ItemType Directory -Force (Split-Path $Driver) | Out-Null
    Invoke-WebRequest 'https://jdbc.postgresql.org/download/postgresql-42.7.8.jar' -OutFile $Driver
}

if ([string]::IsNullOrWhiteSpace($env:CAMPUSPARK_DB_PASSWORD)) {
    $secure = Read-Host 'Enter PostgreSQL password for postgres' -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $env:CAMPUSPARK_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
$env:PGPASSWORD = $env:CAMPUSPARK_DB_PASSWORD
$exists = (& $psql -h localhost -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='campuspark'").Trim()
if ($exists -ne '1') {
    Write-Host 'Creating database campuspark...'
    & $psql -h localhost -U postgres -d postgres -v ON_ERROR_STOP=1 -c 'CREATE DATABASE campuspark;'
}
& $psql -h localhost -U postgres -d campuspark -v ON_ERROR_STOP=1 -f $Schema

if (Test-Path $Build) { Remove-Item $Build -Recurse -Force }
New-Item -ItemType Directory -Force $Build | Out-Null
& javac -encoding UTF-8 -d $Build -cp $Driver (Join-Path $Root 'backend\database\DBConnection.java') (Join-Path $Root 'backend\ParkingServer.java')
if ($LASTEXITCODE -ne 0) { throw 'Backend compilation failed.' }

$serverCommand = "Set-Location '$Root'; java -cp 'build\classes;$Driver' ParkingServer"
Start-Process powershell -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $serverCommand
Start-Process 'http://localhost:8080/'
Write-Host 'CampusPark is running at http://localhost:8080/'
