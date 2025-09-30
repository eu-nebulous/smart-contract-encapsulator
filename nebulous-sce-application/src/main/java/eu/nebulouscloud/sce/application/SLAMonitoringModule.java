package eu.nebulouscloud.sce.application;

import io.grpc.ManagedChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.GatewayException;

public class SLAMonitoringModule {

  private Gateway gateway;
  private Contract contract;
  private ManagedChannel grpcChannel;
  private ObjectMapper mapper;

  public SLAMonitoringModule() throws Exception {
    this.mapper = new ObjectMapper();

    BlockchainConnectionManager connectionManager = BlockchainConnectionManager.getInstance();
    this.contract = connectionManager.getContract();

  }

  public String getSLA(String slaName) throws Exception {
    System.out.println("▶ getSLA: " + slaName);

    try {
      byte[] result = contract.evaluateTransaction("getSLA", slaName);
      String resultString = new String(result, StandardCharsets.UTF_8);

      System.out.println("✔ getSLA result: " +
          mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(resultString)));

      return resultString;
    } catch (GatewayException e) {
      System.err.println("✗ getSLA failed: " + e.getMessage());
      throw new Exception("SLA retrieval failed: " + e.getMessage());
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
      System.err.println("Error closing SLA Monitoring Module: " + e.getMessage());
    }
  }
}