package ok.dht.test.shik.validator;

import one.nio.http.Request;

import java.net.HttpURLConnection;
import java.util.Set;
import java.util.function.Function;

public class Validator {

    private static final String PATH_PREFIX = "/v0/entity";
    private static final String REQUESTED_REPLICAS = "from=";
    private static final String REQUIRED_REPLICAS = "ack=";
    private static final String TIMESTAMP = "timestamp=";
    private static final String ID_PARAM = "id=";
    private static final Set<Integer> SUPPORTED_METHODS = Set.of(
        Request.METHOD_GET,
        Request.METHOD_PUT,
        Request.METHOD_DELETE
    );

    private final int defaultAckCount;
    private final int defaultFromCount;

    public Validator(int clusterSize) {
        defaultFromCount = clusterSize;
        defaultAckCount = clusterSize / 2 + 1;
    }

    public ValidationResult validate(Request request) {
        String path = request.getPath();
        ValidationResult result = new ValidationResult();

        if (!path.startsWith(PATH_PREFIX)) {
            result.setCode(HttpURLConnection.HTTP_BAD_REQUEST);
            return result;
        }

        String id = request.getParameter(ID_PARAM);
        if (id == null || id.isEmpty()) {
            result.setCode(HttpURLConnection.HTTP_BAD_REQUEST);
            return result;
        }

        if (request.getMethod() == Request.METHOD_PUT && request.getBody() == null) {
            result.setCode(HttpURLConnection.HTTP_BAD_REQUEST);
            return result;
        }

        result.setId(id);
        return validateReplicationParams(request, result);
    }

    private ValidationResult validateReplicationParams(Request request, ValidationResult result) {
        int requestedReplicas;
        int requiredReplicas;
        long timestamp;
        try {
            requestedReplicas = getParamFromRequest(request, REQUESTED_REPLICAS,
                defaultFromCount, Integer::parseInt);
            requiredReplicas = getParamFromRequest(request, REQUIRED_REPLICAS,
                defaultAckCount, Integer::parseInt);
            timestamp = getParamFromRequest(request, TIMESTAMP, 0L, Long::parseLong);

            if (requiredReplicas <= 0 || requiredReplicas > requestedReplicas) {
                result.setCode(HttpURLConnection.HTTP_BAD_REQUEST);
                return result;
            }
        } catch (NumberFormatException e) {
            result.setCode(HttpURLConnection.HTTP_BAD_REQUEST);
            return result;
        }

        result.setRequestedReplicas(requestedReplicas);
        result.setRequiredReplicas(requiredReplicas);
        result.setTimestamp(timestamp);
        return validateRequestMethod(request, result);
    }

    private ValidationResult validateRequestMethod(Request request, ValidationResult result) {
        result.setCode(SUPPORTED_METHODS.contains(request.getMethod())
            ? HttpURLConnection.HTTP_OK : HttpURLConnection.HTTP_BAD_METHOD);
        return result;
    }

    private <T> T getParamFromRequest(Request request, String paramName,
                                      T defaultValue, Function<String, T> parseString) {
        String param = request.getParameter(paramName);
        return param == null ? defaultValue : parseString.apply(param);
    }

}
