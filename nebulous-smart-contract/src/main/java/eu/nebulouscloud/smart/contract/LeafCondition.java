package eu.nebulouscloud.smart.contract;

import java.util.Map;

/**
 * Leaf condition (metric comparison)
 */
public class LeafCondition extends LogicalExpression {
    private String metric;
    private String operator;
    private double threshold;

    public LeafCondition(String metric, String operator, double threshold) {
        this.metric = metric;
        this.operator = operator;
        this.threshold = threshold;
    }

    @Override
    public ViolationResult evaluate(Map<String, String> metrics) {
        return evaluateCondition(metrics);
    }

    @Override
    public ViolationResult evaluateWithMetricPriority(Map<String, String> metrics, String priorityMetric) {
        // For leaf conditions, priority doesn't change evaluation
        return evaluateCondition(metrics);
    }

    @Override
    public boolean containsMetric(String targetMetric) {
        return this.metric.equals(targetMetric);
    }

    @Override
    public String getDescription() {
        return metric + " " + operator + " " + threshold;
    }

    private ViolationResult evaluateCondition(Map<String, String> metrics) {
        String metricValueStr = metrics.get(metric);
        if (metricValueStr == null) {
            return new ViolationResult(false, metric + " not available (assumed satisfied)");
        }

        try {
            double metricValue = Double.parseDouble(metricValueStr);
            boolean conditionMet = false;
            String conditionDesc = metric + " " + operator + " " + threshold + " (actual: " + metricValue + ")";

            switch (operator) {
                case "GREATER_EQUAL_THAN":
                    conditionMet = metricValue >= threshold;
                    break;
                case "LESS_EQUAL_THAN":
                    conditionMet = metricValue <= threshold;
                    break;
                case "LESS_THAN":
                    conditionMet = metricValue < threshold;
                    break;
                case "GREATER_THAN":
                    conditionMet = metricValue > threshold;
                    break;
                case "EQUAL":
                    conditionMet = metricValue == threshold;
                    break;
                default:
                    return new ViolationResult(true, "Unknown operator: " + operator, metric, metricValue);
            }

            // Return violation result: violated = !conditionMet
            return new ViolationResult(!conditionMet, conditionDesc, metric, metricValue);

        } catch (NumberFormatException e) {
            return new ViolationResult(true, "Invalid metric value for " + metric + ": " + metricValueStr, metric,
                    0.0);
        }
    }
}