package ok.dht.test.shik.validator;

import one.nio.http.Request;

import java.net.HttpURLConnection;
import java.util.Set;

public class Validator {

    private static final String PATH_PREFIX = "/v0/entity";
    private static final String REQUESTED_REPLICAS = "from=";
    private static final String REQUIRED_REPLICAS = "ack=";
    private static final String ID_PARAM = "id=";
    private static final Set<Integer> SUPPORTED_METHODS = Set.of(
        Request.METHOD_GET,
        Request.METHOD_PUT,
        Request.METHOD_DELETE
    );

    private final int defaultReplicasCount;

    public Validator(int defaultReplicasCount) {
        this.defaultReplicasCount = defaultReplicasCount;
    }

    public ValidationResult validate(Request request) {
        String path = request.getPath();
        if (!path.startsWith(PATH_PREFIX)) {
            return new ValidationResult(HttpURLConnection.HTTP_BAD_REQUEST);
        }

        String id = request.getParameter(ID_PARAM);
        if (id == null || id.isEmpty()) {
            return new ValidationResult(HttpURLConnection.HTTP_BAD_REQUEST);
        }

        if (request.getMethod() == Request.METHOD_PUT && request.getBody() == null) {
            return new ValidationResult(HttpURLConnection.HTTP_BAD_REQUEST);
        }

        return validateReplicationParams(request, id);
    }

    private ValidationResult validateReplicationParams(Request request, String id) {
        int requestedReplicas;
        int requiredReplicas;
        try {
            requestedReplicas = getIntParamFromRequest(request, REQUESTED_REPLICAS);
            requiredReplicas = getIntParamFromRequest(request, REQUIRED_REPLICAS);

            if (requiredReplicas <= 0 || requiredReplicas > requestedReplicas) {
                return new ValidationResult(HttpURLConnection.HTTP_BAD_REQUEST);
            }
        } catch (NumberFormatException e) {
            return new ValidationResult(HttpURLConnection.HTTP_BAD_REQUEST);
        }

        return validateRequestMethod(request, id, requestedReplicas, requiredReplicas);
    }

    private ValidationResult validateRequestMethod(Request request, String id,
                                                   int requestedReplicas, int requiredReplicas) {
        if (!SUPPORTED_METHODS.contains(request.getMethod())) {
            return new ValidationResult(HttpURLConnection.HTTP_BAD_METHOD);
        }

        return new ValidationResult(HttpURLConnection.HTTP_OK, id, requestedReplicas, requiredReplicas);
    }

    private int getIntParamFromRequest(Request request, String paramName) {
        String requestedReplicasParam = request.getParameter(paramName);
        if (requestedReplicasParam != null) {
            return Integer.parseInt(requestedReplicasParam);
        }
        return defaultReplicasCount;
    }

}
