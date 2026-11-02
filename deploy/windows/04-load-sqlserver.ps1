# WINWEB01 - create the DocVault tables in SQL Server Express and load the exported CSV files.
# Needs: SQL Server Express (instance SQLEXPRESS), sql\mssql\01_schema.sql, and the three CSV files
# made by export-from-postgres.sh on the Linux VM.
# Re-runnable: the schema script drops and recreates the tables, so the data is replaced.
param(
    [string]$Dir      = 'C:\setup\data',
    [string]$Schema   = 'C:\setup\01_schema.sql',
    [string]$Instance = '.\SQLEXPRESS'
)
$ErrorActionPreference = 'Stop'
$sqlcmd = 'C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\170\Tools\Binn\SQLCMD.EXE'
$inv = [Globalization.CultureInfo]::InvariantCulture

& $sqlcmd -S $Instance -E -b -i $Schema
if ($LASTEXITCODE -ne 0) { throw 'The schema script failed.' }

# Column name -> .NET type, in the order the tables must be loaded (parents before children).
$tables = [ordered]@{
    users           = [ordered]@{ user_id = [int]; username = [string]; role = [string] }
    documents       = [ordered]@{ document_id = [int]; title = [string]; file_name = [string]
                                  content_type = [string]; size_bytes = [long]; storage_path = [string]
                                  owner_id = [int]; uploaded_at = [datetime] }
    document_access = [ordered]@{ document_id = [int]; user_id = [int]; can_edit = [bool] }
}

function ConvertTo-Cell($text, [type]$type) {
    if ($null -eq $text -or $text -eq '') { return [DBNull]::Value }
    if ($type -eq [bool])     { return ($text -in 't', 'true', '1') }
    if ($type -eq [datetime]) { return [datetime]::Parse($text, $inv) }
    return [Convert]::ChangeType($text, $type, $inv)
}

$conn = New-Object System.Data.SqlClient.SqlConnection "Server=$Instance;Database=docvault_db;Integrated Security=True"
$conn.Open()
try {
    foreach ($name in $tables.Keys) {
        $cols = $tables[$name]
        $dt = New-Object System.Data.DataTable
        foreach ($c in $cols.Keys) { [void]$dt.Columns.Add($c, $cols[$c]) }
        foreach ($line in (Import-Csv (Join-Path $Dir "$name.csv"))) {
            $row = $dt.NewRow()
            foreach ($c in $cols.Keys) { $row[$c] = ConvertTo-Cell $line.$c $cols[$c] }
            $dt.Rows.Add($row)
        }
        # KeepIdentity keeps the original ids (document 200009 stays 200009), so rows still match the live app.
        $bc = New-Object System.Data.SqlClient.SqlBulkCopy($conn, [System.Data.SqlClient.SqlBulkCopyOptions]::KeepIdentity, $null)
        $bc.DestinationTableName = "dbo.$name"
        foreach ($c in $cols.Keys) { [void]$bc.ColumnMappings.Add($c, $c) }
        $bc.WriteToServer($dt)
        '{0,-16} {1} rows loaded' -f $name, $dt.Rows.Count
    }
}
finally { $conn.Close() }

& $sqlcmd -S $Instance -E -d docvault_db -Q "SELECT 'users' AS tbl, COUNT(*) AS n FROM users UNION ALL SELECT 'documents', COUNT(*) FROM documents UNION ALL SELECT 'document_access', COUNT(*) FROM document_access"
