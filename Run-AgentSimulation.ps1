param(
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$Maven,
    [string[]]$JavaArgs = @()
)

$ErrorActionPreference = 'Stop'

function Test-Jdk17([string]$Path) {
    if (!$Path -or !(Test-Path (Join-Path $Path 'bin\javac.exe'))) { return $false }
    $release = Join-Path $Path 'release'
    return (Test-Path $release) -and ((Get-Content $release -Raw) -match '(?m)^JAVA_VERSION="17(?:[."]|$)')
}

if (!$PSBoundParameters.ContainsKey('JdkHome') -and !(Test-Jdk17 $JdkHome)) {
    $JdkHome = $null
}
if (!$JdkHome) {
    $JdkHome = Get-ChildItem (Join-Path $env:USERPROFILE '.jdks') -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Jdk17 $_.FullName } |
        Sort-Object Name -Descending | Select-Object -First 1 -ExpandProperty FullName
}
if (!(Test-Jdk17 $JdkHome)) {
    throw 'Set JAVA_HOME to JDK 17 or pass -JdkHome.'
}
if (!$Maven) {
    $Maven = (Get-Command mvn.cmd -ErrorAction SilentlyContinue).Source
}
if (!$Maven) {
    $Maven = (Get-ChildItem (Join-Path $env:ProgramFiles 'JetBrains') -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | ForEach-Object {
            Join-Path $_.FullName 'plugins\maven-plugin\lib\maven3\bin\mvn.cmd'
        } | Where-Object { Test-Path $_ } | Select-Object -First 1)
}
if (!$Maven) {
    throw 'Maven is unavailable. Install Maven or pass -Maven.'
}

$previousJavaHome = $env:JAVA_HOME
Push-Location $PSScriptRoot
try {
    $env:JAVA_HOME = $JdkHome
    & $Maven -B clean package
    if ($LASTEXITCODE -ne 0) { throw 'CMS agent simulation build failed.' }

    & (Join-Path $JdkHome 'bin\java.exe') -jar 'target/afs-cms.jar' '--spring.profiles.active=agent-sim' @JavaArgs
    if ($LASTEXITCODE -ne 0) { throw 'CMS agent simulation stopped with an error.' }
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJavaHome
}

