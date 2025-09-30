# Smart Contract Encapsulator (SCE)

The **Smart Contract Encapsulator (SCE)** is a core component of the NebulOuS SLA lifecycle.  
It consumes SLAs produced by the SLA Generator, converts them into deterministic smart contract states, and stores them on a **Hyperledger Fabric ledger** via the Fabric Gateway (FGW).  

Additionally, the SCE ingests real-time monitoring events, evaluates SLA conditions, detects violations, triggers state transitions and settlements, and publishes events back to AMQP message topics for system-wide consumption.

---

## Features

### Deterministic & Tamper-Proof Contracts
Every SLA is transformed into a **deterministic smart contract state**, ensuring that all endorsing peers produce identical results.  
This guarantees **immutability, auditability, and trust** in SLA commitments.

### Real-Time Violation Detection
The SCE continuously consumes monitoring data streams and performs **on-chain evaluation of metrics** against SLA rules.  
Violations are detected **instantly**, minimizing the gap between SLA breaches and remediation.

### Automated Lifecycle Transitions
SLAs evolve dynamically as metrics change.  
The SCE applies **state transitions** (e.g., SLA moving from sl 1 → sl 2 → sl 3) and triggers **settlements automatically**.

### Event-Driven Integration
The SCE emits structured **Fabric events** (`Violation`, `Transition`, `Settlement`) and republishes them to **AMQP topics**.  
This ensures that SLA status updates are **instantly available** to the entire NebulOuS ecosystem.
