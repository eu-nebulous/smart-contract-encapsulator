package eu.nebulouscloud.smart.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compound expression (AND/OR)
 */
public class CompoundExpression extends LogicalExpression {
    private String operator;
    private List<LogicalExpression> operands;

    public CompoundExpression(String operator, List<LogicalExpression> operands) {
        this.operator = operator;
        this.operands = operands;
    }

    @Override
    public ViolationResult evaluate(Map<String, String> metrics) {
        return evaluateLogical(metrics, null);
    }

    @Override
    public ViolationResult evaluateWithMetricPriority(Map<String, String> metrics, String priorityMetric) {
        return evaluateLogical(metrics, priorityMetric);
    }

    @Override
    public boolean containsMetric(String targetMetric) {
        return operands.stream().anyMatch(operand -> operand.containsMetric(targetMetric));
    }

    @Override
    public String getDescription() {
        return "(" + operator + " expression with " + operands.size() + " operands)";
    }

    private ViolationResult evaluateLogical(Map<String, String> metrics, String priorityMetric) {
        if ("AND".equals(operator)) {
            return evaluateAND(metrics, priorityMetric);
        } else if ("OR".equals(operator)) {
            return evaluateOR(metrics, priorityMetric);
        } else {
            return new ViolationResult(true, "Unknown logical operator: " + operator);
        }
    }

    private ViolationResult evaluateAND(Map<String, String> metrics, String priorityMetric) {
        // Evaluate operands containing priorityMetric first (if provided)
        List<LogicalExpression> first = new ArrayList<>();
        List<LogicalExpression> rest = new ArrayList<>();
        if (priorityMetric != null) {
            for (LogicalExpression op : operands) {
                if (op.containsMetric(priorityMetric))
                    first.add(op);
                else
                    rest.add(op);
            }
        } else {
            rest.addAll(operands);
        }

        // evaluate prioritized ones first
        for (LogicalExpression op : first) {
            ViolationResult r = op.evaluateWithMetricPriority(metrics, priorityMetric);
            if (r == null)
                continue;
            if (r.isViolated) {
                return new ViolationResult(true, "AND violation (priority): " + r.reason, r.violatedMetric,
                        r.violatedValue);
            }
        }
        // evaluate the rest
        for (LogicalExpression op : rest) {
            ViolationResult r = op.evaluate(metrics);
            if (r == null)
                continue;
            if (r.isViolated) {
                return new ViolationResult(true, "AND violation: " + r.reason, r.violatedMetric, r.violatedValue);
            }
        }
        return new ViolationResult(false, "All AND conditions satisfied");
    }

    /**
     * OR evaluation: satisfied if ANY operand satisfied. Evaluate
     * priority-containing operands first.
     * Violation only if ALL operands violate.
     */
    private ViolationResult evaluateOR(Map<String, String> metrics, String priorityMetric) {
        List<LogicalExpression> first = new ArrayList<>();
        List<LogicalExpression> rest = new ArrayList<>();
        if (priorityMetric != null) {
            for (LogicalExpression op : operands) {
                if (op.containsMetric(priorityMetric))
                    first.add(op);
                else
                    rest.add(op);
            }
        } else {
            rest.addAll(operands);
        }

        // evaluate prioritized ones first; if any is satisfied, OR is satisfied
        for (LogicalExpression op : first) {
            ViolationResult r = op.evaluateWithMetricPriority(metrics, priorityMetric);
            if (r == null)
                continue;
            if (!r.isViolated) {
                return new ViolationResult(false, "OR satisfied by (priority): " + r.reason);
            }
        }

        // evaluate the rest; if any satisfied, OR satisfied
        for (LogicalExpression op : rest) {
            ViolationResult r = op.evaluate(metrics);
            if (r == null)
                continue;
            if (!r.isViolated) {
                return new ViolationResult(false, "OR satisfied by: " + r.reason);
            }
        }

        // all operands violated
        List<String> reasons = new ArrayList<>();
        for (LogicalExpression op : operands) {
            ViolationResult r = op.evaluate(metrics);
            if (r != null)
                reasons.add(r.reason);
        }
        String combined = String.join(" AND ", reasons);
        // pick first operand violation details if available
        ViolationResult firstViolation = operands.size() > 0 ? operands.get(0).evaluate(metrics)
                : new ViolationResult(true, "No operands");
        return new ViolationResult(true, "OR violation: all conditions failed - " + combined,
                firstViolation.violatedMetric, firstViolation.violatedValue);
    }
}