-- T-SQL practice queries on the DocVault copy in SQL Server Express (database docvault_db).
-- Run with: sqlcmd -S .\SQLEXPRESS -E -d docvault_db -i 02_practice.sql
-- Each query notes how PostgreSQL does the same thing, which is what interviews ask about.

-- 1. Join: newest documents with their owner.   (PostgreSQL: LIMIT 5)
SELECT TOP 5 d.document_id, d.title, u.username AS owner, d.uploaded_at
FROM documents d JOIN users u ON u.user_id = d.owner_id
ORDER BY d.uploaded_at DESC;

-- 2. Aggregation: documents and total size per owner.
SELECT u.username, COUNT(*) AS docs, SUM(d.size_bytes)/1024 AS total_kb
FROM documents d JOIN users u ON u.user_id = d.owner_id
GROUP BY u.username
ORDER BY docs DESC;

-- 3. The app's visibility rule: what user 2 (alice) may see. Expected on the lab data: documents 1 and 2.
SELECT d.document_id, d.title
FROM documents d
WHERE d.owner_id = 2
   OR EXISTS (SELECT 1 FROM document_access a WHERE a.document_id = d.document_id AND a.user_id = 2);

-- 4. Title search with LIKE. Finds exact text only (no stemming), and scans every row.
--    PostgreSQL's GIN full-text search handles stemming and uses an index.
--    Expected on the lab data: documents 6 and 7.
SELECT document_id, title FROM documents WHERE title LIKE '%redirect%';

-- Syntax differences worth knowing:
--   LIMIT n            -> TOP n
--   BOOLEAN            -> BIT (0/1)
--   now()              -> SYSUTCDATETIME() or GETDATE()
--   SERIAL             -> IDENTITY(1,1)
--   string || string   -> string + string, or CONCAT()
--   COALESCE           -> works in both; ISNULL is SQL Server only
