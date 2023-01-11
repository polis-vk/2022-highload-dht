package ok.dht.test.shik.validator;

public class ValidationResult {

    private int code;
    private String id;
    private int requestedReplicas;
    private int requiredReplicas;
    private long timestamp;
    private String start;
    private String end;

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

    public String getStart() {
        return start;
    }

    public String getEnd() {
        return end;
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

    public void setStart(String start) {
        this.start = start;
    }

    public void setEnd(String end) {
        this.end = end;
    }
}
