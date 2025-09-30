package eu.nebulouscloud.smart.contract;

import java.util.Map;

/**
 * Abstract base class for logical expressions
 */
public abstract class LogicalExpression {
    public abstract ViolationResult evaluate(Map<String, String> metrics);

    public abstract ViolationResult evaluateWithMetricPriority(Map<String, String> metrics, String priorityMetric);

    public abstract boolean containsMetric(String metric);

    public abstract String getDescription();
}