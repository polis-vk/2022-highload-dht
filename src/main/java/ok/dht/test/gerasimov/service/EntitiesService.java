package ok.dht.test.gerasimov.service;

import ok.dht.test.gerasimov.client.CircuitBreakerClient;
import ok.dht.test.gerasimov.exception.EntityServiceException;
import ok.dht.test.gerasimov.sharding.ConsistentHash;
import ok.dht.test.gerasimov.utils.ResponseEntity;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class EntitiesService implements HandleService {
    private static final Set<Integer> ALLOWED_METHODS = Set.of(Request.METHOD_GET);
    private static final String ENDPOINT = "/v0/entities";

    private final ConsistentHash<String> consistentHash;
    private final DB dao;
    private final int port;
    private final CircuitBreakerClient client;

    public EntitiesService(ConsistentHash<String> consistentHash, DB dao, int port, CircuitBreakerClient client) {
        this.consistentHash = consistentHash;
        this.dao = dao;
        this.port = port;
        this.client = client;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) {
        Parameters parameters = isValidRequest(request, session);

        if (parameters == null) {
            return;
        }
    }

    @Override
    public String getEndpoint() {
        return ENDPOINT;
    }

    private Parameters isValidRequest(Request request, HttpSession session) {
        try {
            if (!ALLOWED_METHODS.contains(request.getMethod())) {
                session.sendResponse(ResponseEntity.methodNotAllowed());
                return null;
            }

            String start = request.getParameter("start=");
            String end = request.getParameter("end=");
            if (start == null || start.isEmpty()) {
                session.sendResponse(ResponseEntity.badRequest(
                                "The required <start> parameter was not passed"
                        )
                );
                return null;
            }

            try {
                return new Parameters(
                        start,
                        end
                );
            } catch (NumberFormatException e) {
                session.sendResponse(ResponseEntity.internalError("Invalid timestamp header"));
                return null;
            }
        } catch (IOException e) {
            throw new EntityServiceException("Can not send response", e);
        }
    }

    private static class Parameters {
        private final String start;
        private final String end;

        public Parameters(String start, String end) {
            this.start = start;
            this.end = end;
        }
    }
}
