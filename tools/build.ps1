# Build helper: keeps the Maven distribution, the dependency repository and the JDK lookup
# off the system drive. Everything this project writes lives on D:.
#
#   .\tools\build.ps1 compile
#   .\tools\build.ps1 -DskipTests package
#   .\tools\build.ps1 test
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)

if (-not $MavenArgs -or $MavenArgs.Count -eq 0) {
    $MavenArgs = @('compile')
}

$ErrorActionPreference = 'Stop'

# 1) Maven home (installed on D:)
$mavenHome = 'D:\tools\apache-maven-3.9.9'
$mvn = Join-Path $mavenHome 'bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $mvn)) {
    throw "Maven not found at $mvn. Reinstall it under D:\tools."
}

# 2) JDK 17: JAVA_HOME first, then %USERPROFILE%\.jdks
function Test-Jdk17([string]$path) {
    if (-not $path) { return $false }
    $release = Join-Path $path 'release'
    return (Test-Path -LiteralPath (Join-Path $path 'bin\java.exe')) -and
        (Test-Path -LiteralPath $release) -and
        ((Get-Content -LiteralPath $release -Raw) -match '(?m)^JAVA_VERSION="17[."]')
}

$jdk = $env:JAVA_HOME
if (-not (Test-Jdk17 $jdk)) {
    $jdk = Get-ChildItem (Join-Path $env:USERPROFILE '.jdks') -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Jdk17 $_.FullName } |
        Sort-Object Name -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $jdk) { throw 'JDK 17 not found. Set JAVA_HOME.' }

$env:JAVA_HOME = $jdk
if (-not $env:MAVEN_OPTS) { $env:MAVEN_OPTS = '-Xmx768m' }

# 3) Dependency repository on D: (a junction may also exist at %USERPROFILE%\.m2\repository)
$repo = 'D:\m2repo'
if (-not (Test-Path -LiteralPath $repo)) { New-Item -ItemType Directory -Path $repo | Out-Null }

Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    & $mvn "-Dmaven.repo.local=$repo" @MavenArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
