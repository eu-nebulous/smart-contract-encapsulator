package eu.nebulouscloud.smart.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

@DataType
@JsonPropertyOrder({ "violationId", "timestamp", "metric", "value", "slName" })
public class Violation {
    @Property
    @JsonProperty("violationId")
    private String violationId;

    @Property
    @JsonProperty("timestamp")
    private String timestamp; // ISO 8601 timestamp

    @Property
    @JsonProperty("metric")
    private String metric;

    @Property
    @JsonProperty("value")
    private double value;

    @Property
    @JsonProperty("slName")
    private int slName;

    public Violation() {
    }

    public Violation(String violationId, String timestamp, String metric, double value, int slName) {
        this.violationId = violationId;
        this.timestamp = timestamp;
        this.metric = metric;
        this.value = value;
        this.slName = slName;
    }

    // Getters and Setters
    public String getViolationId() {
        return violationId;
    }

    public void setViolationId(String violationId) {
        this.violationId = violationId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public int getSlName() {
        return slName;
    }

    public void setSlName(int slName) {
        this.slName = slName;
    }
}