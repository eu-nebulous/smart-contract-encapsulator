#!/usr/bin/env bash
set -euo pipefail

# ----- Configuration (override with env) -----
CA_SERVICE_NAME="${CA_SERVICE_NAME:-ca-org2-broker}"
CA_PORT="${CA_PORT:-7059}"
CA_URL="${CA_SERVICE_NAME}:${CA_PORT}"

# CA logical name used in fabric-ca-client --caname
CA_CANAME="${CA_CANAME:-ca-org2-broker}"
CA_ADMIN_USER="${CA_ADMIN_USER:-ca-admin-${CA_CANAME}}"

# Secrets mounted in container (Swarm best practice)
CA_ADMIN_SECRET_FILE="${CA_ADMIN_SECRET_FILE:-/run/secrets/ca_org2_bootstrap_pw}"
PEER_PW_FILE="${PEER_PW_FILE:-/run/secrets/ca_org2_bootstrap_pw}"
USER_PW_FILE="${USER_PW_FILE:-/run/secrets/ca_org2_bootstrap_pw}"
ADMIN_PW_FILE="${ADMIN_PW_FILE:-/run/secrets/ca_org2_bootstrap_pw}"

# Files & directories
ROOT_DIR="${ROOT_DIR:-/workspace}"
FABRIC_CA_CLIENT_HOME="${FABRIC_CA_CLIENT_HOME:-${ROOT_DIR}/organizations/peerOrganizations/brokerorg.neb.com/ca-client}"
TLS_CERT_FILE="${TLS_CERT_FILE:-${ROOT_DIR}/organizations/fabric-ca/org2-broker/tls-cert.pem}"

# Peer configuration
PEER_NAME="${PEER_NAME:-peer0}"
PEER_DOMAIN="${PEER_DOMAIN:-brokerorg.neb.com}"
PEER_FQDN="${PEER_NAME}.${PEER_DOMAIN}"

# Command timeouts and retries
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

test_ca_endpoint() {
    local ca_url="$1"
    local cacert="$2"

    # Use fabric-ca-client getcainfo to verify CA readiness (works over TLS).
    # Silence output but return success only when CA responds.
    if fabric-ca-client getcainfo -u "https://${ca_url}" --tls.certfiles "${cacert}" -d >/dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

# ----- Validate environment -----
command -v fabric-ca-client >/dev/null 2>&1 || die "fabric-ca-client not found in PATH."

# ADD THESE LINES - Force no client certificates
export FABRIC_CA_CLIENT_TLS_CLIENT_CERTFILE=""
export FABRIC_CA_CLIENT_TLS_CLIENT_KEYFILE=""
export FABRIC_CA_CLIENT_DEBUG=true

# Read secrets
CA_ADMINPW="$(read_secret "$CA_ADMIN_SECRET_FILE")"
PEER_PW="$(read_secret "$PEER_PW_FILE")"
USER_PW="$(read_secret "$USER_PW_FILE")"
ADMIN_PW="$(read_secret "$ADMIN_PW_FILE")"

if [[ -z "$CA_ADMINPW" || -z "$PEER_PW" || -z "$USER_PW" || -z "$ADMIN_PW" ]]; then
  die "Missing one or more required secrets. Ensure they are mounted at /run/secrets/..."
fi

mkdir -p "$FABRIC_CA_CLIENT_HOME"
export FABRIC_CA_CLIENT_HOME

echo "FABRIC_CA_CLIENT_HOME=${FABRIC_CA_CLIENT_HOME}"
echo "CA URL: https://${CA_URL}  (caname: ${CA_CANAME})"
echo "Peer FQDN: ${PEER_FQDN}"

# short warmup
echo "Sleeping 10s to allow CA to warm up..." ; sleep 10

# ----- Wait for CA and TLS cert availability -----
attempt=0
while (( attempt < MAX_ATTEMPTS )); do
    if [[ -f "$TLS_CERT_FILE" ]]; then
        echo "Testing Fabric CA connection to ${CA_URL} (attempt $((attempt + 1))/$MAX_ATTEMPTS)..."

        if test_ca_endpoint "${CA_URL}" "${TLS_CERT_FILE}"; then
            echo "Fabric CA is reachable and responding at ${CA_URL}"
            break
        else
            echo "Fabric CA not ready (getcainfo failed) - attempt ${attempt}/${MAX_ATTEMPTS}"
        fi
    else
        echo "Waiting for TLS cert at ${TLS_CERT_FILE}... attempt ${attempt}/${MAX_ATTEMPTS}"
    fi

    attempt=$((attempt + 1))
    sleep $SLEEP_SECONDS
done

if (( attempt == MAX_ATTEMPTS )); then
    echo "Final attempt - diagnosing with full debug output:" >&2
    fabric-ca-client getcainfo -u "https://${CA_URL}" --tls.certfiles "${TLS_CERT_FILE}" -d || true
    die "CA not ready after $MAX_ATTEMPTS attempts."
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

# ----- Register identities -----
set -x
fabric-ca-client register --caname "${CA_CANAME}" --id.name "${PEER_NAME}" --id.secret "${PEER_PW}" --id.type peer --tls.certfiles "${TLS_CERT_FILE}" || echo "Register '${PEER_NAME}' may already exist - continuing"
fabric-ca-client register --caname "${CA_CANAME}" --id.name user1 --id.secret "${USER_PW}" --id.type client --tls.certfiles "${TLS_CERT_FILE}" || echo "Register 'user1' may already exist - continuing"
fabric-ca-client register --caname "${CA_CANAME}" --id.name admin --id.secret "${ADMIN_PW}" --id.type admin --tls.certfiles "${TLS_CERT_FILE}" || echo "Register 'admin' may already exist - continuing"
set +x

# ----- Enroll peer (MSP) if not already present -----
PEER_DIR="${ROOT_DIR}/organizations/peerOrganizations/${PEER_DOMAIN}/peers/${PEER_FQDN}"
if [[ -d "${PEER_DIR}/msp" && -n "$(ls -A ${PEER_DIR}/msp 2>/dev/null || true)" ]]; then
  echo "Peer MSP already exists at ${PEER_DIR}/msp. Skipping enroll."
else
  mkdir -p "${PEER_DIR}/msp"
  set -x
  fabric-ca-client enroll -u "https://${PEER_NAME}:${PEER_PW}@${CA_URL}" --caname "${CA_CANAME}" -M "${PEER_DIR}/msp" --csr.hosts "${PEER_FQDN}" --csr.hosts localhost --tls.certfiles "${TLS_CERT_FILE}"
  set +x
fi

# copy config
cp -f "${CONFIG_YAML}" "${PEER_DIR}/msp/config.yaml"

# ----- Enroll for TLS -----
if [[ -d "${PEER_DIR}/tls" && -n "$(ls -A ${PEER_DIR}/tls 2>/dev/null || true)" ]]; then
  echo "Peer TLS already exists at ${PEER_DIR}/tls. Skipping TLS enroll."
else
  mkdir -p "${PEER_DIR}/tls"
  set -x
  fabric-ca-client enroll -u "https://${PEER_NAME}:${PEER_PW}@${CA_URL}" --caname "${CA_CANAME}" -M "${PEER_DIR}/tls" --enrollment.profile tls --csr.hosts "${PEER_FQDN}" --csr.hosts localhost --tls.certfiles "${TLS_CERT_FILE}"
  set +x

  # organize TLS outputs
  cp "${PEER_DIR}/tls/tlscacerts/"* "${PEER_DIR}/tls/ca.crt"
  cp "${PEER_DIR}/tls/signcerts/"* "${PEER_DIR}/tls/server.crt"
  cp "${PEER_DIR}/tls/keystore/"* "${PEER_DIR}/tls/server.key"
fi

# copy tlscacerts into MSP locations
mkdir -p "${PEER_DIR}/msp/tlscacerts"
cp -f "${PEER_DIR}/tls/tlscacerts/"* "${PEER_DIR}/msp/tlscacerts/tlsca.${PEER_DOMAIN}-cert.pem"

mkdir -p "${ROOT_DIR}/organizations/peerOrganizations/${PEER_DOMAIN}/msp/tlscacerts"
cp -f "${PEER_DIR}/tls/tlscacerts/"* "${ROOT_DIR}/organizations/peerOrganizations/${PEER_DOMAIN}/msp/tlscacerts/tlsca.${PEER_DOMAIN}-cert.pem"

# copy CA certs to org MSP
mkdir -p "${ROOT_DIR}/organizations/peerOrganizations/${PEER_DOMAIN}/msp/cacerts"
cp -f "${FABRIC_CA_CLIENT_HOME}/msp/cacerts/"* "${ROOT_DIR}/organizations/peerOrganizations/${PEER_DOMAIN}/msp/cacerts/"

# copy config
cp -f "${CONFIG_YAML}" "${ROOT_DIR}/organizations/peerOrganizations/${PEER_DOMAIN}/msp/config.yaml"

# enroll user1
USER_DIR="${ROOT_DIR}/organizations/peerOrganizations/${PEER_DOMAIN}/users/user1@${PEER_DOMAIN}"
if [[ -d "${USER_DIR}/msp" && -n "$(ls -A ${USER_DIR}/msp 2>/dev/null || true)" ]]; then
  echo "User MSP already exists. Skipping user enroll."
else
  mkdir -p "${USER_DIR}"
  set -x
  fabric-ca-client enroll -u "https://user1:${USER_PW}@${CA_URL}" --caname "${CA_CANAME}" -M "${USER_DIR}/msp" --tls.certfiles "${TLS_CERT_FILE}"
  set +x
  cp -f "${CONFIG_YAML}" "${USER_DIR}/msp/config.yaml"
fi

# enroll admin user
ADMIN_DIR="${ROOT_DIR}/organizations/peerOrganizations/${PEER_DOMAIN}/users/Admin@${PEER_DOMAIN}"
if [[ -d "${ADMIN_DIR}/msp" && -n "$(ls -A ${ADMIN_DIR}/msp 2>/dev/null || true)" ]]; then
  echo "Admin MSP already exists. Skipping admin enroll."
else
  mkdir -p "${ADMIN_DIR}"
  set -x
  fabric-ca-client enroll -u "https://admin:${ADMIN_PW}@${CA_URL}" --caname "${CA_CANAME}" -M "${ADMIN_DIR}/msp" --tls.certfiles "${TLS_CERT_FILE}"
  set +x
  cp -f "${CONFIG_YAML}" "${ADMIN_DIR}/msp/config.yaml"
fi

# -------------------------
# Post-processing: secure permissions & ownership
# -------------------------
echo "Securing file ownership and permissions..."

if command -v chown >/dev/null 2>&1; then
  host_uid=$(id -u 2>/dev/null || echo 0)
  host_gid=$(id -g 2>/dev/null || echo 0)
  chown -R "$host_uid:$host_gid" "${ROOT_DIR}/organizations/peerOrganizations/${PEER_DOMAIN}" || true
fi

find "${ROOT_DIR}/organizations/peerOrganizations/${PEER_DOMAIN}" -type d -exec chmod 750 {} \; 2>/dev/null || true
find "${ROOT_DIR}/organizations/peerOrganizations/${PEER_DOMAIN}" -type f -name '*_sk' -exec chmod 600 {} \; 2>/dev/null || true
find "${ROOT_DIR}/organizations/peerOrganizations/${PEER_DOMAIN}" -type f -name '*.key' -exec chmod 600 {} \; 2>/dev/null || true
find "${ROOT_DIR}/organizations/peerOrganizations/${PEER_DOMAIN}" -type f -name '*.pem' -exec chmod 644 {} \; 2>/dev/null || true

echo "org2 (broker) certificates setup completed successfully."