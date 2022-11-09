package ok.dht.test.shik.validator;

public class ValidationResult {

    private int code;
    private String id;
    private int requestedReplicas;
    private int requiredReplicas;
    private long timestamp;

    public int getCode() {
        return code;
    }

    public String getId() {
        return id;
    }

    public int getRequestedReplicas() {
        return requestedReplicas;
    }

    public int getRequiredReplicas() {
        return requiredReplicas;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setRequestedReplicas(int requestedReplicas) {
        this.requestedReplicas = requestedReplicas;
    }

    public void setRequiredReplicas(int requiredReplicas) {
        this.requiredReplicas = requiredReplicas;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
