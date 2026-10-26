# DocVault

A small document-management web app built to practise the stack enterprise support teams work with: Linux, Tomcat, NGINX, PostgreSQL, plus a Windows leg and monitoring. Self-directed learning project, built step by step with AI assistance.

## Architecture
Browser -> NGINX (reverse proxy) -> Tomcat 10 (DocVault WAR) -> PostgreSQL (metadata) + disk (files)

## Status
| Phase | Status |
|---|---|
| 0. Database schema | Done |
| 1. Upload/list app on Tomcat | In progress |
| 2. Search and permissions | Planned |
| 3. Windows leg (IIS + SQL Server) | Planned |
| 4. Monitoring (Grafana) | Planned |
| 5. Break-fix trainer and runbooks | Planned |

## Layout
- sql/ - database schema
- app/ - Java servlet app (Maven)
- docs/ - architecture and incident runbooks
- trainer/ - scripted faults

## Build
cd app && mvn package   (produces target/docvault.war)

## Security
No credentials are stored in this repo. The database password is set interactively and configured in Tomcat, not in code.
