package eu.nebulouscloud.smart.contract;

/**
 * Helper class for violation results used by the logical expression classes.
 */
public class ViolationResult {
    public boolean isViolated;
    public String reason;
    public String violatedMetric;
    public double violatedValue;

    public ViolationResult(boolean isViolated, String reason) {
        this(isViolated, reason, null, 0.0);
    }

    public ViolationResult(boolean isViolated, String reason, String violatedMetric, double violatedValue) {
        this.isViolated = isViolated;
        this.reason = reason;
        this.violatedMetric = violatedMetric;
        this.violatedValue = violatedValue;
    }

    @Override
    public String toString() {
        return "ViolationResult{" +
                "isViolated=" + isViolated +
                ", reason='" + reason + '\'' +
                ", violatedMetric='" + violatedMetric + '\'' +
                ", violatedValue=" + violatedValue +
                '}';
    }
}