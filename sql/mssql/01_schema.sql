-- DocVault tables for SQL Server (T-SQL), the Windows-side copy used for T-SQL practice.
-- Differences from the PostgreSQL schema (sql/01_schema.sql):
--   SERIAL            -> INT IDENTITY(1,1)
--   BOOLEAN           -> BIT
--   TIMESTAMP         -> DATETIME2
--   search_vector     -> left out (full-text search here would use SQL Server Full-Text, a later exercise)
--   users.password_hash -> left out on purpose: password hashes are never copied out of the live database
-- Re-runnable: it drops and recreates the three tables. Run only on this practice copy.
IF DB_ID('docvault_db') IS NULL CREATE DATABASE docvault_db;
GO
USE docvault_db;
GO
IF OBJECT_ID('dbo.document_access') IS NOT NULL DROP TABLE dbo.document_access;
IF OBJECT_ID('dbo.documents')       IS NOT NULL DROP TABLE dbo.documents;
IF OBJECT_ID('dbo.users')           IS NOT NULL DROP TABLE dbo.users;
GO
CREATE TABLE dbo.users (
    user_id  INT IDENTITY(1,1) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    role     VARCHAR(20) NOT NULL DEFAULT 'viewer'
);
CREATE TABLE dbo.documents (
    document_id  INT IDENTITY(1,1) PRIMARY KEY,
    title        VARCHAR(200) NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NULL,
    size_bytes   BIGINT NULL,
    storage_path VARCHAR(500) NOT NULL,
    owner_id     INT NOT NULL REFERENCES dbo.users(user_id),
    uploaded_at  DATETIME2 NULL DEFAULT SYSUTCDATETIME()
);
CREATE TABLE dbo.document_access (
    document_id INT NOT NULL REFERENCES dbo.documents(document_id) ON DELETE CASCADE,
    user_id     INT NOT NULL REFERENCES dbo.users(user_id),
    can_edit    BIT NOT NULL DEFAULT 0,
    PRIMARY KEY (document_id, user_id)
);
GO
