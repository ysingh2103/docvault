# Windows leg (WINWEB01, Windows Server 2019)

IIS is a second front door in front of the same DocVault, so the same app can be
reached through two web servers.

```
Browser -> IIS (WINWEB01 :80) -> NGINX (Linux :8081) -> Tomcat (:8080) -> DocVault -> PostgreSQL
```

Run in PowerShell (Admin) on the server, in order:

| Script | What it does |
|---|---|
| `01-network.ps1` | Fixed IP address and DNS |
| `02-install-iis.ps1` | IIS, URL Rewrite, Application Request Routing |
| `03-iis-proxy.ps1` | Turns on the proxy and forwards `/docvault/` to NGINX |
| `rebuild-all.ps1` | All of the above plus SQL Server Express, in one go (see `REBUILD.md`) |

The request ID is created by NGINX, so it is the same in the IIS response, the NGINX
log and the Tomcat log. The domain join is skipped on purpose: this lab does not need it.
