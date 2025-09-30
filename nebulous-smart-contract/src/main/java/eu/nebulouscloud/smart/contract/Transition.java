package eu.nebulouscloud.smart.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

@DataType
@JsonPropertyOrder({ "firstSl", "secondSl", "evaluationPeriod", "violationThreshold" })
public class Transition {
    @Property
    @JsonProperty("firstSl")
    private int firstSl;

    @Property
    @JsonProperty("secondSl")
    private int secondSl;

    @Property
    @JsonProperty("evaluationPeriod")
    private String evaluationPeriod;

    @Property
    @JsonProperty("violationThreshold")
    private int violationThreshold;

    public Transition() {
    }

    public Transition(int firstSl, int secondSl, String evaluationPeriod, int violationThreshold) {
        this.firstSl = firstSl;
        this.secondSl = secondSl;
        this.evaluationPeriod = evaluationPeriod;
        this.violationThreshold = violationThreshold;
    }

    // Getters and Setters
    public int getFirstSl() {
        return firstSl;
    }

    public void setFirstSl(int firstSl) {
        this.firstSl = firstSl;
    }

    public int getSecondSl() {
        return secondSl;
    }

    public void setSecondSl(int secondSl) {
        this.secondSl = secondSl;
    }

    public String getEvaluationPeriod() {
        return evaluationPeriod;
    }

    public void setEvaluationPeriod(String evaluationPeriod) {
        this.evaluationPeriod = evaluationPeriod;
    }

    public int getViolationThreshold() {
        return violationThreshold;
    }

    public void setViolationThreshold(int violationThreshold) {
        this.violationThreshold = violationThreshold;
    }
}
