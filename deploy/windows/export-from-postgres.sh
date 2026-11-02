#!/usr/bin/env bash
# Run on the LINUX VM. Exports DocVault's metadata to CSV files for the SQL Server copy.
# Only the three tables' plain columns are exported. Password hashes and the search vector are not.
set -euo pipefail
OUT="${1:-$HOME/docvault-export}"
mkdir -p "$OUT"

export_table() {   # name, SELECT
  sudo -u postgres psql -d docvault_db -X -q -c "COPY ($2) TO STDOUT WITH CSV HEADER" > "$OUT/$1.csv"
}
export_table users            "SELECT user_id, username, role FROM users ORDER BY user_id"
export_table documents        "SELECT document_id, title, file_name, content_type, size_bytes, storage_path, owner_id, uploaded_at FROM documents ORDER BY document_id"
export_table document_access  "SELECT document_id, user_id, can_edit FROM document_access ORDER BY document_id, user_id"

echo "Exported to $OUT (line count includes the header row):"
wc -l "$OUT"/*.csv
