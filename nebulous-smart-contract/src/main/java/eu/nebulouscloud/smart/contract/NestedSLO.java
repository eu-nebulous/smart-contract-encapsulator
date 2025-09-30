package eu.nebulouscloud.smart.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;
import java.util.List;

@DataType
// Ensure JSON serialization is always in this order
@JsonPropertyOrder({ "operator", "operands" })
public class NestedSLO {
    @Property
    @JsonProperty("operator")
    private String operator; // AND, OR

    @Property
    @JsonProperty("operands")
    private List<Object> operands; // Can be SimpleSLO or NestedSLO

    public NestedSLO() {
    }

    public NestedSLO(String operator, List<Object> operands) {
        this.operator = operator;
        this.operands = operands;
    }

    // Getters and Setters
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