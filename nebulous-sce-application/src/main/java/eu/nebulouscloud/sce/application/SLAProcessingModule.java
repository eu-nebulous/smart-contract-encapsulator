package eu.nebulouscloud.sce.application;

import io.grpc.ManagedChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.GatewayException;

public class SLAProcessingModule {

  private Gateway gateway;
  private Contract contract;
  private ManagedChannel grpcChannel;
  private ObjectMapper mapper;

  public SLAProcessingModule() throws Exception {
    this.mapper = new ObjectMapper();

    BlockchainConnectionManager connectionManager = BlockchainConnectionManager.getInstance();
    this.contract = connectionManager.getContract();

  }

  public String createSLA(JsonNode jsonData) throws Exception {
    System.out.println("▶ createSLA: Processing SLA data");
    final ObjectMapper objectMapper = new ObjectMapper();
    SlaJsonValidator validator = new SlaJsonValidator();

    try {

      // Validate structure and convert data types
      JsonNode convertedJson = validator.validateAndConvertSlaJson(jsonData);

      // Write the JsonNode to a string with indentation
      String compactJson = objectMapper.writeValueAsString(convertedJson);

      String slaName = convertedJson.has("slaName") ? convertedJson.get("slaName").asText() : "unknown";

      System.out.println("▶ createSLA: Creating SLA with name: " + slaName);

      // Call the blockchain createSLA function with slaName and full slaData
      byte[] result = contract.submitTransaction("createSLA", slaName, compactJson);
      String resultString = new String(result, StandardCharsets.UTF_8);

      // Parse the response
      JsonNode response = objectMapper.readTree(resultString);
      
      if (response.get("success").asBoolean()) {
          System.out.println("✓ SLA created successfully: " + slaName);
          return resultString;
      } else {
          String errorCode = response.get("error").asText();
          String message = response.get("message").asText();
          
          if ("SLA_ALREADY_EXISTS".equals(errorCode)) {
              System.out.println("✗ SLA already exists: " + slaName);
              // Handle gracefully - maybe return existing SLA or skip
          } else {
              System.out.println("✗ SLA creation failed: " + message);
          }
          return resultString;
        }

    } catch (GatewayException e) {
      System.err.println("✗ createSLA failed: " + e.getMessage());
      throw new Exception("SLA creation failed: " + e.getMessage());
    } catch (Exception e) {
      System.err.println("✗ createSLA JSON parsing failed: " + e.getMessage());
      throw new Exception("SLA creation failed - invalid JSON: " + e.getMessage());
    }
  }

  public String updateSLA(String slaData) throws Exception {
    System.out.println("▶ updateSLA: Processing SLA data");
    final ObjectMapper objectMapper = new ObjectMapper();
    SlaJsonValidator validator = new SlaJsonValidator();

    try {
      // Parse the JSON
      JsonNode jsonData = objectMapper.readTree(slaData);

      // Validate structure and convert data types
      JsonNode convertedJson = validator.validateAndConvertSlaJson(jsonData);

      String compactJson = objectMapper.writeValueAsString(convertedJson);

      String slaName = convertedJson.has("slaName") ? convertedJson.get("slaName").asText() : "unknown";

      // String slaName = jsonData.get("slaName").asText();

      System.out.println("▶ updateSLA: Updating SLA with name: " + slaName);

      // Call the blockchain createSLA function with slaName and full slaData
      byte[] result = contract.submitTransaction("updateSLA", slaName, compactJson);
      String resultString = new String(result, StandardCharsets.UTF_8);

      System.out.println("✔ updateSLA result: " +
          mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(resultString)));

      return resultString;
    } catch (GatewayException e) {
      System.err.println("✗ updateSLA failed: " + e.getMessage());
      throw new Exception("SLA update failed: " + e.getMessage());
    } catch (Exception e) {
      System.err.println("✗ updateSLA JSON parsing failed: " + e.getMessage());
      throw new Exception("SLA update failed - invalid JSON: " + e.getMessage());
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
      System.err.println("Error closing SLA Processing Module: " + e.getMessage());
    }
  }
}