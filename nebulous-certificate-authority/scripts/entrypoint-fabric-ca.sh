#!/bin/sh
set -eu

CA_NAME="${FABRIC_CA_SERVER_CA_NAME:-}"
PORT="${FABRIC_CA_SERVER_PORT:-7054}"

if [ -z "$CA_NAME" ]; then
  echo "ERROR: FABRIC_CA_SERVER_CA_NAME not set"
  exit 2
fi

# map CA name to DB name and bootstrap secret
case "$CA_NAME" in
  ca-orderer) 
    DBNAME="ca_orderer"
    BOOT_SECRET="/run/secrets/ca_orderer_bootstrap_pw"
    ;;
  ca-org1-resourceprovider) 
    DBNAME="ca_org1"
    BOOT_SECRET="/run/secrets/ca_org1_bootstrap_pw"
    ;;
  ca-org2-broker) 
    DBNAME="ca_org2"
    BOOT_SECRET="/run/secrets/ca_org2_bootstrap_pw"
    ;;
  *)
    echo "ERROR: Unknown CA_NAME='$CA_NAME'"
    exit 2
    ;;
esac

# Read secrets
BOOT_PW="$(cat "$BOOT_SECRET")"
PG_PASS="$(cat /run/secrets/postgres_password)"

# Set working directory
WORKDIR="/etc/hyperledger/fabric-ca-server"
cd "$WORKDIR"

# Simple wait - PostgreSQL should be ready by the time we get here
echo "Waiting for PostgreSQL to be ready..."
echo "PostgreSQL container should be healthy before CA containers start due to depends_on"
sleep 15

echo "Proceeding to start Fabric CA server..."

# Build connection details
BOOTSTRAP="ca-admin-${CA_NAME}:${BOOT_PW}"
DB_DATASOURCE="host=postgres-ca port=5432 user=fabricca password=${PG_PASS} dbname=${DBNAME} sslmode=disable"

echo "Starting fabric-ca-server:"
echo "  CA: $CA_NAME"
echo "  Port: $PORT" 
echo "  Database: $DBNAME"
echo "  Bootstrap: ca-admin-${CA_NAME}"
echo ""

# Start the server - it will handle DB connection errors itself
echo "Executing: fabric-ca-server start"
exec fabric-ca-server start \
  -b "$BOOTSTRAP" \
  --port "$PORT" \
  --db.type postgres \
  --db.datasource "$DB_DATASOURCE"