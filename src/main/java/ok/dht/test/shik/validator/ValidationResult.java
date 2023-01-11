package ok.dht.test.shik.validator;

public class ValidationResult {

    private final int code;
    private final String id;
    private final int requestedReplicas;
    private final int requiredReplicas;

    public ValidationResult(int code, String id, int requestedReplicas, int requiredReplicas) {
        this.code = code;
        this.id = id;
        this.requestedReplicas = requestedReplicas;
        this.requiredReplicas = requiredReplicas;
    }

    public ValidationResult(int code) {
        this(code, null, 0, 0);
    }

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
}
