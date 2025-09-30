package eu.nebulouscloud.sce.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.Arrays;
import java.util.List;

public class SlaJsonValidator {

    private final ObjectMapper objectMapper;

    // Define field type requirements
    private static final List<String> INTEGER_FIELDS = Arrays.asList(
            "slName", "firstSl", "secondSl", "violationThreshold",
            "concernedSL", "settlementCount", "value");

    private static final List<String> DOUBLE_FIELDS = Arrays.asList(
            "secondArgument");

    public SlaJsonValidator() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Validates and converts JSON data according to SLA schema requirements
     * 
     * @param jsonData The input JsonNode to validate and convert
     * @return Converted JsonNode with proper data types
     * @throws IllegalArgumentException if JSON structure is invalid
     */
    public JsonNode validateAndConvertSlaJson(JsonNode jsonData) throws IllegalArgumentException {
        // 1. Check if it's a valid JSON structure
        if (jsonData == null || !jsonData.isObject()) {
            throw new IllegalArgumentException("Invalid JSON: Root must be an object");
        }

        // Create a deep copy to avoid modifying the original
        ObjectNode result = jsonData.deepCopy();

        // 2. Validate required top-level structure
        validateRequiredFields(result);

        // 3. Convert data types throughout the JSON
        convertDataTypes(result);

        return result;
    }

    private void validateRequiredFields(JsonNode jsonData) {
        String[] requiredFields = { "slaName", "sls", "transitions", "settlement", "metrics" };

        for (String field : requiredFields) {
            if (!jsonData.has(field)) {
                throw new IllegalArgumentException("Missing required field: " + field);
            }
        }

        // Check if sls is array
        if (!jsonData.get("sls").isArray()) {
            throw new IllegalArgumentException("Field 'sls' must be an array");
        }

        // Check if transitions is array
        if (!jsonData.get("transitions").isArray()) {
            throw new IllegalArgumentException("Field 'transitions' must be an array");
        }

        // Check if metrics is array
        if (!jsonData.get("metrics").isArray()) {
            throw new IllegalArgumentException("Field 'metrics' must be an array");
        }

        // Check if settlement is object
        if (!jsonData.get("settlement").isObject()) {
            throw new IllegalArgumentException("Field 'settlement' must be an object");
        }
    }

    private void convertDataTypes(ObjectNode rootNode) {
        // Convert top-level fields
        convertNodeTypes(rootNode);

        // Convert sls array
        if (rootNode.has("sls") && rootNode.get("sls").isArray()) {
            ArrayNode slsArray = (ArrayNode) rootNode.get("sls");
            for (int i = 0; i < slsArray.size(); i++) {
                JsonNode slNode = slsArray.get(i);
                if (slNode.isObject()) {
                    convertNodeTypes((ObjectNode) slNode);
                    // Recursively convert operands
                    convertOperands((ObjectNode) slNode);
                }
            }
        }

        // Convert transitions array
        if (rootNode.has("transitions") && rootNode.get("transitions").isArray()) {
            ArrayNode transitionsArray = (ArrayNode) rootNode.get("transitions");
            for (int i = 0; i < transitionsArray.size(); i++) {
                JsonNode transitionNode = transitionsArray.get(i);
                if (transitionNode.isObject()) {
                    convertNodeTypes((ObjectNode) transitionNode);
                }
            }
        }

        // Convert settlement object
        if (rootNode.has("settlement") && rootNode.get("settlement").isObject()) {
            convertNodeTypes((ObjectNode) rootNode.get("settlement"));
        }

        // Convert metrics array
        if (rootNode.has("metrics") && rootNode.get("metrics").isArray()) {
            ArrayNode metricsArray = (ArrayNode) rootNode.get("metrics");
            for (int i = 0; i < metricsArray.size(); i++) {
                JsonNode metricNode = metricsArray.get(i);
                if (metricNode.isObject()) {
                    convertNodeTypes((ObjectNode) metricNode);
                    // Convert nested window and output objects
                    convertNestedObjects((ObjectNode) metricNode);
                }
            }
        }
    }

    private void convertOperands(ObjectNode slNode) {
        if (slNode.has("operands") && slNode.get("operands").isArray()) {
            ArrayNode operandsArray = (ArrayNode) slNode.get("operands");
            for (int i = 0; i < operandsArray.size(); i++) {
                JsonNode operandNode = operandsArray.get(i);
                if (operandNode.isObject()) {
                    convertNodeTypes((ObjectNode) operandNode);
                    // Recursively handle nested operands
                    convertOperands((ObjectNode) operandNode);
                }
            }
        }
    }

    private void convertNestedObjects(ObjectNode metricNode) {
        // Convert window object
        if (metricNode.has("window") && metricNode.get("window") != null && metricNode.get("window").isObject()) {
            convertNodeTypes((ObjectNode) metricNode.get("window"));
        }

        // Convert output object
        if (metricNode.has("output") && metricNode.get("output").isObject()) {
            convertNodeTypes((ObjectNode) metricNode.get("output"));
        }
    }

    private void convertNodeTypes(ObjectNode node) {
        // Create a list of field names to avoid concurrent modification
        // List<String> fieldNames = Arrays.asList(node.fieldNames().next().split(","));

        node.fieldNames().forEachRemaining(fieldName -> {
            JsonNode fieldValue = node.get(fieldName);

            if (fieldValue == null || fieldValue.isNull()) {
                return; // Skip null values
            }

            try {
                // Convert integers
                if (INTEGER_FIELDS.contains(fieldName)) {
                    int intValue = convertToInt(fieldValue, fieldName);
                    node.put(fieldName, intValue);
                }
                // Convert doubles
                else if (DOUBLE_FIELDS.contains(fieldName)) {
                    double doubleValue = convertToDouble(fieldValue, fieldName);
                    node.put(fieldName, doubleValue);
                }
                // Everything else should be string (if not already object/array)
                else if (!fieldValue.isObject() && !fieldValue.isArray()) {
                    String stringValue = fieldValue.asText();
                    node.put(fieldName, stringValue);
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Error converting field '" + fieldName + "': " + e.getMessage());
            }
        });
    }

    private int convertToInt(JsonNode node, String fieldName) {
        if (node.isInt()) {
            return node.asInt();
        } else if (node.isTextual()) {
            try {
                return Integer.parseInt(node.asText().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Cannot convert field '" + fieldName + "' value '" + node.asText() + "' to integer");
            }
        } else if (node.isDouble() || node.isFloatingPointNumber()) {
            return (int) node.asDouble(); // Truncate decimal part
        } else {
            throw new IllegalArgumentException(
                    "Cannot convert field '" + fieldName + "' of type " + node.getNodeType() + " to integer");
        }
    }

    private double convertToDouble(JsonNode node, String fieldName) {
        if (node.isDouble() || node.isFloatingPointNumber()) {
            return node.asDouble();
        } else if (node.isInt()) {
            return (double) node.asInt();
        } else if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Cannot convert field '" + fieldName + "' value '" + node.asText() + "' to double");
            }
        } else {
            throw new IllegalArgumentException(
                    "Cannot convert field '" + fieldName + "' of type " + node.getNodeType() + " to double");
        }
    }

}