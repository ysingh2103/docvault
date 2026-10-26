
# DocVault

A small document-management web app built to practise the stack enterprise support teams work with: Linux, Tomcat, NGINX, PostgreSQL, plus a Windows leg and monitoring. Self-directed learning project, built step by step with AI assistance.

## Architecture
Browser -> NGINX (reverse proxy) -> Tomcat 10 (DocVault WAR) -> PostgreSQL (metadata) + disk (files)

## Features
- Upload, list, and download documents (20 MB limit)
- Files stored on disk, metadata in PostgreSQL
- Database access through a Tomcat-managed connection pool (JNDI), no credentials in code
- Dark blue UI with drag-and-drop upload and live filtering

## Status
| Phase | Status |
|---|---|
| 0. Database schema | Done |
| 1. Upload/list app on Tomcat behind NGINX | Done |
| 2. Search and permissions | Next |
| 3. Windows leg (IIS + SQL Server) | Planned |
| 4. Monitoring (Grafana) | Planned |
| 5. Break-fix trainer and runbooks | Planned |

## Layout
- sql/ - database schema
- app/ - Java servlet app (Maven)
- deploy/ - NGINX, Tomcat, and systemd configuration used on the server
- docs/ - architecture and incident runbooks
- trainer/ - scripted faults

## Build and deploy
1. Create the database: sudo -u postgres psql < sql/01_schema.sql, then set the docvault_app password with \password
2. Put the PostgreSQL JDBC driver in Tomcat's lib folder
3. Copy deploy/tomcat/docvault.xml.example to /etc/tomcat10/Catalina/localhost/docvault.xml and set the real password there
4. Allow Tomcat to write to the storage folder with the systemd drop-in in deploy/systemd/
5. Build with: cd app && mvn package, then copy target/docvault.war into Tomcat's webapps folder
6. Add the NGINX route from deploy/nginx/site.conf

## Security
No credentials are stored in this repo. The database password lives only in Tomcat's configuration on the server.
