-- Phase 2a: full-text search. Run as postgres:
--   sudo -u postgres psql -d docvault_db -f sql/02_search.sql

-- Backfill rows that were inserted without a search vector.
UPDATE documents
   SET search_vector = to_tsvector('english', title || ' ' || file_name)
 WHERE search_vector IS NULL;

-- Proof the GIN index is used (compare with the index dropped):
--   EXPLAIN ANALYZE SELECT document_id FROM documents
--    WHERE search_vector @@ websearch_to_tsquery('english', 'report');
-- Note: with only a handful of rows PostgreSQL may prefer a sequential scan.
-- Force the comparison with: SET enable_seqscan = off;
