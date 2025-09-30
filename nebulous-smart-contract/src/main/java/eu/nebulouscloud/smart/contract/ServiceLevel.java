package eu.nebulouscloud.smart.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;
import java.util.List;

@DataType
// Ensure consistent JSON serialization order
@JsonPropertyOrder({ "slName", "operator", "operands" })
public class ServiceLevel {
    @Property
    @JsonProperty("slName")
    private int slName;

    @Property
    @JsonProperty("operator")
    private String operator; // AND, OR

    @Property
    @JsonProperty("operands")
    private List<Object> operands; // Can be SimpleSLO or NestedSLO

    public ServiceLevel() {
    }

    public ServiceLevel(int slName, String operator, List<Object> operands) {
        this.slName = slName;
        this.operator = operator;
        this.operands = operands;
    }

    // Getters and Setters
    public int getSlName() {
        return slName;
    }

    public void setSlName(int slName) {
        this.slName = slName;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public List<Object> getOperands() {
        return operands;
    }

    public void setOperands(List<Object> operands) {
        this.operands = operands;
    }
}
