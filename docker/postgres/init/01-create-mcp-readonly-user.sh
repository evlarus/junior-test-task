#!/bin/sh
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<EOSQL
CREATE ROLE mcp_readonly LOGIN PASSWORD '${MCP_DB_PASSWORD}';
ALTER ROLE mcp_readonly SET default_transaction_read_only = on;
GRANT CONNECT ON DATABASE "${POSTGRES_DB}" TO mcp_readonly;
GRANT USAGE ON SCHEMA public TO mcp_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO mcp_readonly;
ALTER DEFAULT PRIVILEGES FOR ROLE "${POSTGRES_USER}" IN SCHEMA public GRANT SELECT ON TABLES TO mcp_readonly;
EOSQL
