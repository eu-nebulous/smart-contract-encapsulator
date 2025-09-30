package eu.nebulouscloud.smart.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

@DataType
@JsonPropertyOrder({ "firstArgument", "operator", "secondArgument" })
public class SimpleSLO {
    @Property
    @JsonProperty("firstArgument")
    private String firstArgument;

    @Property
    @JsonProperty("operator")
    private String operator; // GREATER_EQUAL_THAN, GREATER_THAN, LESS_EQUAL_THAN, LESS_THAN, EQUALS,
                             // NOT_EQUALS

    @Property
    @JsonProperty("secondArgument")
    private Object secondArgument; // Can be number or string

    public SimpleSLO() {
    }

    public SimpleSLO(String firstArgument, String operator, Object secondArgument) {
        this.firstArgument = firstArgument;
        this.operator = operator;
        this.secondArgument = secondArgument;
    }

    // Getters and Setters
    public String getFirstArgument() {
        return firstArgument;
    }

    public void setFirstArgument(String firstArgument) {
        this.firstArgument = firstArgument;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Object getSecondArgument() {
        return secondArgument;
    }

    public void setSecondArgument(Object secondArgument) {
        this.secondArgument = secondArgument;
    }
}