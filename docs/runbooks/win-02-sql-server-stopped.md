# Runbook W2: queries on the Windows SQL Server copy fail, but the DocVault web app works

**Layer:** SQL Server Express on WINWEB01, instance `SQLEXPRESS`. **Seen in the lab:** Nov 3, 2026.

## Symptom
Any query against the Windows copy of `docvault_db` fails with a connection error:

```
Sqlcmd: Error: Microsoft ODBC Driver 17 for SQL Server : SQL Server Network Interfaces: Error Locating Server/Instance Specified [xFFFFFFFF].
Sqlcmd: Error: Microsoft ODBC Driver 17 for SQL Server : Login timeout expired.
Sqlcmd: Error: Microsoft ODBC Driver 17 for SQL Server : A network-related or instance-specific error has occurred while establishing a connection to SQL Server. Server is not found or not accessible. Check if instance name is correct and if SQL Server is configured to allow remote connections.
```

The message sounds like a network or naming problem. Check the service before changing anything else.

## Scope: what is and is not affected
| Check | Result while broken | What it tells you |
|---|---|---|
| `http://192.168.49.214/docvault/` in a browser | Still loads | The live app uses PostgreSQL on Linux, not this SQL Server. The two are separate systems |
| `Get-Service 'MSSQL$SQLEXPRESS'` | `Status: Stopped` | The service is not running, so nothing can connect |

Conclusion: only the Windows copy is down. Ask which "database" the user means before you start.

## Cause
The `MSSQL$SQLEXPRESS` service was stopped (here, on purpose with `Stop-Service`). Typical real causes: a crash, a patch restart that did not come back, a manual stop, a disk-full condition, a changed service account password.

## Fix
```powershell
Start-Service 'MSSQL$SQLEXPRESS'
sqlcmd -S .\SQLEXPRESS -E -d docvault_db -Q "SELECT COUNT(*) AS documents FROM documents"   # 11
```
Wait about 10 seconds if the first query fails: SQL Server needs a moment to open and recover its databases. The Application event log then showed `Starting up database 'docvault_db'` and `Recovery is complete`.

## Prevention
- Set the service recovery option to restart on failure.
- Alert on the service state or on port 1433 once remote access is enabled (planned Grafana check).
- Read the Application event log for the stop event before restarting, to tell a manual stop from a crash.

## Notes
- The event-log line for the stop itself was not captured (the first query returned only the startup events). Add it here when you repeat the exercise.
- WINWEB01's clock is three hours behind Toronto, and the NGINX logs are in UTC. Convert before matching logs across machines.

## Run it again
`trainer/windows/sql-server-stopped.ps1 -Break` then `-Fix`.
