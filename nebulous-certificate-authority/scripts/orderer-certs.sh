#!/usr/bin/env bash
set -euo pipefail

# ----- Configuration (override with env) -----
CA_SERVICE_NAME="${CA_SERVICE_NAME:-ca-orderer}"     # Docker Swarm service name on the overlay net
CA_PORT="${CA_PORT:-7055}"
CA_URL="${CA_SERVICE_NAME}:${CA_PORT}"

# CA logical name used in fabric-ca-client --caname
CA_CANAME="${CA_CANAME:-ca-orderer}"
CA_ADMIN_USER="${CA_ADMIN_USER:-ca-admin-${CA_CANAME}}"

# Secrets mounted in container (Swarm best practice)
CA_ADMIN_SECRET_FILE="${CA_ADMIN_SECRET_FILE:-/run/secrets/ca_orderer_bootstrap_pw}"
ORDERER_PW_FILE="${ORDERER_PW_FILE:-/run/secrets/ca_orderer_bootstrap_pw}"
ORDERER_ADMIN_PW_FILE="${ORDERER_ADMIN_PW_FILE:-/run/secrets/ca_orderer_bootstrap_pw}"

# Files & directories
ROOT_DIR="${ROOT_DIR:-/workspace}"  # mount your repo/workdir here in the client container
FABRIC_CA_CLIENT_HOME="${FABRIC_CA_CLIENT_HOME:-${ROOT_DIR}/organizations/ordererOrganizations/neb.com/ca-client}"
TLS_CERT_FILE="${TLS_CERT_FILE:-${ROOT_DIR}/organizations/fabric-ca/ordererOrg/tls-cert.pem}"

# Command timeouts and retries (overridable via env)
MAX_ATTEMPTS="${MAX_ATTEMPTS:-120}"
SLEEP_SECONDS="${SLEEP_SECONDS:-5}"

# ----- Helpers -----
read_secret() {
  local f="$1"
  if [[ -f "$f" ]]; then
    cat "$f"
  else
    echo ""
  fi
}

die() { echo "ERROR: $*" >&2; exit 1; }

# helper: test CA endpoint using available tool (curl preferred, fallback to openssl)
test_ca_endpoint() {
  local ca_url="$1"
  local cacert="$2"

  if command -v curl >/dev/null 2>&1; then
    curl --silent --fail --cacert "$cacert" "https://${ca_url}/cainfo" >/dev/null 2>&1
    return $?
  fi

  if command -v openssl >/dev/null 2>&1; then
    # openssl s_client: try TLS handshake and verify using CAfile.
    # echo piped to close connection after handshake.
    echo | openssl s_client -connect "${ca_url}" -CAfile "${cacert}" >/dev/null 2>&1
    return $?
  fi

  # no tool to check endpoint
  return 2
}

# ----- Validate environment -----
command -v fabric-ca-client >/dev/null 2>&1 || die "fabric-ca-client not found in PATH. Run this in a container with fabric-ca-client installed."

# Read secrets (do not echo them)
CA_ADMINPW="$(read_secret "$CA_ADMIN_SECRET_FILE")"
ORDERER_PW="$(read_secret "$ORDERER_PW_FILE")"
ORDERER_ADMIN_PW="$(read_secret "$ORDERER_ADMIN_PW_FILE")"

if [[ -z "$CA_ADMINPW" || -z "$ORDERER_PW" || -z "$ORDERER_ADMIN_PW" ]]; then
  die "Missing one or more required secrets. Ensure they are mounted at /run/secrets/..."
fi

mkdir -p "$FABRIC_CA_CLIENT_HOME"
export FABRIC_CA_CLIENT_HOME

echo "FABRIC_CA_CLIENT_HOME=${FABRIC_CA_CLIENT_HOME}"
echo "CA URL: https://${CA_URL}  (caname: ${CA_CANAME})"

# short warmup to avoid immediate race
echo "Sleeping 10s to allow CA to warm up..." ; sleep 10

# ----- Wait for CA and TLS cert availability -----
attempt=0
while (( attempt < MAX_ATTEMPTS )); do
  if [[ -f "$TLS_CERT_FILE" ]]; then
    if test_ca_endpoint "${CA_URL}" "${TLS_CERT_FILE}"; then
      echo "CA reachable and TLS cert present."
      break
    else
      echo "TLS cert at ${TLS_CERT_FILE} present but CA not responding yet. attempt ${attempt}/${MAX_ATTEMPTS}"
    fi
  else
    echo "Waiting for TLS cert at ${TLS_CERT_FILE}... attempt ${attempt}/${MAX_ATTEMPTS}"
  fi

  attempt=$((attempt + 1))
  sleep $SLEEP_SECONDS
done

if (( attempt == MAX_ATTEMPTS )); then
  die "CA not ready or TLS certificate not found after $MAX_ATTEMPTS attempts."
fi

# ----- Idempotency helpers -----
enrolled_marker="${FABRIC_CA_CLIENT_HOME}/.enrolled_ca_admin"
if [[ -f "${enrolled_marker}" ]]; then
  echo "CA admin already enrolled (marker ${enrolled_marker}). Skipping CA admin enroll step."
else
  echo "Enrolling CA admin..."
  set -x
  fabric-ca-client enroll -u "https://${CA_ADMIN_USER}:${CA_ADMINPW}@${CA_URL}" \
    --caname "${CA_CANAME}" \
    --tls.certfiles "${TLS_CERT_FILE}"
  set +x
  # mark success
  touch "${enrolled_marker}"
fi

# detect CA cert filename for config.yaml
CACERT_DIR="${FABRIC_CA_CLIENT_HOME}/msp/cacerts"
[[ -d "$CACERT_DIR" ]] || die "Expected cacerts dir ${CACERT_DIR} after enroll"

CA_CERT_FILENAME="$(ls "${CACERT_DIR}" | head -n1)"
[[ -n "$CA_CERT_FILENAME" ]] || die "No CA cert found in ${CACERT_DIR}"

# Create NodeOUs config if not exists
CONFIG_YAML="${FABRIC_CA_CLIENT_HOME}/msp/config.yaml"
if [[ ! -f "$CONFIG_YAML" ]]; then
  cat > "$CONFIG_YAML" <<EOF
NodeOUs:
  Enable: true
  ClientOUIdentifier:
    Certificate: cacerts/${CA_CERT_FILENAME}
    OrganizationalUnitIdentifier: client
  PeerOUIdentifier:
    Certificate: cacerts/${CA_CERT_FILENAME}
    OrganizationalUnitIdentifier: peer
  AdminOUIdentifier:
    Certificate: cacerts/${CA_CERT_FILENAME}
    OrganizationalUnitIdentifier: admin
  OrdererOUIdentifier:
    Certificate: cacerts/${CA_CERT_FILENAME}
    OrganizationalUnitIdentifier: orderer
EOF
fi

# ----- Register identities (skip if already registered or if register returns error that identity exists) -----
set -x
fabric-ca-client register --caname "${CA_CANAME}" --id.name orderer --id.secret "${ORDERER_PW}" --id.type orderer --tls.certfiles "${TLS_CERT_FILE}" || echo "Register 'orderer' may already exist - continuing"
fabric-ca-client register --caname "${CA_CANAME}" --id.name ordererAdmin --id.secret "${ORDERER_ADMIN_PW}" --id.type admin --tls.certfiles "${TLS_CERT_FILE}" || echo "Register 'ordererAdmin' may already exist - continuing"
set +x

# ----- Enroll orderer (MSP) if not already present -----
ORDERER_DIR="${ROOT_DIR}/organizations/ordererOrganizations/neb.com/orderers/orderer.neb.com"
if [[ -d "${ORDERER_DIR}/msp" && -n "$(ls -A ${ORDERER_DIR}/msp 2>/dev/null || true)" ]]; then
  echo "Orderer MSP already exists at ${ORDERER_DIR}/msp. Skipping enroll."
else
  mkdir -p "${ORDERER_DIR}/msp"
  set -x
  fabric-ca-client enroll -u "https://orderer:${ORDERER_PW}@${CA_URL}" --caname "${CA_CANAME}" -M "${ORDERER_DIR}/msp" --csr.hosts orderer.neb.com --csr.hosts localhost --tls.certfiles "${TLS_CERT_FILE}"
  set +x
fi

# copy config
cp -f "${CONFIG_YAML}" "${ORDERER_DIR}/msp/config.yaml"

# ----- Enroll for TLS -----
if [[ -d "${ORDERER_DIR}/tls" && -n "$(ls -A ${ORDERER_DIR}/tls 2>/dev/null || true)" ]]; then
  echo "Orderer TLS already exists at ${ORDERER_DIR}/tls. Skipping TLS enroll."
else
  mkdir -p "${ORDERER_DIR}/tls"
  set -x
  fabric-ca-client enroll -u "https://orderer:${ORDERER_PW}@${CA_URL}" --caname "${CA_CANAME}" -M "${ORDERER_DIR}/tls" --enrollment.profile tls --csr.hosts orderer.neb.com --csr.hosts localhost --tls.certfiles "${TLS_CERT_FILE}"
  set +x

  # organize TLS outputs
  cp "${ORDERER_DIR}/tls/tlscacerts/"* "${ORDERER_DIR}/tls/ca.crt"
  cp "${ORDERER_DIR}/tls/signcerts/"* "${ORDERER_DIR}/tls/server.crt"
  cp "${ORDERER_DIR}/tls/keystore/"* "${ORDERER_DIR}/tls/server.key"
fi

# copy tlscacerts into MSP locations
mkdir -p "${ORDERER_DIR}/msp/tlscacerts"
cp -f "${ORDERER_DIR}/tls/tlscacerts/"* "${ORDERER_DIR}/msp/tlscacerts/tlsca.neb.com-cert.pem"

mkdir -p "${ROOT_DIR}/organizations/ordererOrganizations/neb.com/msp/tlscacerts"
cp -f "${ORDERER_DIR}/tls/tlscacerts/"* "${ROOT_DIR}/organizations/ordererOrganizations/neb.com/msp/tlscacerts/tlsca.neb.com-cert.pem"

# copy CA certs to org MSP
mkdir -p "${ROOT_DIR}/organizations/ordererOrganizations/neb.com/msp/cacerts"
cp -f "${FABRIC_CA_CLIENT_HOME}/msp/cacerts/"* "${ROOT_DIR}/organizations/ordererOrganizations/neb.com/msp/cacerts/"

# copy config
cp -f "${CONFIG_YAML}" "${ROOT_DIR}/organizations/ordererOrganizations/neb.com/msp/config.yaml"

# enroll admin user
ADMIN_DIR="${ROOT_DIR}/organizations/ordererOrganizations/neb.com/users/Admin@neb.com"
if [[ -d "${ADMIN_DIR}/msp" && -n "$(ls -A ${ADMIN_DIR}/msp 2>/dev/null || true)" ]]; then
  echo "Admin MSP already exists. Skipping admin enroll."
else
  mkdir -p "${ADMIN_DIR}"
  set -x
  fabric-ca-client enroll -u "https://ordererAdmin:${ORDERER_ADMIN_PW}@${CA_URL}" --caname "${CA_CANAME}" -M "${ADMIN_DIR}/msp" --tls.certfiles "${TLS_CERT_FILE}"
  set +x
  cp -f "${CONFIG_YAML}" "${ADMIN_DIR}/msp/config.yaml"
fi

# -------------------------
# Post-processing: secure permissions & ownership
# -------------------------
echo "Securing file ownership and permissions..."

# attempt to set ownership to the invoking host UID (works when running interactively)
if command -v chown >/dev/null 2>&1; then
  host_uid=$(id -u 2>/dev/null || echo 0)
  host_gid=$(id -g 2>/dev/null || echo 0)
  chown -R "$host_uid:$host_gid" "${ROOT_DIR}/organizations/ordererOrganizations/neb.com" || true
fi

# tighten permissions
find "${ROOT_DIR}/organizations/ordererOrganizations/neb.com" -type d -exec chmod 750 {} \; 2>/dev/null || true
find "${ROOT_DIR}/organizations/ordererOrganizations/neb.com" -type f -name '*_sk' -exec chmod 600 {} \; 2>/dev/null || true
find "${ROOT_DIR}/organizations/ordererOrganizations/neb.com" -type f -name '*.key' -exec chmod 600 {} \; 2>/dev/null || true
find "${ROOT_DIR}/organizations/ordererOrganizations/neb.com" -type f -name '*.pem' -exec chmod 644 {} \; 2>/dev/null || true

echo "Orderer certificates setup completed successfully."
