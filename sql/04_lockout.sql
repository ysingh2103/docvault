-- Phase 5b: lockout after repeated wrong passwords.
-- Run as the postgres superuser (the app user cannot create tables):
--   sudo -u postgres psql -d docvault_db -f sql/04_lockout.sql
-- One row per FAILED sign-in. A user+address pair with 5 failures in the last 10 minutes is locked.
-- Rows are also kept for usernames that do not exist, so a lock never reveals which usernames are real.
CREATE TABLE IF NOT EXISTS login_attempts (
    attempt_id   BIGSERIAL PRIMARY KEY,
    username     VARCHAR(50) NOT NULL,      -- lower-cased, as typed
    client_ip    VARCHAR(64) NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_login_attempts_lookup
    ON login_attempts (username, client_ip, attempted_at DESC);

-- Same rights as the other tables (the earlier ALL TABLES grant only covered tables that existed then).
GRANT SELECT, INSERT, DELETE ON login_attempts TO docvault_app;
GRANT USAGE, SELECT ON SEQUENCE login_attempts_attempt_id_seq TO docvault_app;

-- To unlock someone by hand (as postgres):
--   DELETE FROM login_attempts WHERE username = 'bob';
-- To see who is locked right now:
--   SELECT username, client_ip, count(*) AS failures, max(attempted_at) AS last_failure
--   FROM login_attempts WHERE attempted_at > now() - interval '10 minutes'
--   GROUP BY 1, 2 HAVING count(*) >= 5;
