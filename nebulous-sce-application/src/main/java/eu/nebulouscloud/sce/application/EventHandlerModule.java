package eu.nebulouscloud.sce.application;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Network;
import org.hyperledger.fabric.client.Contract;

import com.fasterxml.jackson.databind.node.ObjectNode;

// Event handling imports for Client SDK
import org.hyperledger.fabric.client.ChaincodeEvent;
import org.hyperledger.fabric.client.ChaincodeEventsRequest;
import org.hyperledger.fabric.client.CloseableIterator;

public class EventHandlerModule {
  private static final String CHAINCODE = "nebulous-smart-contract";

  private BlockchainConnectionManager connectionManager;
  private Gateway gateway;
  private Contract contract;
  private Network network;
  private ManagedChannel grpcChannel;
  private ObjectMapper mapper;

  // Event listener management
  private CloseableIterator<ChaincodeEvent> eventIterator;
  private CompletableFuture<Void> eventListenerTask;
  private volatile boolean isListening = false;

  private ExnConnector exnConnector;

  // Map to store events
  private final Map<String, Map<String, Object>> eventStore = new HashMap<>();

  public EventHandlerModule() throws Exception {
    this.mapper = new ObjectMapper();

    this.connectionManager = BlockchainConnectionManager.getInstance();
    this.contract = connectionManager.getContract();
    this.network = connectionManager.getNetwork();
    // initializeBlockchainConnection();
  }

  // Setter to inject ExnConnector after construction
  public void setExnConnector(ExnConnector exnConnector) {
    this.exnConnector = exnConnector;

    System.out.println("✅ Event Handler Module initialized with blockchain connection");

    startEventListener();
  }

  private void startEventListener() {
    try {
      // Create chaincode events request
      ChaincodeEventsRequest request = network.newChaincodeEventsRequest(CHAINCODE)
          // .startBlock(network.getBlockHeight()) // Start from current block
          .build();

      // Get iterator for chaincode events
      eventIterator = request.getEvents();
      isListening = true;

      // Start async event processing
      eventListenerTask = CompletableFuture.runAsync(() -> {
        try {
          System.out.println("🔄 Starting event listener...");

          while (isListening && eventIterator.hasNext()) {
            try {
              ChaincodeEvent event = eventIterator.next();
              handleChaincodeEvent(event);
            } catch (Exception e) {
              System.err.println("❌ Error processing individual event: " + e.getMessage());
              // Continue processing other events
            }
          }
        } catch (Exception e) {
          System.err.println("❌ Event listener error: " + e.getMessage());
        } finally {
          System.out.println("🛑 Event listener stopped");
        }
      });

      System.out.println("✅ Event listener started successfully");

    } catch (Exception e) {
      System.err.println("❌ Failed to start event listener: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void handleChaincodeEvent(ChaincodeEvent contractEvent) {
    try {
      String eventName = contractEvent.getEventName();
      // String txId = contractEvent.getTransactionId();
      byte[] payload = contractEvent.getPayload();

      if (payload != null && payload.length > 0) {
        String payloadJson = new String(payload, StandardCharsets.UTF_8);

        // Parse JSON payload
        JsonNode eventData = mapper.readTree(payloadJson);

        // Handle different event types
        String appID = eventData.get("slaName").asText();

        switch (eventName) {
          case "ViolationDetectedEvent":
            System.out.println("⚠️ ViolationDetectedEvent " + eventData.get("slaName").asText() + " tx="
                + contractEvent.getTransactionId()
                + " payload=" + payloadJson);

            ObjectNode violation_event = (ObjectNode) eventData;
            if (exnConnector != null) {
              exnConnector.sendViolationEvent(appID, violation_event);
            } else {
              System.err.println("❌ ExnConnector not initialized yet");
            }
            break;

          case "TransitionEvent":
            System.out.println(
                "TransitionEvent " + eventData.get("slaName").asText() + " tx=" + contractEvent.getTransactionId()
                    + " payload=" + payloadJson);

            ObjectNode transition_event = (ObjectNode) eventData;
            if (exnConnector != null) {
              exnConnector.sendTransitionEvent(appID, transition_event);
            } else {
              System.err.println("❌ ExnConnector not initialized yet");
            }
            break;

          case "SettlementEvent":
            System.out.println(
                "SettlementEvent " + eventData.get("slaName").asText() + " tx=" + contractEvent.getTransactionId()
                    + " payload=" + payloadJson);

            ObjectNode settlement_event = (ObjectNode) eventData;
            if (exnConnector != null) {
              exnConnector.sendSettlementEvent(appID, settlement_event);
            } else {
              System.err.println("❌ ExnConnector not initialized yet");
            }
            break;

          default:
            // logger.warning("⚠️ Unknown event type: " + eventName);
        }

      } else {
        System.err.println("⚠️ Event " + eventName + " has no payload");
      }

    } catch (Exception e) {
      System.err.println("❌ Error handling contract event" + e.getMessage());
      // Continue processing other events even if one fails
    }
  }

  public String MainFunction(String event_message) throws Exception {
    System.out.println("▶ MainFunction: Processing event.. ");
    final ObjectMapper objectMapper = new ObjectMapper();
    String slaName = null;
    String eventsJson = null;
    try {

      // String eventJsn = message.substring(6).trim();
      slaName = recordEvent(event_message);

      // Get the complete event map for this SLA
      Map<String, Object> slaEvents = eventStore.get(slaName);

      // Convert the map to JSON string
      eventsJson = objectMapper.writeValueAsString(slaEvents);

      System.out.println(eventsJson);

      contract.submitTransaction("checkViolation", eventsJson);

      return slaName;

    }
    catch (Exception e) {
      System.err.println("✗ ✗ Event processing failed:");
      System.err.println("   Exception Type: " + e.getClass().getName());
      System.err.println("   Exception Message: " + e.getMessage());
      System.err.println("   Input event_message: " + event_message);
      System.err.println("   SLA Name: " + (slaName != null ? slaName : "not determined"));
      System.err.println("   Generated eventsJson: " + (eventsJson != null ? eventsJson : "not generated"));
      
      // Log cause if available
      if (e.getCause() != null) {
        System.err.println("   Cause: " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
        if (e.getCause().getCause() != null) {
          System.err.println("   Root Cause: " + e.getCause().getCause().getClass().getName() + ": " + e.getCause().getCause().getMessage());
        }
      }
      
      // For StatusRuntimeException, log additional details
      if (e instanceof StatusRuntimeException) {
        StatusRuntimeException sre = (StatusRuntimeException) e;
        System.err.println("   gRPC Status Code: " + sre.getStatus().getCode());
        System.err.println("   gRPC Status Description: " + sre.getStatus().getDescription());
        System.err.println("   gRPC Status: " + sre.getStatus().toString());
      }
      
      // Log full stack trace
      System.err.println("   Full Stack Trace:");
      e.printStackTrace();
      
      throw new Exception("Event processing failed - invalid JSON: " + e.getMessage(), e);
    }
  }

  private String recordEvent(String event_message) throws Exception {
    final ObjectMapper objectMapper = new ObjectMapper();
    try {
      // Parse JSON string to JsonNode
      JsonNode jsonData = objectMapper.readTree(event_message);

      String timestamp = jsonData.get("timestamp").asText();
      // Extract slaName
      String slaName = jsonData.has("slaName") ? jsonData.get("slaName").asText() : "unknown";

      // Convert JSON to Map
      Map<String, Object> eventData = objectMapper.convertValue(jsonData, Map.class);

      // Get or create the existing map for this slaName
      Map<String, Object> existingEvent = eventStore.computeIfAbsent(slaName, k -> new HashMap<>());

      // Update or add slaName to the existing map
      existingEvent.put("slaName", slaName);

      // Merge metrics from the new event into the existing map
      if (eventData.containsKey("metric")) {
        @SuppressWarnings("unchecked")
        Map<String, Object> newMetric = (Map<String, Object>) eventData.get("metric");
        @SuppressWarnings("unchecked")
        Map<String, Object> existingMetrics = (Map<String, Object>) existingEvent.computeIfAbsent("metrics",
            k -> new HashMap<>());

        // Since we're only receiving one metric at a time, we get its key directly
        String metricKey = newMetric.keySet().iterator().next();
        Object metricValue = newMetric.get(metricKey);

        // Update the metric in the existing metrics
        existingMetrics.put(metricKey, metricValue);

        // Store the key of the latest metric
        existingEvent.put("latestMetric", metricKey);

        existingEvent.put("latestMetricTimestamp", timestamp);
      }

      System.out.println("✔ Event recorded for slaName: " + slaName);
      return slaName;
    } catch (Exception e) {
      System.err.println("✗ recordEvent JSON parsing failed: " + e.getMessage());
      throw new Exception("Event recording failed: " + e.getMessage());
    }
  }

  // function to print the map for a given slaName
  public void printEventMap(String slaName) {
    Map<String, Object> eventMap = eventStore.get(slaName);
    if (eventMap == null) {
      System.out.println("✗ No event found for slaName: " + slaName);
    } else {
      System.out.println("✔ Event map for slaName: " + slaName);
      System.out.println(eventMap.toString());
    }
  }

  public void close() {
    try {
      if (gateway != null) {
        gateway.close();
      }
      if (grpcChannel != null) {
        grpcChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
      }
    } catch (Exception e) {
      System.err.println("Error closing Event Handler Module: " + e.getMessage());
    }
  }
}