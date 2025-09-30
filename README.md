# Smart Contract Encapsulator (SCE)

The **Smart Contract Encapsulator (SCE)** is a core component of the NebulOuS SLA lifecycle.  
It consumes SLAs produced by the SLA Generator, converts them into deterministic smart contract states, and stores them on a **Hyperledger Fabric ledger** via the Fabric Gateway (FGW).  

Additionally, the SCE ingests real-time monitoring events, evaluates SLA conditions, detects violations, triggers state transitions and settlements, and publishes events back to AMQP message topics for system-wide consumption.


## Features

### Deterministic & Tamper-Proof Contracts
Every SLA is transformed into a **deterministic smart contract state**, ensuring that all endorsing peers produce identical results. This guarantees **immutability, auditability, and trust** in SLA commitments.

### Real-Time Violation Detection
The SCE continuously consumes monitoring data streams and performs **on-chain evaluation of metrics** against SLA rules.  
Violations are detected **instantly**, minimizing the gap between SLA breaches and remediation.

### Automated Lifecycle Transitions
SLAs evolve dynamically as metrics change.  
The SCE applies **state transitions** (e.g., SLA moving from sl 1 → sl 2 → sl 3) and triggers **settlements automatically**.

### Event-Driven Integration
The SCE emits structured **Fabric events** (`Violation`, `Transition`, `Settlement`) and republishes them to **AMQP topics**.  
This ensures that SLA status updates are **instantly available** to the entire NebulOuS ecosystem.



## Key Components
- **Certificate Authority**

    Manages digital identities and cryptographic materials (MSP, TLS certificates) for all network participants.

- **Fabric Network**

    Consists of an ordering service and peer nodes from different organizations.

- **Smart Contract (Chaincode)**

    Java-based chaincode that manages SLA lifecycle, violations, transitions, and settlements on the ledger.

- **SCE Application**

    Client application that connects to the Fabric network, validates SLA JSON, processes monitoring events, and publishes Fabric events to AMQP topics.




# Installation & Setup

## Prerequisites
Before setting up the SCE, ensure the following are installed:

- Docker (20.10+)
- Docker Compose (1.29.2+)
- Java JDK (11+)
- Maven (3.6+)
- Hyperledger Fabric binaries and Docker images (v2.5.5)

## Certificate Authority Setup

Before running the application, you need to create the required certificates for each organization and place them in the appropriate wallet directories.



### Enroll Organizations and Generate Certificates
Use the following Docker commands to enroll each organization and generate the necessary certificates.

```
docker service create --name enroll-<ORG_NAME> \
  --network fabric-ca_fabric-ca \
  --secret <CA_BOOTSTRAP_PW_SECRET> \
  --secret postgres_password \
  --mount type=bind,src=/home/ubuntu/smart-contract-encapsulator/nebulous-certificate-authority,dst=/workspace \
  -w /workspace/scripts \
  -e ROOT_DIR=/workspace \
  -e CA_SERVICE_NAME=<CA_SERVICE_NAME> \
  -e CA_PORT=<CA_PORT> \
  -e FABRIC_LOGGING_SPEC=debug \
  --replicas 1 \
  --restart-condition none \
  --constraint 'node.role == manager' \
  local/fabric-ca-client:alpine \
  sh -c "fabric-ca-client version && bash <ENROLL_SCRIPT>.sh"
  ```

### Create Wallet Directories and Place Certificates

After enrolling each organization, you need to organize the generated certificates and private keys into your application’s wallet structure.
Create the necessary directories:
```
# Identity certificates and keys for each user
mkdir -p wallet/<USER>@<ORG>/msp/signcerts
mkdir -p wallet/<USER>@<ORG>/msp/keystore

# TLS certificates
mkdir -p wallet/certs
```

Place the certificates in the appropriate folders:

- User identity certificate → wallet/<USER>@<ORG>/msp/signcerts/cert.pem
- User private key → wallet/<USER>@<ORG>/msp/keystore/ (keep the original file name)
- TLS CA certificates → wallet/certs/ (rename as needed for each peer/orderer, e.g., tlsca.<org>-cert.pem or peer0.<org>.tls.ca.crt)


# Build the application
```
mvn clean package
```

# Build Docker image
```
docker build -t nebulous-sce-application:latest .
```

## Running the Application
Start the SCE application with the following command:

```
docker run --rm --network host \
  -v /home/ubuntu/smart-contract-encapsulator/nebulous-sce-application/wallet:/usr/local/lib/wallet:ro \
  nebulous-sce-application:latest \
  --activemq-host localhost \
  --activemq-port 5672 \
  --activemq-user admin \
  --activemq-password admin
```
## Application Parameters

--activemq-host: ActiveMQ broker hostname

--activemq-port: ActiveMQ broker port (default: 5672)

--activemq-user: ActiveMQ username

--activemq-password: ActiveMQ password

