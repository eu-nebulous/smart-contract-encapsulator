#!/bin/bash
set -e

# --- 1. Environment and Variables ---
export FABRIC_CFG_PATH=/home/ubuntu/smart-contract-encapsulator/nebulous-fabric-network
export CORE_PEER_TLS_ENABLED=true
export CHANNEL_NAME="sla-channel"
export BASE_DIR="$(dirname "$PWD")"
export ORDERER_CA_FILE="${BASE_DIR}/nebulous-certificate-authority/organizations/ordererOrganizations/neb.com/orderers/orderer.neb.com/msp/tlscacerts/tlsca.neb.com-cert.pem"

# Add Fabric binaries to PATH
export PATH=$PATH:/home/ubuntu/smart-contract-encapsulator/nebulous-fabric-network/bin



# --- Identity Management Function (Only one is needed now) ---
set_context() {
    local ORG_NAME=$1
    if [ "$ORG_NAME" == "ResourceProvider" ]; then
        export CORE_PEER_LOCALMSPID="ResourceProviderOrgMSP"
        export CORE_PEER_TLS_ENABLED=true
        export CORE_PEER_TLS_ROOTCERT_FILE="${BASE_DIR}/nebulous-certificate-authority/organizations/peerOrganizations/resourceproviderorg.neb.com/peers/peer0.resourceproviderorg.neb.com/tls/ca.crt"
        # Always use the Admin User's identity for all channel operations
        export CORE_PEER_MSPCONFIGPATH="${BASE_DIR}/nebulous-certificate-authority/organizations/peerOrganizations/resourceproviderorg.neb.com/users/Admin@resourceproviderorg.neb.com/msp"
        export CORE_PEER_ADDRESS=localhost:9051
    elif [ "$ORG_NAME" == "Broker" ]; then
        export CORE_PEER_LOCALMSPID="BrokerOrgMSP"
        export CORE_PEER_TLS_ENABLED=true
        export CORE_PEER_TLS_ROOTCERT_FILE="${BASE_DIR}/nebulous-certificate-authority/organizations/peerOrganizations/brokerorg.neb.com/peers/peer0.brokerorg.neb.com/tls/ca.crt"
        # Always use the Admin User's identity for all channel operations
        export CORE_PEER_MSPCONFIGPATH="${BASE_DIR}/nebulous-certificate-authority/organizations/peerOrganizations/brokerorg.neb.com/users/Admin@brokerorg.neb.com/msp"
        export CORE_PEER_ADDRESS=localhost:7051
    else
        echo "ERROR: Unknown organization '$ORG_NAME'" && exit 1
    fi
}

set_context ResourceProvider
peer channel list

set_context Broker
peer channel list