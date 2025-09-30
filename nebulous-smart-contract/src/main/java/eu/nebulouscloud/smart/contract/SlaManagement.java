package eu.nebulouscloud.smart.contract;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.*;
import org.hyperledger.fabric.shim.ChaincodeStub;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Contract(name = "SlaManagement", info = @Info(title = "SLA Management", description = "Smart Contract for managing NebulOuS SLAs", version = "1.0.0"))
@Default
public final class SlaManagement implements ContractInterface {

    // Ultra-deterministic ObjectMapper configuration
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(SerializationFeature.WRITE_ENUMS_USING_TO_STRING, true)
            .configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false)
            .configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, false)
            .configure(DeserializationFeature.USE_LONG_FOR_INTS, false);

    /**
     * Creates a new SLA with complete determinism
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String createSLA(final Context ctx, final String slaName, final String slaDefinitionString) {
        ChaincodeStub stub = ctx.getStub();

        if (slaExists(ctx, slaName)) {
            // Return structured error response instead of throwing exception
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "SLA_ALREADY_EXISTS");
            errorResponse.put("message", "The SLA " + slaName + " already exists");
            errorResponse.put("slaName", slaName);

            try {
                return objectMapper.writeValueAsString(errorResponse);
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize error response: " + e.getMessage());
            }
        }

        try {
            // Parse input with deterministic ordering
            JsonNode inputNode = objectMapper.readTree(slaDefinitionString);

            // Create deterministic SLA structure
            Map<String, Object> slaMap = new LinkedHashMap<>();
            slaMap.put("docType", "SLA");
            slaMap.put("slaName", slaName);
            slaMap.put("status", "ACTIVE");
            slaMap.put("currentSl", 1);
            slaMap.put("violations", new ArrayList<>());

            // Process components in deterministic order
            if (inputNode.has("sls")) {
                slaMap.put("sls", processSls(inputNode.get("sls")));
            }

            if (inputNode.has("transitions")) {
                slaMap.put("transitions", processTransitions(inputNode.get("transitions")));
            }

            if (inputNode.has("metrics")) {
                slaMap.put("metrics", processMetrics(inputNode.get("metrics")));
            }

            if (inputNode.has("settlement")) {
                slaMap.put("settlement", processSettlement(inputNode.get("settlement")));
            }

            // Return success response
            Map<String, Object> successResponse = new LinkedHashMap<>();
            successResponse.put("success", true);
            successResponse.put("message", "SLA created successfully");
            successResponse.put("slaName", slaName);
            successResponse.put("data", slaMap);

            // Convert to deterministic JSON string
            String deterministicJson = objectMapper.writeValueAsString(successResponse);

            // Store on ledger
            stub.putState(slaName, deterministicJson.getBytes(StandardCharsets.UTF_8));
            stub.setEvent("NewSLA", deterministicJson.getBytes(StandardCharsets.UTF_8));

            return deterministicJson;

        } catch (Exception e) {
            throw new RuntimeException("Failed to create SLA: " + e.getMessage());
        }
    }

    /**
     * Process SLS with deterministic ordering
     */
    private List<Map<String, Object>> processSls(JsonNode slsNode) {
        List<Map<String, Object>> slsList = new ArrayList<>();

        for (JsonNode slNode : slsNode) {
            Map<String, Object> sl = new LinkedHashMap<>();
            sl.put("slName", slNode.get("slName").asInt());
            sl.put("operator", slNode.get("operator").asText());

            if (slNode.has("operands")) {
                sl.put("operands", processOperands(slNode.get("operands")));
            }

            slsList.add(sl);
        }

        // Sort by slName for determinism
        slsList.sort((a, b) -> Integer.compare((Integer) a.get("slName"), (Integer) b.get("slName")));
        return slsList;
    }

    /**
     * Process operands with deterministic ordering
     */
    private List<Map<String, Object>> processOperands(JsonNode operandsNode) {
        List<Map<String, Object>> operandsList = new ArrayList<>();

        for (JsonNode operandNode : operandsNode) {
            Map<String, Object> operand = new LinkedHashMap<>();

            // Process fields in alphabetical order for determinism
            if (operandNode.has("firstArgument")) {
                operand.put("firstArgument", operandNode.get("firstArgument").asText());
            }
            if (operandNode.has("operator")) {
                operand.put("operator", operandNode.get("operator").asText());
            }
            if (operandNode.has("secondArgument")) {
                operand.put("secondArgument", operandNode.get("secondArgument").asDouble());
            }
            if (operandNode.has("operands")) {
                operand.put("operands", processOperands(operandNode.get("operands")));
            }

            operandsList.add(operand);
        }

        return operandsList;
    }

    /**
     * Process transitions with deterministic ordering
     */
    private List<Map<String, Object>> processTransitions(JsonNode transitionsNode) {
        List<Map<String, Object>> transitionsList = new ArrayList<>();

        for (JsonNode transitionNode : transitionsNode) {
            Map<String, Object> transition = new LinkedHashMap<>();
            transition.put("firstSl", transitionNode.get("firstSl").asInt());
            transition.put("secondSl", transitionNode.get("secondSl").asInt());
            transition.put("evaluationPeriod", transitionNode.get("evaluationPeriod").asText());
            transition.put("violationThreshold", transitionNode.get("violationThreshold").asInt());

            transitionsList.add(transition);
        }

        return transitionsList;
    }

    /**
     * Process metrics with deterministic ordering
     */
    private List<Map<String, Object>> processMetrics(JsonNode metricsNode) {
        List<Map<String, Object>> metricsList = new ArrayList<>();

        for (JsonNode metricNode : metricsNode) {
            Map<String, Object> metric = new LinkedHashMap<>();
            metric.put("name", metricNode.get("name").asText());

            if (metricNode.has("window")) {
                JsonNode windowNode = metricNode.get("window");
                if (windowNode != null && !windowNode.isNull()) {
                    Map<String, Object> window = new LinkedHashMap<>();
                    window.put("type", windowNode.get("type").asText());
                    window.put("unit", windowNode.get("unit").asText());
                    window.put("value", windowNode.get("value").asInt());
                    metric.put("window", window);
                } else {
                    // Explicitly set to null if the JSON has "window": null
                    metric.put("window", null);
                }
            }

            if (metricNode.has("output")) {
                JsonNode outputNode = metricNode.get("output");
                if (outputNode != null && !outputNode.isNull()) {
                    Map<String, Object> output = new LinkedHashMap<>();
                    output.put("type", outputNode.get("type").asText());
                    output.put("unit", outputNode.get("unit").asText());
                    output.put("value", outputNode.get("value").asInt());
                    metric.put("output", output);
                } else {
                    // Explicitly set to null if the JSON has "output": null
                    metric.put("output", null);
                }
            }

            metricsList.add(metric);
        }

        // Sort by name for determinism
        metricsList.sort((a, b) -> ((String) a.get("name")).compareTo((String) b.get("name")));
        return metricsList;
    }

    /**
     * Process settlement with deterministic ordering
     */
    private Map<String, Object> processSettlement(JsonNode settlementNode) {
        Map<String, Object> settlement = new LinkedHashMap<>();
        settlement.put("evaluationPeriod", settlementNode.get("evaluationPeriod").asText());
        settlement.put("settlementCount", settlementNode.get("settlementCount").asInt());
        settlement.put("concernedSL", settlementNode.get("concernedSL").asInt());
        settlement.put("settlementAction", settlementNode.get("settlementAction").asText());
        return settlement;
    }

    /**
     * Create deterministic JSON string
     */
    private String createDeterministicJson(Map<String, Object> dataMap) throws JsonProcessingException {
        // Use LinkedHashMap to maintain insertion order
        Map<String, Object> orderedMap = new LinkedHashMap<>();

        // Add fields in specific order for determinism
        String[] fieldOrder = { "docType", "slaName", "status", "currentSl", "violations", "sls", "transitions",
                "metrics", "settlement" };

        for (String field : fieldOrder) {
            if (dataMap.containsKey(field)) {
                orderedMap.put(field, dataMap.get(field));
            }
        }

        return objectMapper.writeValueAsString(orderedMap);
    }

    /**
     * Retrieves an existing SLA
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getSLA(final Context ctx, final String slaName) {
        ChaincodeStub stub = ctx.getStub();

        byte[] slaBytes = stub.getState(slaName);
        if (slaBytes == null || slaBytes.length == 0) {
            throw new RuntimeException("The SLA " + slaName + " does not exist");
        }

        return new String(slaBytes, StandardCharsets.UTF_8);
    }

    /**
     * Gets the status of an SLA
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getSLAstatus(final Context ctx, final String slaName) {
        try {
            String slaJson = getSLA(ctx, slaName);
            JsonNode slaNode = objectMapper.readTree(slaJson);
            return slaNode.get("status").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get SLA status: " + e.getMessage());
        }
    }

    /**
     * Gets the current service level of an SLA
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public int getCurrentSL(final Context ctx, final String slaName) {
        try {
            String slaJson = getSLA(ctx, slaName);
            JsonNode slaNode = objectMapper.readTree(slaJson);
            return slaNode.get("currentSl").asInt();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get current SL: " + e.getMessage());
        }
    }

    /**
     * Gets the current service level node for an SLA
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getSLnode(final Context ctx, final String slaName, final int slName) {
        try {
            String slaJson = getSLA(ctx, slaName);
            JsonNode slaNode = objectMapper.readTree(slaJson);
            JsonNode slsNode = slaNode.get("sls");

            if (slsNode == null || !slsNode.isArray()) {
                throw new RuntimeException("The SLA " + slaName + " has no service levels defined");
            }

            for (JsonNode slNode : slsNode) {
                if (slNode.get("slName").asInt() == slName) {
                    return objectMapper.writeValueAsString(slNode);
                }
            }

            throw new RuntimeException("Service level " + slName + " not found in SLA " + slaName);

        } catch (Exception e) {
            throw new RuntimeException("Failed to get SL node: " + e.getMessage());
        }
    }

    /**
     * Updates an existing SLA - updates any parts provided in the update string
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String updateSLA(final Context ctx, final String slaName, final String slaUpdateString) {
        try {
            ChaincodeStub stub = ctx.getStub();
            String currentSlaJson = getSLA(ctx, slaName);
            JsonNode currentSlaNode = objectMapper.readTree(currentSlaJson);

            if ("TERMINATED".equals(currentSlaNode.get("status").asText())) {
                throw new RuntimeException("Cannot update a terminated SLA: " + slaName);
            }

            JsonNode updateNode = objectMapper.readTree(slaUpdateString);

            // Start with existing SLA data
            Map<String, Object> updatedSlaMap = new LinkedHashMap<>();
            updatedSlaMap.put("docType", "SLA");
            updatedSlaMap.put("slaName", slaName);

            // Update status if provided, otherwise keep existing
            updatedSlaMap.put("status", updateNode.has("status") ? updateNode.get("status").asText()
                    : currentSlaNode.get("status").asText());

            // Update currentSl if provided, otherwise keep existing
            updatedSlaMap.put("currentSl", updateNode.has("currentSl") ? updateNode.get("currentSl").asInt()
                    : currentSlaNode.get("currentSl").asInt());

            // Always preserve existing violations - violations cannot be updated by users
            JsonNode existingViolations = currentSlaNode.get("violations");
            if (existingViolations != null && existingViolations.isArray()) {
                List<Map<String, Object>> violationsList = new ArrayList<>();
                for (JsonNode vNode : existingViolations) {
                    Map<String, Object> violation = objectMapper.convertValue(vNode, Map.class);
                    violationsList.add(violation);
                }
                updatedSlaMap.put("violations", violationsList);
            } else {
                updatedSlaMap.put("violations", new ArrayList<>());
            }

            // Update sls if provided, otherwise preserve existing
            if (updateNode.has("sls")) {
                updatedSlaMap.put("sls", processSls(updateNode.get("sls")));
            } else if (currentSlaNode.has("sls")) {
                updatedSlaMap.put("sls", processSls(currentSlaNode.get("sls")));
            }

            // Update transitions if provided, otherwise preserve existing
            if (updateNode.has("transitions")) {
                updatedSlaMap.put("transitions", processTransitions(updateNode.get("transitions")));
            } else if (currentSlaNode.has("transitions")) {
                updatedSlaMap.put("transitions", processTransitions(currentSlaNode.get("transitions")));
            }

            // Update metrics if provided, otherwise preserve existing
            if (updateNode.has("metrics")) {
                updatedSlaMap.put("metrics", processMetrics(updateNode.get("metrics")));
            } else if (currentSlaNode.has("metrics")) {
                updatedSlaMap.put("metrics", processMetrics(currentSlaNode.get("metrics")));
            }

            // Update settlement if provided, otherwise preserve existing
            if (updateNode.has("settlement")) {
                updatedSlaMap.put("settlement", processSettlement(updateNode.get("settlement")));
            } else if (currentSlaNode.has("settlement")) {
                updatedSlaMap.put("settlement", processSettlement(currentSlaNode.get("settlement")));
            }

            // Create deterministic JSON string
            String updatedJson = createDeterministicJson(updatedSlaMap);

            // Store on ledger
            stub.putState(slaName, updatedJson.getBytes(StandardCharsets.UTF_8));
            stub.setEvent("SLAUpdated", updatedJson.getBytes(StandardCharsets.UTF_8));

            return updatedJson;

        } catch (Exception e) {
            throw new RuntimeException("Failed to update SLA: " + e.getMessage());
        }
    }

    /**
     * Terminates an SLA
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String terminateSLA(final Context ctx, final String slaName) {
        return updateSLA(ctx, slaName, "{\"status\":\"TERMINATED\"}");
    }

    /**
     * Get deterministic timestamp from transaction context
     */
    private String getDeterministicTimestampMili(Context ctx) {
        try {
            Instant txTimestamp = ctx.getStub().getTxTimestamp();
            // Return raw epoch millis
            return String.valueOf(txTimestamp.toEpochMilli());
        } catch (Exception e) {
            // Fallback: deterministic millis from txId
            String txId = ctx.getStub().getTxId();
            long deterministicMillis = 1600000000000L + (Math.abs(txId.hashCode()) % 10000000000L);
            return String.valueOf(deterministicMillis);
        }
    }

    /**
     * Get a specific violation
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getViolation(final Context ctx, final String slaName, final String violationId) {
        ChaincodeStub stub = ctx.getStub();
        String violationKey = stub.createCompositeKey("violation", slaName, violationId).toString();
        byte[] violationBytes = stub.getState(violationKey);
        if (violationBytes == null || violationBytes.length == 0) {
            throw new RuntimeException("Violation " + violationId + " for SLA " + slaName + " does not exist");
        }
        return new String(violationBytes, StandardCharsets.UTF_8);
    }

    /**
     * Checks if an SLA exists
     */
    private boolean slaExists(final Context ctx, final String slaName) {
        ChaincodeStub stub = ctx.getStub();
        byte[] slaBytes = stub.getState(slaName);
        return slaBytes != null && slaBytes.length > 0;
    }

    /*
     * #############################################################################
     * #############################################################################
     * ################################CHECK*VIOLATION##############################
     * #############################################################################
     * #############################################################################
     */

    /**
     * Check the violations with deterministic processing focused on latest metric
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String checkViolation(final Context ctx, final String eventDataJson) {
        try {
            JsonNode eventNode = objectMapper.readTree(eventDataJson);
            JsonNode metricsNode = eventNode.get("metrics");
            String latestMetric = eventNode.get("latestMetric").asText();
            String slaName = eventNode.get("slaName").asText();
            // String latestMetricTimestamp =
            // eventNode.get("latestMetricTimestamp").asText();

            if (metricsNode == null) {
                throw new RuntimeException("No metrics found in event data");
            }

            if (latestMetric == null || latestMetric.isEmpty()) {
                throw new RuntimeException("No latestMetric specified in event data");
            }

            String slaJson = getSLA(ctx, slaName);
            JsonNode slaNode = objectMapper.readTree(slaJson);

            if (!"ACTIVE".equals(slaNode.get("status").asText())) {
                throw new RuntimeException("SLA not active: " + slaName);
            }

            int currentSL = slaNode.get("currentSl").asInt();
            String currentSLNode = getSLnode(ctx, slaName, currentSL);
            JsonNode slNode = objectMapper.readTree(currentSLNode);

            // Convert metrics to Map for evaluation
            Map<String, String> metricsMap = new HashMap<>();
            Iterator<String> fieldNames = metricsNode.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                metricsMap.put(fieldName, metricsNode.get(fieldName).asText());
            }

            Iterator<String> metricNames = metricsNode.fieldNames();
            while (metricNames.hasNext()) {
                String metric = metricNames.next();

                ViolationResult violationResult = evaluateMetricWithLogicalExpression(slNode, metricsMap, metric);

                if (violationResult != null && violationResult.isViolated) {

                    return processViolationAndReturn(ctx, slaName, (ObjectNode) slaNode, violationResult, metricsMap,
                            eventNode, slNode);
                }

            }
            // no metric caused a violation
            return "{\"violated\":false,\"reason\":\"No violations detected\"}";

        } catch (Exception e) {
            throw new RuntimeException("Error in checkViolation: " + e.getMessage());
        }
    }

    /**
     * Evaluate metrics using full logical expression
     */
    private ViolationResult evaluateMetricWithLogicalExpression(JsonNode slNode, Map<String, String> metrics,
            String targetMetric) {
        LogicalExpression expression = buildLogicalExpression(slNode);

        if (!expression.containsMetric(targetMetric)) {
            return new ViolationResult(false, "Metric " + targetMetric + " not found in SLA conditions");
        }

        return expression.evaluateWithMetricPriority(metrics, targetMetric);
    }

    /**
     * Build logical expression tree from SLA node
     */
    private LogicalExpression buildLogicalExpression(JsonNode node) {
        if (node.has("firstArgument") && !node.has("operands")) {
            // Leaf condition
            String metric = node.get("firstArgument").asText();
            String operator = node.get("operator").asText();
            double threshold = node.get("secondArgument").asDouble();
            return new LeafCondition(metric, operator, threshold);
        }

        if (node.has("operator") && node.has("operands")) {
            String operator = node.get("operator").asText();
            List<LogicalExpression> operands = new ArrayList<>();

            for (JsonNode operand : node.get("operands")) {
                operands.add(buildLogicalExpression(operand));
            }

            return new CompoundExpression(operator, operands);
        }

        throw new RuntimeException("Invalid SLA node structure");
    }

    /**
     * Process violation and return result
     */
    private String processViolationAndReturn(Context ctx, String slaName, ObjectNode slaNode,
            ViolationResult violationResult, Map<String, String> metricsMap,
            JsonNode eventNode, JsonNode slNode) {

        ObjectNode finalSlaState = processVio_Tran_Sett(ctx, slaName, (ObjectNode) slaNode, violationResult, metricsMap,
                eventNode, slNode);

        // Single atomic state update
        ChaincodeStub stub = ctx.getStub();
        try {
            String finalJson = objectMapper.writeValueAsString(finalSlaState);
            stub.putState(slaName, finalJson.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Error updating SLA state: " + e.getMessage());
        }

        return "{\"violated\":" + violationResult.isViolated +
                ",\"reason\":\"" + violationResult.reason +
                "\",\"metric\":\"" + violationResult.violatedMetric +
                "\",\"value\":" + violationResult.violatedValue + "}";

    }

    /**
     * Process violation, transitions, and settlement in memory - NO state writes
     */
    private ObjectNode processVio_Tran_Sett(Context ctx, String slaName, ObjectNode slaNode,
            ViolationResult violationResult, Map<String, String> metrics,
            JsonNode eventNode, JsonNode slNode) {
        try {
            // 1. Add violation to in-memory state
            ObjectNode stateWithViolation = addViolationInMemory(ctx, slaNode, violationResult, eventNode, slNode);

            String T_Timestamp = eventNode.get("latestMetricTimestamp").asText();
            // 2. Check and apply transitions in memory
            ObjectNode stateAfterTransition = applyTransitionsInMemory(ctx, slaName,
                    stateWithViolation, eventNode, T_Timestamp);

            String S_Timestamp = eventNode.get("latestMetricTimestamp").asText();
            // 3. Check and apply settlement in memory
            ObjectNode finalState = applySettlementInMemory(ctx, slaName,
                    stateAfterTransition, eventNode, S_Timestamp);

            return finalState;

        } catch (Exception e) {
            throw new RuntimeException("Error processing violation and transitions: " + e.getMessage());
        }
    }

    /**
     * Add violation to SLA state in memory only
     */
    private ObjectNode addViolationInMemory(Context ctx, ObjectNode slaNode, ViolationResult violationResult,
            JsonNode eventNode, JsonNode slNode) {
        try {
            ChaincodeStub stub = ctx.getStub();
            String violatedMetric = violationResult.violatedMetric != null ? violationResult.violatedMetric : "unknown";

            // Create deterministic violation ID
            String txId = stub.getTxId();
            String violationId = "vio-" + slaNode.get("slaName").asText().substring(0, 8) + "-" +
                    txId.substring(0, 8) + "-" + violatedMetric;

            // Create violation object
            Map<String, Object> violation = new LinkedHashMap<>();
            violation.put("violationId", violationId);
            // violation.put("violationTimestamp", violationTimestamp);
            violation.put("latestMetricTimestamp", eventNode.get("latestMetricTimestamp").asText());
            violation.put("timestamp", getDeterministicTimestampMili(ctx));
            violation.put("latestMetric", eventNode.get("latestMetric").asText());
            violation.put("violatedMetric", violatedMetric);
            violation.put("value", violationResult.violatedValue);
            violation.put("slName", slaNode.get("currentSl").asInt());

            violation.put("metrics", eventNode.get("metrics"));

            // Add to existing violations array
            JsonNode violationsNode = slaNode.get("violations");
            List<Map<String, Object>> violationsList = new ArrayList<>();

            if (violationsNode != null && violationsNode.isArray()) {
                for (JsonNode vNode : violationsNode) {
                    Map<String, Object> existingViolation = objectMapper.convertValue(vNode, Map.class);
                    violationsList.add(existingViolation);
                }
            }

            violationsList.add(violation);
            slaNode.set("violations", objectMapper.valueToTree(violationsList));

            System.out.println("Added violation in memory. Total violations: " + violationsList.size());

            /////////////////////////////////////// VIOLATION EVENT ////////////////////

            ObjectNode eventPayload = objectMapper.createObjectNode();
            eventPayload.put("slaName", slaNode.get("slaName").asText());
            eventPayload.put("timestamp", getDeterministicTimestampMili(ctx));
            eventPayload.put("sla_level", slaNode.get("currentSl").asInt());
            eventPayload.put("violatedMetric", violatedMetric);
            eventPayload.put("slNode", slNode);
            eventPayload.put("metrics", eventNode.get("metrics"));

            // eventPayload.put("value", violationResult.violatedValue);
            // eventPayload.put("probability", 1);
            // eventPayload.put("metrics", eventNode.get("metrics"));
            // eventPayload.put("metric", violatedMetric);
            // eventPayload.put("value", violationResult.violatedValue);
            // eventPayload.put("reason", violationResult.reason);

            // set an event for the violation
            byte[] bytes = objectMapper.writeValueAsBytes(eventPayload);
            stub.setEvent("ViolationDetectedEvent", bytes);

            return slaNode;

        } catch (Exception e) {
            throw new RuntimeException("Error adding violation in memory: " + e.getMessage());
        }
    }

    /**
     * Apply transitions in memory only
     */
    private ObjectNode applyTransitionsInMemory(Context ctx, String slaName, ObjectNode slaNode, JsonNode eventNode,
            String timestamp) {
        try {
            ChaincodeStub stub = ctx.getStub();

            if (!"ACTIVE".equals(slaNode.get("status").asText())) {
                return slaNode;
            }

            int currentSLInt = slaNode.get("currentSl").asInt();
            // String currentSL = String.valueOf(currentSLInt);

            JsonNode transitions = slaNode.get("transitions");
            if (transitions == null || !transitions.isArray()) {
                return slaNode;
            }

            // Instant currentTime = Instant.parse(timestamp);
            // Instant currentTime = Instant.ofEpochSecond(Long.parseLong(timestamp));
            Instant currentTime = Instant.ofEpochMilli(Long.parseLong(timestamp));

            for (JsonNode transition : transitions) {
                int firstSl = transition.get("firstSl").asInt();
                int secondSl = transition.get("secondSl").asInt();
                if (firstSl != currentSLInt) {
                    continue;
                }

                String evaluationPeriod = transition.get("evaluationPeriod").asText();
                int violationThreshold = transition.get("violationThreshold").asInt();

                Duration period = Duration.parse(evaluationPeriod);
                Instant cutoffTime = currentTime.minus(period);

                JsonNode violationsArray = slaNode.get("violations");
                long recentViolations = 0;
                if (violationsArray != null && violationsArray.isArray()) {
                    System.out.println("Checking transitions with violation array size: " + violationsArray.size());

                    for (JsonNode violationNode : violationsArray) {
                        String violationTimeStr = violationNode.get("latestMetricTimestamp").asText();

                        Instant violationTime = Instant.ofEpochMilli(Long.parseLong(violationTimeStr));
                        int violationSL = violationNode.get("slName").asInt();
                        // System.out.println("Violation SL: " + violationSL);
                        // System.out.println("current SL: " + currentSLInt);
                        // System.out.println("Violation Time: " + violationTime);
                        // System.out.println("currentSLInt: " + currentSLInt);
                        // System.out.println("cutoffTime: " + cutoffTime);

                        if (violationTime.isAfter(cutoffTime) && violationSL == currentSLInt) {
                            recentViolations++;
                        }
                    }
                }

                System.out.println("Transition check: " + recentViolations + "/" + violationThreshold +
                        " violations for SL " + currentSLInt);

                if (recentViolations >= violationThreshold) {
                    int targetSLInt = secondSl;
                    slaNode.put("currentSl", targetSLInt);
                    System.out.println("Applied transition in memory: SL " + firstSl + " → SL " + secondSl);

                    /////////////////////////////////////// TRANSITION EVENT ////////////////////

                    ObjectNode eventPayload = objectMapper.createObjectNode();
                    // eventPayload.put("txId", stub.getTxId());
                    eventPayload.put("slaName", slaNode.get("slaName").asText());
                    eventPayload.put("timestamp", timestamp);
                    eventPayload.put("original_status", firstSl);
                    eventPayload.put("final_status", secondSl);

                    // eventPayload.put("latestMetricTimestamp",
                    // eventNode.get("latestMetricTimestamp").asText());
                    // eventPayload.put("firstSl", firstSl);
                    // eventPayload.put("secondSl", secondSl);
                    // eventPayload.put("violationThreshold", violationThreshold);
                    // eventPayload.put("recentViolations", recentViolations);

                    // set an event for the violation
                    byte[] bytes = objectMapper.writeValueAsBytes(eventPayload);
                    stub.setEvent("TransitionEvent", bytes);

                    break; // Only one transition per execution
                }
            }

            return slaNode;

        } catch (Exception e) {
            throw new RuntimeException("Error applying transitions in memory: " + e.getMessage());
        }
    }

    /**
     * Apply settlement in memory only
     */
    private ObjectNode applySettlementInMemory(Context ctx, String slaName, ObjectNode slaNode, JsonNode eventNode,
            String timestamp) {
        try {
            ChaincodeStub stub = ctx.getStub();

            if (!"ACTIVE".equals(slaNode.get("status").asText())) {
                return slaNode;
            }

            JsonNode settlement = slaNode.get("settlement");
            if (settlement == null) {
                return slaNode;
            }

            int concernedSL = settlement.get("concernedSL").asInt();
            int currentSLInt = slaNode.get("currentSl").asInt();

            if (concernedSL != currentSLInt) {
                return slaNode;
            }

            String evaluationPeriod = settlement.get("evaluationPeriod").asText();
            int settlementCount = settlement.get("settlementCount").asInt();
            String settlementAction = settlement.get("settlementAction").asText();

            // Instant currentTime = Instant.ofEpochSecond(Long.parseLong(timestamp));
            Instant currentTime = Instant.ofEpochMilli(Long.parseLong(timestamp));
            Duration period = Duration.parse(evaluationPeriod);
            Instant cutoffTime = currentTime.minus(period);

            JsonNode violationsArray = slaNode.get("violations");
            long recentViolations = 0;
            if (violationsArray != null && violationsArray.isArray()) {
                for (JsonNode violationNode : violationsArray) {
                    String violationTimeStr = violationNode.get("latestMetricTimestamp").asText();
                    // Instant violationTime =
                    // Instant.ofEpochSecond(Long.parseLong(violationTimeStr));
                    Instant violationTime = Instant.ofEpochMilli(Long.parseLong(violationTimeStr));

                    int violationSL = violationNode.get("slName").asInt();

                    if (violationTime.isAfter(cutoffTime) && violationSL == concernedSL) {
                        recentViolations++;
                    }
                }
            }

            System.out.println("Settlement check: " + recentViolations + "/" + settlementCount +
                    " violations for SL " + concernedSL);

            if (recentViolations >= settlementCount) {
                slaNode.put("status", settlementAction);
                System.out.println("Applied settlement in memory: " + settlementAction);

                /////////////////////////////////////// SETTLEMENT EVENT ////////////////////

                ObjectNode eventPayload = objectMapper.createObjectNode();
                // eventPayload.put("txId", stub.getTxId());
                eventPayload.put("slaName", slaNode.get("slaName").asText());
                eventPayload.put("timestamp", timestamp);
                eventPayload.put("original_status", concernedSL);
                eventPayload.put("final_status", settlementAction);

                // eventPayload.put("latestMetricTimestamp",
                // eventNode.get("latestMetricTimestamp").asText());
                // eventPayload.put("settlementTimestamp", timestamp);
                // eventPayload.put("concernedSL", concernedSL);
                // eventPayload.put("settlementCount", settlementCount);
                // eventPayload.put("recentViolations", recentViolations);
                // eventPayload.put("settlementAction", settlementAction);

                // set an event for the violation
                byte[] bytes = objectMapper.writeValueAsBytes(eventPayload);
                stub.setEvent("SettlementEvent", bytes);

            }

            return slaNode;

        } catch (Exception e) {
            throw new RuntimeException("Error applying settlement in memory: " + e.getMessage());
        }
    }

    /**
     * Evaluate an expression with correct AND/OR logic
     */
    private ViolationResult evaluateExpression(JsonNode node, Map<String, String> metrics) {
        // Check if this is a leaf operand
        if (node.has("firstArgument") && !node.has("operands")) {
            return evaluateLeafCondition(node, metrics);
        }

        // Handle compound expressions
        if (node.has("operator") && node.has("operands")) {
            String operator = node.get("operator").asText();
            JsonNode operands = node.get("operands");

            if ("AND".equals(operator)) {
                return evaluateANDCorrect(operands, metrics);
            } else if ("OR".equals(operator)) {
                return evaluateORCorrect(operands, metrics);
            }
        }

        return new ViolationResult(false, "Unknown expression type");
    }

    /**
     * Correct AND evaluation: violation if ANY operand violates
     */
    private ViolationResult evaluateANDCorrect(JsonNode operands, Map<String, String> metrics) {
        List<String> satisfiedConditions = new ArrayList<>();

        for (JsonNode operand : operands) {
            ViolationResult operandResult = evaluateExpression(operand, metrics);

            // For AND: if ANY operand violates, the whole AND violates
            if (operandResult.isViolated) {
                return new ViolationResult(true,
                        "AND violation: " + operandResult.reason,
                        operandResult.violatedMetric, operandResult.violatedValue);
            }

            satisfiedConditions.add(operandResult.reason);
        }

        return new ViolationResult(false, "All AND conditions satisfied: " + String.join(" AND ", satisfiedConditions));
    }

    /**
     * Correct OR evaluation: violation if ALL operands violate
     */
    private ViolationResult evaluateORCorrect(JsonNode operands, Map<String, String> metrics) {
        List<String> violatedConditions = new ArrayList<>();
        String firstViolatedMetric = null;
        double firstViolatedValue = 0.0;

        for (JsonNode operand : operands) {
            ViolationResult operandResult = evaluateExpression(operand, metrics);

            if (operandResult.isViolated) {
                violatedConditions.add(operandResult.reason);
                if (firstViolatedMetric == null && operandResult.violatedMetric != null) {
                    firstViolatedMetric = operandResult.violatedMetric;
                    firstViolatedValue = operandResult.violatedValue;
                }
            } else {
                // For OR: if ANY operand is satisfied, the whole OR is satisfied
                return new ViolationResult(false,
                        "OR satisfied by: " + operandResult.reason);
            }
        }

        // If we reach here, ALL operands violated
        return new ViolationResult(true,
                "OR violation: all conditions failed - " + String.join(" OR ", violatedConditions),
                firstViolatedMetric, firstViolatedValue);
    }

    /**
     * Evaluate a leaf condition (direct metric comparison)
     */
    private ViolationResult evaluateLeafCondition(JsonNode operand, Map<String, String> metrics) {
        String firstArg = operand.get("firstArgument").asText();
        String operator = operand.get("operator").asText();
        double secondArg = operand.get("secondArgument").asDouble();

        String metricValueStr = metrics.get(firstArg);
        if (metricValueStr == null) {
            // If metric is not available, assume it's satisfied (conservative approach)
            return new ViolationResult(false, firstArg + " not available (assumed satisfied)");
        }

        try {
            double metricValue = Double.parseDouble(metricValueStr);
            boolean conditionMet = false;
            String conditionDesc = firstArg + " " + operator + " " + secondArg + " (actual: " + metricValue + ")";

            switch (operator) {
                case "GREATER_EQUAL_THAN":
                    conditionMet = metricValue >= secondArg;
                    break;
                case "LESS_EQUAL_THAN":
                    conditionMet = metricValue <= secondArg;
                    break;
                case "LESS_THAN":
                    conditionMet = metricValue < secondArg;
                    break;
                case "GREATER_THAN":
                    conditionMet = metricValue > secondArg;
                    break;
                case "EQUAL":
                    conditionMet = metricValue == secondArg;
                    break;
                default:
                    return new ViolationResult(true, "Unknown operator: " + operator, firstArg, metricValue);
            }

            // Return violation result: violated = !conditionMet
            return new ViolationResult(!conditionMet, conditionDesc, firstArg, metricValue);

        } catch (NumberFormatException e) {
            return new ViolationResult(true, "Invalid metric value for " + firstArg + ": " + metricValueStr, firstArg,
                    0.0);
        }
    }

}