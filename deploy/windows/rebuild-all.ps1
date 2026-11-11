# WINWEB01 - rebuild the whole Windows leg in one go on a fresh Windows Server.
# Run in PowerShell (Admin), from this folder. See REBUILD.md for the full steps.
# Steps: (optional) fixed IP -> IIS + URL Rewrite + ARR -> proxy rule -> SQL Server Express -> load data.
# NOT yet tested end to end on a clean server (the SQL Express install step in particular).
param(
    [Parameter(Mandatory)][string]$Upstream,      # NGINX address, e.g. http://192.168.49.172:8081
    [string]$DataDir   = 'C:\setup\data',         # users.csv, documents.csv, document_access.csv go here
    [switch]$SetStaticIp,                          # only on a LAN where you control the address
    [switch]$SkipSql                               # IIS only, no SQL Server
)
$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
New-Item -ItemType Directory -Force C:\setup, $DataDir | Out-Null
function Step($n, $text) { Write-Host "`n=== $n. $text ===" -ForegroundColor Cyan }

if ($SetStaticIp) { Step 1 'Fixed IP address';       & "$here\01-network.ps1" }
Step 2 'IIS, URL Rewrite, ARR';                       & "$here\02-install-iis.ps1"
Step 3 'Reverse proxy rule to NGINX';                 & "$here\03-iis-proxy.ps1" -Upstream $Upstream

if (-not $SkipSql) {
    Step 4 'SQL Server Express 2019'
    if (-not (Get-Service 'MSSQL$SQLEXPRESS' -ErrorAction SilentlyContinue)) {
        $exe = 'C:\setup\SQL2019-SSEI-Expr.exe'
        Invoke-WebRequest 'https://go.microsoft.com/fwlink/?linkid=866658' -OutFile $exe
        & $exe /QUIET /ACTION=Install /IACCEPTSQLSERVERLICENSETERMS /INSTANCENAME=SQLEXPRESS `
              /SQLSYSADMINACCOUNTS="BUILTIN\Administrators" /ENU
        if ($LASTEXITCODE -ne 0) { throw "SQL Express setup failed (exit $LASTEXITCODE). Install it by hand, then re-run." }
    } else { Write-Host 'Already installed.' }

    Step 5 'Load the data into SQL Server'
    Copy-Item "$here\..\..\sql\mssql\01_schema.sql" C:\setup\01_schema.sql -Force
    $missing = 'users','documents','document_access' | Where-Object { -not (Test-Path "$DataDir\$_.csv") }
    if ($missing) {
        Write-Warning "Missing CSV file(s) in ${DataDir}: $($missing -join ', ')."
        Write-Warning 'Make them on the Linux side with export-from-postgres.sh, copy them here, then run 04-load-sqlserver.ps1.'
    } else { & "$here\04-load-sqlserver.ps1" -Dir $DataDir }
}

Step 6 'Check'
try { $r = Invoke-WebRequest "http://localhost/docvault/login" -UseBasicParsing
      Write-Host "IIS -> NGINX -> Tomcat: HTTP $($r.StatusCode), request id $($r.Headers['X-Request-ID'])" -ForegroundColor Green }
catch { Write-Warning "Proxy test failed: $($_.Exception.Message). Is the Linux side up on $Upstream ?" }
