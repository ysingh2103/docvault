#!/usr/bin/env bash
# Restore DocVault from the lab backup folder into the containers.
# Usage: ./restore.sh ~/lab-backup
set -euo pipefail
B="${1:?usage: ./restore.sh <backup-folder>}"
cd "$(dirname "$0")"
[ -f .env ] || { echo "Create .env first: cp .env.example .env and edit it"; exit 1; }
set -a; . ./.env; set +a
[ -f "$B/postgres-all.sql.gz" ] && [ -f "$B/docvault-files.tgz" ] || { echo "Backup files not found in $B"; exit 1; }

echo "1/4 Starting the database..."
docker compose up -d --build db
until docker compose exec -T db pg_isready -U postgres >/dev/null 2>&1; do sleep 1; done

echo "2/4 Loading the PostgreSQL dump (role/exists notices are normal)..."
gunzip -c "$B/postgres-all.sql.gz" | docker compose exec -T db psql -U postgres -q -d postgres >/dev/null 2>restore-errors.log || true
docker compose exec -T db psql -U postgres -q -c "ALTER ROLE docvault_app PASSWORD '${DB_PASSWORD}'"
# The app needs the lockout table; this file is safe to run on a dump that already has it.
docker compose exec -T db psql -U postgres -q -d docvault_db < ../../sql/04_lockout.sql

echo "3/4 Copying the uploaded files..."
T="$(mktemp -d)"; tar xzf "$B/docvault-files.tgz" -C "$T"
SRC="$(find "$T" -type d -name files | head -1)"
[ -n "$SRC" ] || { echo "No 'files' folder in docvault-files.tgz. Run: tar tzf $B/docvault-files.tgz | head"; exit 1; }
docker compose up -d --no-start app
docker compose cp "$SRC/." app:/var/lib/docvault/files/
rm -rf "$T"

echo "4/4 Starting DocVault..."
docker compose up -d
echo "Done. Open http://localhost:8081/docvault/  (errors, if any: deploy/docker/restore-errors.log)"
