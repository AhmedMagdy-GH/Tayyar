-- Read-only pre-migration inspection. Run against the intended target database.
-- No password hashes, application rows, DDL, or privilege changes are accessed.
BEGIN READ ONLY;

SELECT current_database(), current_user, session_user, version();

SELECT rolname, rolsuper, rolcreatedb, rolcreaterole, rolcanlogin, rolbypassrls
FROM pg_roles
WHERE rolname IN (current_user, 'tayyar_app')
ORDER BY rolname;

SELECT nspname AS schema_name, pg_get_userbyid(nspowner) AS owner,
       has_schema_privilege(current_user, oid, 'USAGE') AS current_user_usage,
       has_schema_privilege(current_user, oid, 'CREATE') AS current_user_create
FROM pg_namespace
WHERE nspname !~ '^pg_' AND nspname <> 'information_schema';

SELECT schemaname, tablename, tableowner
FROM pg_tables
WHERE schemaname !~ '^pg_' AND schemaname <> 'information_schema'
ORDER BY schemaname, tablename;

SELECT table_schema, table_name, grantee, privilege_type
FROM information_schema.table_privileges
WHERE table_schema !~ '^pg_' AND table_schema <> 'information_schema'
ORDER BY table_schema, table_name, grantee, privilege_type;

SELECT pg_get_userbyid(roleid) AS granted_role, pg_get_userbyid(member) AS member
FROM pg_auth_members
WHERE member = (SELECT oid FROM pg_roles WHERE rolname = current_user);

SELECT pg_get_userbyid(defaclrole) AS owner, n.nspname AS schema_name,
       defaclobjtype AS object_type, defaclacl AS default_privileges
FROM pg_default_acl d
LEFT JOIN pg_namespace n ON n.oid = d.defaclnamespace;

COMMIT;
