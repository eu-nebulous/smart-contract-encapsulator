package eu.nebulouscloud.smart.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

@DataType
@JsonPropertyOrder({ "evaluationPeriod", "settlementCount", "concernedSL", "settlementAction" })
public class Settlement {
    @Property
    @JsonProperty("evaluationPeriod")
    private String evaluationPeriod;

    @Property
    @JsonProperty("settlementCount")
    private int settlementCount;

    @Property
    @JsonProperty("concernedSL")
    private int concernedSL;

    @Property
    @JsonProperty("settlementAction")
    private String settlementAction; // CANCEL

    public Settlement() {
    }

    public Settlement(String evaluationPeriod, int settlementCount, int concernedSL, String settlementAction) {
        this.evaluationPeriod = evaluationPeriod;
        this.settlementCount = settlementCount;
        this.concernedSL = concernedSL;
        this.settlementAction = settlementAction;
    }

    // Getters and Setters
    public String getEvaluationPeriod() {
        return evaluationPeriod;
    }

    public void setEvaluationPeriod(String evaluationPeriod) {
        this.evaluationPeriod = evaluationPeriod;
    }

    public int getSettlementCount() {
        return settlementCount;
    }

    public void setSettlementCount(int settlementCount) {
        this.settlementCount = settlementCount;
    }

    public int getConcernedSL() {
        return concernedSL;
    }

    public void setConcernedSL(int concernedSL) {
        this.concernedSL = concernedSL;
    }

    public String getSettlementAction() {
        return settlementAction;
    }

    public void setSettlementAction(String settlementAction) {
        this.settlementAction = settlementAction;
    }
}
