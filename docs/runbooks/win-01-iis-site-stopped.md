# Runbook W1: DocVault is down through Windows, but fine on Linux

**Layer:** IIS on WINWEB01 (192.168.49.214). **Seen in the lab:** Nov 3, 2026.

## Symptom
`http://192.168.49.214/docvault/` does not load. Port 80 on the Windows server refuses connections.

## Scope: which layer is at fault?
Test one layer at a time. Skip a layer to see whether it is the cause.

| Test | Result while broken | What it tells you |
|---|---|---|
| `Test-NetConnection 192.168.49.214` (ping part) | Ping succeeded | The server is up and on the network, so this is not the lost-address problem (that one gave DestinationHostUnreachable) |
| `Test-NetConnection 192.168.49.214 -Port 80` | `TcpTestSucceeded : False` | Nothing is listening on port 80 |
| `Test-NetConnection 192.168.49.172 -Port 8081` | `TcpTestSucceeded : True` | NGINX on Linux answers, so Tomcat, the app and the database are not the problem |

Conclusion: the fault is in the IIS layer.

## Cause
```powershell
Get-Website
```
`Default Web Site` showed `State: Stopped`. A stopped site releases port 80, so IIS stops answering. (Typical real causes: a crash, a bad config change, a manual stop, a failed deploy.)

## Fix
```powershell
Start-Website 'Default Web Site'
Test-NetConnection 192.168.49.214 -Port 80     # TcpTestSucceeded : True
```
Then load `http://192.168.49.214/docvault/` and sign in.

## Prevention
- Alert on port 80 of WINWEB01 (planned Grafana check).
- Look at the IIS log and the Windows System event log for why the site stopped before just restarting it, so the cause is not lost.
- Keep `03-iis-proxy.ps1` so the setup can be rebuilt.

## Not captured
These tests were run on WINWEB01 itself, not from another PC, and the browser's error message was not recorded. Repeat from a PC and add the message here.

## Run it again
`trainer/windows/iis-site-stopped.ps1 -Break` then `-Fix`.
