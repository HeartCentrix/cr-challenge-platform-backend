-- LOCAL DEVELOPMENT ONLY. Run as the database owner after 04_admin_schema.sql.
-- Password supplied for local development is stored only as a salted bcrypt hash.
-- Never run this seed on AWS; provision unique credentials separately per environment.
BEGIN;
DO $$
BEGIN
    IF current_database() <> 'challenge_platform'
       OR inet_server_addr() IS NULL
       OR inet_server_addr() NOT IN (inet '127.0.0.1', inet '::1') THEN
        RAISE EXCEPTION 'Local admin seed is restricted to the local challenge_platform database';
    END IF;
END $$;
INSERT INTO challenge_platform_admin.admin_user (email, password_hash)
VALUES ('admin@codereport.com', '$2a$12$Yd5UnVGeKJTU/N99zHvOFubym5.HzKb2ff7cmx/Nw9LcfEU3lvaVS')
ON CONFLICT (email) DO NOTHING;
COMMIT;
