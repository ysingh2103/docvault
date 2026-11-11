# Rebuild the Windows leg from nothing

Use this when the Windows VM is gone (new lab, cloud VM, trial licence). Takes about an hour, most of it
waiting for installers. Needs: a fresh Windows Server 2019 or 2022 with internet, and DocVault already
running on the Linux side (the Docker version in `deploy/docker/` is fine).

## 1. On the Linux side (once)
Export the three tables to CSV (run on the host with PostgreSQL; with Docker, run the same `COPY` through
`docker compose exec db psql -U postgres -d docvault_db`):
```bash
bash deploy/windows/export-from-postgres.sh ~/docvault-export
```
Serve them to the Windows VM for a few minutes, then stop the server:
```bash
cd ~/docvault-export && python3 -m http.server 9000
```
(Allow port 9000 from the Windows VM only, and remove the rule afterwards.)

## 2. On the Windows VM (PowerShell as Administrator)
```powershell
# Get the scripts
Invoke-WebRequest https://github.com/ysingh2103/docvault/archive/refs/heads/main.zip -OutFile C:\docvault.zip
Expand-Archive C:\docvault.zip C:\ -Force
cd C:\docvault-main\deploy\windows

# Get the data (replace LINUXIP)
New-Item -ItemType Directory -Force C:\setup\data | Out-Null
foreach ($t in 'users','documents','document_access') {
  Invoke-WebRequest "http://LINUXIP:9000/$t.csv" -OutFile "C:\setup\data\$t.csv" }

# Build everything (replace LINUXIP; add -SetStaticIp only on a LAN where you choose the address)
.\rebuild-all.ps1 -Upstream http://LINUXIP:8081
```
The last line of output should say `HTTP 200` and show a request ID.

## 3. Check
- Browser: `http://<windows-ip>/docvault/` shows the sign-in page.
- `Get-Service W3SVC, 'MSSQL$SQLEXPRESS'` shows both Running.
- `sql/mssql/02_practice.sql` returns the same results as on PostgreSQL.

## Notes
- Step 1 of the script (static IP) is off by default; in a cloud VM never use it.
- If `rebuild-all.ps1` stops at the SQL Express step, install it by hand, then run it again. It is safe to re-run.
- If the Windows VM is in a different network from Linux, make sure it can reach port 8081 on the Linux side.
- Status: the individual scripts were tested on the lab; the all-in-one wrapper and the unattended SQL Express install have not been run on a clean server yet.
