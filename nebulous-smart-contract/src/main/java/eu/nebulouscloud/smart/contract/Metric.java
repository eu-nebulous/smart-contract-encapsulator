package eu.nebulouscloud.smart.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

@DataType
// Force consistent serialization order
@JsonPropertyOrder({ "name", "window", "output" })
public class Metric {
    @Property
    @JsonProperty("name")
    private String name;

    @Property
    @JsonProperty("window")
    private MetricWindow window;

    @Property
    @JsonProperty("output")
    private MetricOutput output;

    public Metric() {
    }

    public Metric(String name, MetricWindow window, MetricOutput output) {
        this.name = name;
        this.window = window;
        this.output = output;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public MetricWindow getWindow() {
        return window;
    }

    public void setWindow(MetricWindow window) {
        this.window = window;
    }

    public MetricOutput getOutput() {
        return output;
    }

    public void setOutput(MetricOutput output) {
        this.output = output;
    }
}