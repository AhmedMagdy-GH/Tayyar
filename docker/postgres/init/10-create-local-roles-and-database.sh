#!/usr/bin/env bash
set -Eeuo pipefail

required=(DB_NAME DB_MIGRATION_USERNAME DB_MIGRATION_PASSWORD DB_USERNAME DB_PASSWORD)
for name in "${required[@]}"; do
    if [[ -z "${!name:-}" ]]; then
        echo "Required initialization variable ${name} is empty" >&2
        exit 1
    fi
done

identifier='^[a-z_][a-z0-9_]{0,62}$'
for value in "$DB_NAME" "$DB_MIGRATION_USERNAME" "$DB_USERNAME"; do
    if [[ ! "$value" =~ $identifier ]]; then
        echo "Database and role names must be lowercase PostgreSQL identifiers" >&2
        exit 1
    fi
done
if [[ "$DB_MIGRATION_USERNAME" == "$DB_USERNAME" ]]; then
    echo "Migration and runtime roles must be different" >&2
    exit 1
fi

psql --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    --set=database_name="$DB_NAME" \
    --set=migration_user="$DB_MIGRATION_USERNAME" \
    --set=migration_password="$DB_MIGRATION_PASSWORD" \
    --set=runtime_user="$DB_USERNAME" \
    --set=runtime_password="$DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'migration_user', :'migration_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'migration_user') \gexec
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'runtime_user', :'runtime_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = :'runtime_user') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'database_name', :'migration_user')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = :'database_name') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'database_name') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'database_name', :'migration_user') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', :'database_name', :'runtime_user') \gexec
SQL

psql --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$DB_NAME" \
    --set=migration_user="$DB_MIGRATION_USERNAME" \
    --set=runtime_user="$DB_USERNAME" <<'SQL'
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SELECT format('GRANT USAGE, CREATE ON SCHEMA public TO %I', :'migration_user') \gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'runtime_user') \gexec
SELECT format(
    'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I',
    :'migration_user', :'runtime_user') \gexec
SELECT format(
    'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO %I',
    :'migration_user', :'runtime_user') \gexec
SQL
