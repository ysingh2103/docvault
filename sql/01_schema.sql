CREATE DATABASE docvault_db;
\c docvault_db

CREATE TABLE users (
    user_id   SERIAL PRIMARY KEY,
    username  VARCHAR(50) UNIQUE NOT NULL,
    role      VARCHAR(20) NOT NULL DEFAULT 'viewer'
);

CREATE TABLE documents (
    document_id   SERIAL PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100),
    size_bytes    BIGINT,
    storage_path  VARCHAR(500) NOT NULL,
    owner_id      INT NOT NULL REFERENCES users(user_id),
    uploaded_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    search_vector TSVECTOR
);

CREATE TABLE document_access (
    document_id INT NOT NULL REFERENCES documents(document_id) ON DELETE CASCADE,
    user_id     INT NOT NULL REFERENCES users(user_id),
    can_edit    BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (document_id, user_id)
);

CREATE INDEX idx_documents_search ON documents USING GIN (search_vector);

INSERT INTO users (username, role) VALUES
    ('admin', 'admin'), ('alice', 'editor'), ('bob', 'viewer');

CREATE USER docvault_app;
GRANT CONNECT ON DATABASE docvault_db TO docvault_app;
GRANT USAGE ON SCHEMA public TO docvault_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO docvault_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO docvault_app;
