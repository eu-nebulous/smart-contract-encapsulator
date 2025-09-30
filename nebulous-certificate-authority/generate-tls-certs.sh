#!/bin/bash

# Generate TLS certificates for all CAs
for ca_dir in ordererOrg org1-resourceprovider org2-broker; do
  case $ca_dir in
    ordererOrg) cn="ca-orderer" ;;
    org1-resourceprovider) cn="ca-org1-resourceprovider" ;;
    org2-broker) cn="ca-org2-broker" ;;
  esac
  
  echo "Generating TLS certificates for $cn..."
  
  cd organizations/fabric-ca/$ca_dir
  
  # Generate private key
  openssl ecparam -genkey -name prime256v1 -noout -out tls-key.pem
  
  # Create a temporary config for this CA
  cat > temp-csr.conf << EOF
[ req ]
default_bits = 2048
distinguished_name = dn
req_extensions = v3_req
prompt = no
default_md = sha256

[ dn ]
C = US
ST = State
L = City
O = MyOrg
OU = CA
CN = $cn

[ v3_req ]
basicConstraints = CA:TRUE
keyUsage = digitalSignature, keyEncipherment, keyCertSign
extendedKeyUsage = serverAuth, clientAuth
subjectAltName = @alt_names

[ alt_names ]
DNS.1 = $cn
DNS.2 = localhost
IP.1 = 127.0.0.1
EOF
  
  # Generate self-signed certificate with CA extensions
  openssl req -new -x509 -key tls-key.pem -out tls-cert.pem -days 365 \
    -config temp-csr.conf -subj "/C=US/ST=State/L=City/O=MyOrg/OU=CA/CN=$cn" \
    -extensions v3_req
  
  # Clean up
  rm temp-csr.conf
  
  echo "Generated TLS certificates for $cn"
  cd ../../..
done

echo "All TLS certificates generated successfully!"