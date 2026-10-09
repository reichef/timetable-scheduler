#!/bin/sh
# Creates the least-privilege application user (constitution III: persisted data protected by access control).
# The application user may connect, use the public schema, run DML and create objects (needed for Flyway),
# but is not a superuser and does not own the database.
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v app_user="$TTS_DB_USER" -v app_password="$TTS_DB_PASSWORD" <<'SQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
GRANT CONNECT ON DATABASE timetable TO :"app_user";
GRANT USAGE, CREATE ON SCHEMA public TO :"app_user";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app_user";
SQL
