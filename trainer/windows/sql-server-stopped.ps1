# Trainer scenario W2: stop the SQL Server Express service, then restore it.
# Run on WINWEB01 (PowerShell Admin). Diagnose between -Break and -Fix.
# Runbook: docs/runbooks/win-02-sql-server-stopped.md
param(
    [switch]$Break,
    [switch]$Fix,
    [string]$Service = 'MSSQL$SQLEXPRESS'
)
if ($Break -eq $Fix) { throw 'Use exactly one of -Break or -Fix.' }
if ($Break) { Stop-Service $Service -Force; 'Service stopped. Queries should now fail with a connection error.' }
if ($Fix)   { Start-Service $Service; 'Service started. Wait about 10 seconds before the first query.' }
Get-Service $Service | Format-Table Status, Name, DisplayName -AutoSize
