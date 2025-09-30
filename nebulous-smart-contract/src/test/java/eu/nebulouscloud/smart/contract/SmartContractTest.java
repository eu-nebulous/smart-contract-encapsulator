package eu.nebulouscloud.smart.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

class SmartContractTest {

    @Mock
    private Context ctx;

    @Mock
    private ChaincodeStub stub;

    private SlaManagement contract;
    private ObjectMapper objectMapper;

    private static final String SAMPLE_SLA_NAME = "test-sla";
    private static final String SAMPLE_TX_ID = "tx123456789";
    private static final Instant SAMPLE_TIMESTAMP = Instant.parse("2024-01-15T10:30:00Z");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        contract = new SlaManagement();
        objectMapper = new ObjectMapper();

        when(ctx.getStub()).thenReturn(stub);
        when(stub.getTxId()).thenReturn(SAMPLE_TX_ID);
        when(stub.getTxTimestamp()).thenReturn(SAMPLE_TIMESTAMP);
    }

    @Nested
    @DisplayName("SLA Creation Tests")
    class SlaCreationTests {

        @Test
        @DisplayName("Should create SLA successfully with valid input")
        void testCreateSLA_Success() throws Exception {
            // Given
            String slaDefinition = createValidSlaDefinition();
            when(stub.getState(SAMPLE_SLA_NAME)).thenReturn(new byte[0]);

            // When
            String result = contract.createSLA(ctx, SAMPLE_SLA_NAME, slaDefinition);

            // Then
            assertThat(result).isNotNull();
            JsonNode resultNode = objectMapper.readTree(result);

            // Check the structured response format
            assertThat(resultNode.get("success").asBoolean()).isTrue();
            assertThat(resultNode.get("message").asText()).isEqualTo("SLA created successfully");
            assertThat(resultNode.get("slaName").asText()).isEqualTo(SAMPLE_SLA_NAME);

            // Check the data section contains the SLA details
            JsonNode dataNode = resultNode.get("data");
            assertThat(dataNode).isNotNull();
            assertThat(dataNode.get("docType").asText()).isEqualTo("SLA");
            assertThat(dataNode.get("slaName").asText()).isEqualTo(SAMPLE_SLA_NAME);
            assertThat(dataNode.get("status").asText()).isEqualTo("ACTIVE");
            assertThat(dataNode.get("currentSl").asInt()).isEqualTo(1);

            verify(stub).putState(eq(SAMPLE_SLA_NAME), any(byte[].class));
            verify(stub).setEvent(eq("NewSLA"), any(byte[].class));
        }

        @Test
        @DisplayName("Should return error response when SLA already exists")
        void testCreateSLA_AlreadyExists() throws Exception {
            // Given
            String slaDefinition = createValidSlaDefinition();
            when(stub.getState(SAMPLE_SLA_NAME)).thenReturn("existing".getBytes());

            // When
            String result = contract.createSLA(ctx, SAMPLE_SLA_NAME, slaDefinition);

            // Then
            assertThat(result).isNotNull();
            JsonNode resultNode = objectMapper.readTree(result);

            // Check the error response format
            assertThat(resultNode.get("success").asBoolean()).isFalse();
            assertThat(resultNode.get("error").asText()).isEqualTo("SLA_ALREADY_EXISTS");
            assertThat(resultNode.get("message").asText()).isEqualTo("The SLA " + SAMPLE_SLA_NAME + " already exists");
            assertThat(resultNode.get("slaName").asText()).isEqualTo(SAMPLE_SLA_NAME);

            // Verify that no state was changed and no events were emitted
            verify(stub, never()).putState(any(), any(byte[].class));
            verify(stub, never()).setEvent(any(), any(byte[].class));
        }

        @Test
        @DisplayName("Should fail with invalid JSON")
        void testCreateSLA_InvalidJson() {
            // Given
            String invalidJson = "{ invalid json }";
            when(stub.getState(SAMPLE_SLA_NAME)).thenReturn(new byte[0]);

            // When & Then
            assertThatThrownBy(() -> contract.createSLA(ctx, SAMPLE_SLA_NAME, invalidJson))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to create SLA");
        }
    }

    @Nested
    @DisplayName("SLA Retrieval Tests")
    class SlaRetrievalTests {

        @Test
        @DisplayName("Should retrieve existing SLA")
        void testGetSLA_Success() {
            // Given
            String existingSla = "{\"docType\":\"SLA\",\"slaName\":\"test\"}";
            when(stub.getState(SAMPLE_SLA_NAME)).thenReturn(existingSla.getBytes());

            // When
            String result = contract.getSLA(ctx, SAMPLE_SLA_NAME);

            // Then
            assertThat(result).isEqualTo(existingSla);
        }

        @Test
        @DisplayName("Should fail when SLA does not exist")
        void testGetSLA_NotFound() {
            // Given
            when(stub.getState(SAMPLE_SLA_NAME)).thenReturn(new byte[0]);

            // When & Then
            assertThatThrownBy(() -> contract.getSLA(ctx, SAMPLE_SLA_NAME))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("does not exist");
        }

        @Test
        @DisplayName("Should get SLA status")
        void testGetSLAStatus_Success() throws Exception {
            // Given
            String slaJson = "{\"status\":\"ACTIVE\"}";
            when(stub.getState(SAMPLE_SLA_NAME)).thenReturn(slaJson.getBytes());

            // When
            String status = contract.getSLAstatus(ctx, SAMPLE_SLA_NAME);

            // Then
            assertThat(status).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("Should get current service level")
        void testGetCurrentSL_Success() throws Exception {
            // Given
            String slaJson = "{\"currentSl\":2}";
            when(stub.getState(SAMPLE_SLA_NAME)).thenReturn(slaJson.getBytes());

            // When
            int currentSl = contract.getCurrentSL(ctx, SAMPLE_SLA_NAME);

            // Then
            assertThat(currentSl).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("SLA Update Tests")
    class SlaUpdateTests {

        @Test
        @DisplayName("Should update SLA status successfully")
        void testUpdateSLA_Status() throws Exception {
            // Given
            String existingSla = createExistingSlaJson();
            String updateJson = "{\"status\":\"SUSPENDED\"}";
            when(stub.getState(SAMPLE_SLA_NAME)).thenReturn(existingSla.getBytes());

            // When
            String result = contract.updateSLA(ctx, SAMPLE_SLA_NAME, updateJson);

            // Then
            JsonNode resultNode = objectMapper.readTree(result);
            assertThat(resultNode.get("status").asText()).isEqualTo("SUSPENDED");
            verify(stub).putState(eq(SAMPLE_SLA_NAME), any(byte[].class));
            verify(stub).setEvent(eq("SLAUpdated"), any(byte[].class));
        }

        @Test
        @DisplayName("Should fail to update terminated SLA")
        void testUpdateSLA_Terminated() {
            // Given
            String terminatedSla = "{\"status\":\"TERMINATED\",\"slaName\":\"test\"}";
            String updateJson = "{\"status\":\"ACTIVE\"}";
            when(stub.getState(SAMPLE_SLA_NAME)).thenReturn(terminatedSla.getBytes());

            // When & Then
            assertThatThrownBy(() -> contract.updateSLA(ctx, SAMPLE_SLA_NAME, updateJson))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Cannot update a terminated SLA");
        }

        @Test
        @DisplayName("Should terminate SLA successfully")
        void testTerminateSLA_Success() throws Exception {
            // Given
            String existingSla = createExistingSlaJson();
            when(stub.getState(SAMPLE_SLA_NAME)).thenReturn(existingSla.getBytes());

            // When
            String result = contract.terminateSLA(ctx, SAMPLE_SLA_NAME);

            // Then
            JsonNode resultNode = objectMapper.readTree(result);
            assertThat(resultNode.get("status").asText()).isEqualTo("TERMINATED");
        }
    }

    @Nested
    @DisplayName("Violation Detection Tests")
    class ViolationDetectionTests {

        @Test
        @DisplayName("Should detect violation when metric exceeds threshold")
        void testCheckViolation_ViolationDetected() throws Exception {
            // Given
            String activeSla = createActiveSlaWithSimpleCondition();
            String eventData = createEventDataWithViolation();

            when(stub.getState(SAMPLE_SLA_NAME)).thenReturn(activeSla.getBytes());

            // When
            String result = contract.checkViolation(ctx, eventData);

            // Then
            JsonNode resultNode = objectMapper.readTree(result);
            assertThat(resultNode.get("violated").asBoolean()).isTrue();
            assertThat(resultNode.get("metric").asText()).isEqualTo("cpu_usage");
            verify(stub).putState(eq(SAMPLE_SLA_NAME), any(byte[].class));
            verify(stub).setEvent(eq("ViolationDetectedEvent"), any(byte[].class));
        }

        @Test
        @DisplayName("Should not detect violation when metrics are within bounds")
        void testCheckViolation_NoViolation() throws Exception {
            // Given
            String activeSla = createActiveSlaWithSimpleCondition();
            String eventData = createEventDataWithoutViolation();

            when(stub.getState(SAMPLE_SLA_NAME)).thenReturn(activeSla.getBytes());

            // When
            String result = contract.checkViolation(ctx, eventData);

            // Then
            JsonNode resultNode = objectMapper.readTree(result);
            assertThat(resultNode.get("violated").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("Should fail when SLA is not active")
        void testCheckViolation_SlaNotActive() {
            // Given
            String inactiveSla = "{\"status\":\"SUSPENDED\",\"slaName\":\"test\"}";
            String eventData = createEventDataWithViolation();

            when(stub.getState(SAMPLE_SLA_NAME)).thenReturn(inactiveSla.getBytes());

            // When & Then
            assertThatThrownBy(() -> contract.checkViolation(ctx, eventData))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("SLA not active");
        }

        @Test
        @DisplayName("Should handle complex AND/OR conditions")
        void testCheckViolation_ComplexConditions() throws Exception {
            // Given
            String slaWithComplexConditions = createSlaWithComplexConditions();
            String eventData = createEventDataForComplexConditions();

            when(stub.getState(SAMPLE_SLA_NAME)).thenReturn(slaWithComplexConditions.getBytes());

            // When
            String result = contract.checkViolation(ctx, eventData);

            // Then
            assertThat(result).isNotNull();
            JsonNode resultNode = objectMapper.readTree(result);
            assertThat(resultNode.get("violated")).isNotNull();
        }
    }

    @Nested
    @DisplayName("Transition Tests")
    class TransitionTests {

        @Test
        @DisplayName("Should apply transition when violation threshold is met")
        void testTransition_Applied() throws Exception {
            // Given
            String slaWithTransitions = createSlaWithTransitionsAndViolations();
            String eventData = createEventDataWithViolation();

            when(stub.getState(SAMPLE_SLA_NAME)).thenReturn(slaWithTransitions.getBytes());

            // When
            String result = contract.checkViolation(ctx, eventData);

            // Then
            verify(stub).setEvent(eq("TransitionEvent"), any(byte[].class));
        }
    }

    @Nested
    @DisplayName("Settlement Tests")
    class SettlementTests {

        @Test
        @DisplayName("Should apply settlement when settlement threshold is met")
        void testSettlement_Applied() throws Exception {
            // Given
            String slaWithSettlement = createSlaWithSettlementAndViolations();
            String eventData = createEventDataWithViolation();

            when(stub.getState(SAMPLE_SLA_NAME)).thenReturn(slaWithSettlement.getBytes());

            // When
            String result = contract.checkViolation(ctx, eventData);

            // Then
            verify(stub).setEvent(eq("SettlementEvent"), any(byte[].class));
        }
    }

    @Nested
    @DisplayName("Service Level Node Tests")
    class ServiceLevelNodeTests {

        @Test
        @DisplayName("Should retrieve specific service level node")
        void testGetSLnode_Success() throws Exception {
            // Given
            String slaWithSls = createSlaWithMultipleServiceLevels();
            when(stub.getState(SAMPLE_SLA_NAME)).thenReturn(slaWithSls.getBytes());

            // When
            String result = contract.getSLnode(ctx, SAMPLE_SLA_NAME, 2);

            // Then
            assertThat(result).isNotNull();
            JsonNode resultNode = objectMapper.readTree(result);
            assertThat(resultNode.get("slName").asInt()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should fail when service level not found")
        void testGetSLnode_NotFound() {
            // Given
            String slaWithSls = createSlaWithMultipleServiceLevels();
            when(stub.getState(SAMPLE_SLA_NAME)).thenReturn(slaWithSls.getBytes());

            // When & Then
            assertThatThrownBy(() -> contract.getSLnode(ctx, SAMPLE_SLA_NAME, 999))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Service level 999 not found");
        }
    }

    // Helper methods for creating test data
    private String createValidSlaDefinition() {
        return """
                {
                    "sls": [
                        {
                            "slName": 1,
                            "operator": "AND",
                            "operands": [
                                {
                                    "firstArgument": "cpu_usage",
                                    "operator": "LESS_EQUAL_THAN",
                                    "secondArgument": 80.0
                                }
                            ]
                        }
                    ],
                    "transitions": [
                        {
                            "firstSl": 1,
                            "secondSl": 2,
                            "evaluationPeriod": "PT1H",
                            "violationThreshold": 3
                        }
                    ],
                    "metrics": [
                        {
                            "name": "cpu_usage",
                            "window": {
                                "type": "sliding",
                                "unit": "minutes",
                                "value": 5
                            },
                            "output": null
                        }
                    ],
                    "settlement": {
                        "evaluationPeriod": "PT24H",
                        "settlementCount": 5,
                        "concernedSL": 2,
                        "settlementAction": "TERMINATED"
                    }
                }
                """;
    }

    private String createExistingSlaJson() {
        return """
                {
                    "docType": "SLA",
                    "slaName": "test-sla",
                    "status": "ACTIVE",
                    "currentSl": 1,
                    "violations": []
                }
                """;
    }

    private String createActiveSlaWithSimpleCondition() {
        return """
                {
                    "docType": "SLA",
                    "slaName": "test-sla",
                    "status": "ACTIVE",
                    "currentSl": 1,
                    "violations": [],
                    "sls": [
                        {
                            "slName": 1,
                            "operator": "AND",
                            "operands": [
                                {
                                    "firstArgument": "cpu_usage",
                                    "operator": "LESS_EQUAL_THAN",
                                    "secondArgument": 80.0
                                }
                            ]
                        }
                    ]
                }
                """;
    }

    private String createEventDataWithViolation() {
        return """
                {
                    "slaName": "test-sla",
                    "latestMetric": "cpu_usage",
                    "latestMetricTimestamp": "1705316400000",
                    "metrics": {
                        "cpu_usage": "85.0",
                        "memory_usage": "60.0"
                    }
                }
                """;
    }

    private String createEventDataWithoutViolation() {
        return """
                {
                    "slaName": "test-sla",
                    "latestMetric": "cpu_usage",
                    "latestMetricTimestamp": "1705316400000",
                    "metrics": {
                        "cpu_usage": "75.0",
                        "memory_usage": "60.0"
                    }
                }
                """;
    }

    private String createSlaWithComplexConditions() {
        return """
                {
                    "docType": "SLA",
                    "slaName": "test-sla",
                    "status": "ACTIVE",
                    "currentSl": 1,
                    "violations": [],
                    "sls": [
                        {
                            "slName": 1,
                            "operator": "OR",
                            "operands": [
                                {
                                    "operator": "AND",
                                    "operands": [
                                        {
                                            "firstArgument": "cpu_usage",
                                            "operator": "LESS_EQUAL_THAN",
                                            "secondArgument": 80.0
                                        },
                                        {
                                            "firstArgument": "memory_usage",
                                            "operator": "LESS_EQUAL_THAN",
                                            "secondArgument": 70.0
                                        }
                                    ]
                                }
                            ]
                        }
                    ]
                }
                """;
    }

    private String createEventDataForComplexConditions() {
        return """
                {
                    "slaName": "test-sla",
                    "latestMetric": "cpu_usage",
                    "latestMetricTimestamp": "1705316400000",
                    "metrics": {
                        "cpu_usage": "85.0",
                        "memory_usage": "75.0"
                    }
                }
                """;
    }

    private String createSlaWithTransitionsAndViolations() {
        long timestamp = System.currentTimeMillis();
        return String.format("""
                {
                    "docType": "SLA",
                    "slaName": "test-sla",
                    "status": "ACTIVE",
                    "currentSl": 1,
                    "violations": [
                        {
                            "violationId": "vio-1",
                            "latestMetricTimestamp": "%d",
                            "slName": 1
                        },
                        {
                            "violationId": "vio-2",
                            "latestMetricTimestamp": "%d",
                            "slName": 1
                        },
                        {
                            "violationId": "vio-3",
                            "latestMetricTimestamp": "%d",
                            "slName": 1
                        }
                    ],
                    "sls": [
                        {
                            "slName": 1,
                            "operator": "AND",
                            "operands": [
                                {
                                    "firstArgument": "cpu_usage",
                                    "operator": "LESS_EQUAL_THAN",
                                    "secondArgument": 80.0
                                }
                            ]
                        }
                    ],
                    "transitions": [
                        {
                            "firstSl": 1,
                            "secondSl": 2,
                            "evaluationPeriod": "PT1H",
                            "violationThreshold": 3
                        }
                    ]
                }
                """, timestamp - 1800000, timestamp - 1200000, timestamp - 600000);
    }

    private String createSlaWithSettlementAndViolations() {
        long timestamp = System.currentTimeMillis();
        return String.format("""
                {
                    "docType": "SLA",
                    "slaName": "test-sla",
                    "status": "ACTIVE",
                    "currentSl": 2,
                    "violations": [
                        {
                            "violationId": "vio-1",
                            "latestMetricTimestamp": "%d",
                            "slName": 2
                        },
                        {
                            "violationId": "vio-2",
                            "latestMetricTimestamp": "%d",
                            "slName": 2
                        },
                        {
                            "violationId": "vio-3",
                            "latestMetricTimestamp": "%d",
                            "slName": 2
                        },
                        {
                            "violationId": "vio-4",
                            "latestMetricTimestamp": "%d",
                            "slName": 2
                        },
                        {
                            "violationId": "vio-5",
                            "latestMetricTimestamp": "%d",
                            "slName": 2
                        }
                    ],
                    "sls": [
                        {
                            "slName": 2,
                            "operator": "AND",
                            "operands": [
                                {
                                    "firstArgument": "cpu_usage",
                                    "operator": "LESS_EQUAL_THAN",
                                    "secondArgument": 80.0
                                }
                            ]
                        }
                    ],
                    "settlement": {
                        "evaluationPeriod": "PT24H",
                        "settlementCount": 5,
                        "concernedSL": 2,
                        "settlementAction": "TERMINATED"
                    }
                }
                """, timestamp - 21600000, timestamp - 18000000, timestamp - 14400000, timestamp - 10800000,
                timestamp - 7200000);
    }

    private String createSlaWithMultipleServiceLevels() {
        return """
                {
                    "docType": "SLA",
                    "slaName": "test-sla",
                    "status": "ACTIVE",
                    "currentSl": 1,
                    "violations": [],
                    "sls": [
                        {
                            "slName": 1,
                            "operator": "AND",
                            "operands": [
                                {
                                    "firstArgument": "cpu_usage",
                                    "operator": "LESS_EQUAL_THAN",
                                    "secondArgument": 80.0
                                }
                            ]
                        },
                        {
                            "slName": 2,
                            "operator": "AND",
                            "operands": [
                                {
                                    "firstArgument": "cpu_usage",
                                    "operator": "LESS_EQUAL_THAN",
                                    "secondArgument": 90.0
                                }
                            ]
                        }
                    ]
                }
                """;
    }
}