package ok.dht.test.shik.events;

import ok.dht.test.shik.model.DBValue;

import java.util.List;

public class HandlerLeaderResponse extends HandlerResponse {

    private List<String> inconsistentReplicas;
    private DBValue actualValue;
    private boolean equalDigests;

    public List<String> getInconsistentReplicas() {
        return inconsistentReplicas;
    }

    public void setInconsistentReplicas(List<String> inconsistentReplicas) {
        this.inconsistentReplicas = inconsistentReplicas;
    }

    public DBValue getActualValue() {
        return actualValue;
    }

    public void setActualValue(DBValue actualValue) {
        this.actualValue = actualValue;
    }

    public boolean isEqualDigests() {
        return equalDigests;
    }

    public void setEqualDigests(boolean equalDigests) {
        this.equalDigests = equalDigests;
    }
}
