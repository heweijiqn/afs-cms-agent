param([string]$JdkHome = $env:JAVA_HOME, [switch]$SeedDemo, [switch]$Background)

$ErrorActionPreference = 'Stop'
function Test-Java17([string]$Path) {
    if (-not $Path) { return $false }
    $release = Join-Path $Path 'release'
    return (Test-Path -LiteralPath (Join-Path $Path 'bin/java.exe')) -and
        (Test-Path -LiteralPath $release) -and ((Get-Content -LiteralPath $release -Raw) -match '(?m)^JAVA_VERSION="17[."]')
}

if (-not $PSBoundParameters.ContainsKey('JdkHome') -and -not (Test-Java17 $JdkHome)) {
    $JdkHome = Get-ChildItem (Join-Path $env:USERPROFILE '.jdks') -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Java17 $_.FullName } | Sort-Object Name -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not (Test-Java17 $JdkHome)) { throw 'Set JAVA_HOME or pass -JdkHome with JDK 17.' }

$jar = Join-Path $PSScriptRoot 'target/afs-cms.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Package afs-cms with Maven before starting the service.' }

$launchArgs = @('-Xmx1200m', '-jar', $jar, '--spring.profiles.active=local-cms')
if ($SeedDemo) { $launchArgs += '--afs.demo.seed=true' }

Push-Location $PSScriptRoot
try {
    Write-Host 'Console: http://127.0.0.1:9901/console'
    Write-Host "Local admin key: $(Join-Path $PSScriptRoot 'data/local-admin.key')"

    if ($Background) {
        # javaw.exe has no console subsystem, so nothing pops up; -WindowStyle Hidden covers the
        # case where a launcher still tries to attach a window. Logs go to ./log instead.
        $java = Join-Path $JdkHome 'bin/javaw.exe'
        $logDir = Join-Path $PSScriptRoot 'log'
        New-Item -ItemType Directory -Force -Path $logDir | Out-Null
        $running = Get-NetTCPConnection -LocalPort 9901 -State Listen -ErrorAction SilentlyContinue
        if ($running) {
            Write-Host "Service already listening on 9901 (pid $($running.OwningProcess)); nothing to do."
            return
        }
        $process = Start-Process -FilePath $java -ArgumentList $launchArgs -WorkingDirectory $PSScriptRoot `
            -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $logDir 'service.out.log') `
            -RedirectStandardError (Join-Path $logDir 'service.err.log')
        Set-Content -LiteralPath (Join-Path $logDir 'service.pid') -Value $process.Id -Encoding ascii
        Write-Host "Started in background (no window). PID $($process.Id), logs in ./log, stop with: Stop-Process -Id $($process.Id)"
        return
    }

    & (Join-Path $JdkHome 'bin/java.exe') @launchArgs
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
    Pop-Location
}
