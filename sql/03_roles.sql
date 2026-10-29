-- Phase 2b: login and roles. Run as postgres:
--   sudo -u postgres psql -d docvault_db -f sql/03_roles.sql

-- 1. Where each user's salted password hash is stored (never the password).
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(200);

-- 2. Sample sharing so each role sees something different.
--    users: 1 = admin, 2 = alice (editor), 3 = bob (viewer)
--    can_edit = true means the user may change the document (reserved for a later phase).
INSERT INTO document_access (document_id, user_id, can_edit) VALUES
    (1, 2, TRUE),    -- alice can edit document 1
    (2, 2, FALSE),   -- alice can read document 2
    (1, 3, FALSE)    -- bob can read document 1
ON CONFLICT (document_id, user_id) DO NOTHING;

-- 3. Set passwords AFTER generating hashes (see README, "Passwords"):
--    java -cp app/target/classes com.docvault.PasswordTool 'a-password-you-choose'
--    then:
--    UPDATE users SET password_hash = '<paste the whole pbkdf2$... line>' WHERE username = 'admin';
--    Repeat for alice and bob. Do not commit real passwords or hashes.
