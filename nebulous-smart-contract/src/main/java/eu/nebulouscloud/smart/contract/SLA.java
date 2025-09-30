package eu.nebulouscloud.smart.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;
import java.util.List;

@DataType
// ✅ Force JSON field order
@JsonPropertyOrder({
        "docType",
        "slaName",
        "status",
        "currentSl",
        "violations",
        "sls",
        "transitions",
        "metrics",
        "settlement"
})
public class SLA {
    @Property
    @JsonProperty("docType")
    private String docType;

    @Property
    @JsonProperty("slaName")
    private String slaName;

    @Property
    @JsonProperty("status")
    private String status; // ACTIVE, TERMINATED

    @Property
    @JsonProperty("currentSl")
    private int currentSl;

    @Property
    @JsonProperty("violations")
    private List<Violation> violations;

    @Property
    @JsonProperty("sls")
    private List<ServiceLevel> sls;

    @Property
    @JsonProperty("transitions")
    private List<Transition> transitions;

    @Property
    @JsonProperty("metrics")
    private List<Metric> metrics;

    @Property
    @JsonProperty("settlement")
    private Settlement settlement;

    public SLA() {
    }

    public SLA(String docType, String slaName, String status, int currentSl, List<Violation> violations,
            List<ServiceLevel> sls, List<Transition> transitions, List<Metric> metrics, Settlement settlement) {
        this.docType = docType;
        this.slaName = slaName;
        this.status = status;
        this.currentSl = currentSl;
        this.violations = violations;
        this.sls = sls;
        this.transitions = transitions;
        this.metrics = metrics;
        this.settlement = settlement;
    }

    // Getters and Setters
    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getSlaName() {
        return slaName;
    }

    public void setSlaName(String slaName) {
        this.slaName = slaName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCurrentSl() {
        return currentSl;
    }

    public void setCurrentSl(int currentSl) {
        this.currentSl = currentSl;
    }

    public List<Violation> getViolations() {
        return violations;
    }

    public void setViolations(List<Violation> violations) {
        this.violations = violations;
    }

    public List<ServiceLevel> getSls() {
        return sls;
    }

    public void setSls(List<ServiceLevel> sls) {
        this.sls = sls;
    }

    public List<Transition> getTransitions() {
        return transitions;
    }

    public void setTransitions(List<Transition> transitions) {
        this.transitions = transitions;
    }

    public List<Metric> getMetrics() {
        return metrics;
    }

    public void setMetrics(List<Metric> metrics) {
        this.metrics = metrics;
    }

    public Settlement getSettlement() {
        return settlement;
    }

    public void setSettlement(Settlement settlement) {
        this.settlement = settlement;
    }
}