package eu.nebulouscloud.smart.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

@DataType
// Ensure JSON serialization is always in this order
@JsonPropertyOrder({ "type", "unit", "value" })
public class MetricOutput {
    @Property
    @JsonProperty("type")
    private String type;

    @Property
    @JsonProperty("unit")
    private String unit;

    @Property
    @JsonProperty("value")
    private int value;

    public MetricOutput() {
    }

    public MetricOutput(String type, String unit, int value) {
        this.type = type;
        this.unit = unit;
        this.value = value;
    }

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }
}