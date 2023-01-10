package ok.dht.test.garanin;

import ok.dht.test.garanin.db.Value;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ResponsesMerger {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResponsesMerger.class);

    private final HttpSession session;
    private final int ack;
    private final int from;
    private final AtomicInteger allResponseCount = new AtomicInteger(0);
    private final AtomicInteger goodResponseCount = new AtomicInteger(0);
    private final AtomicReference<ResponseEntity> mergedResponse = new AtomicReference<>(null);

    public ResponsesMerger(HttpSession session, int ack, int from) {
        this.session = session;
        this.ack = ack;
        this.from = from;
    }

    public void acceptJava(HttpResponse<byte[]> response) {
        if (response == null) {
            incAll();
            return;
        }
        acceptNio(new Response(convertStatus(response.statusCode()), response.body()));
    }

    public void acceptNio(Response response) {
        if (response.getStatus() / 100 == 5) {
            incAll();
            return;
        }
        incGood(response);
    }

    private void incAll() {
        if (allResponseCount.incrementAndGet() == from) {
            sendResponse(new Response("504 Not Enough Replicas", Response.EMPTY));
        }
    }

    private void incGood(Response response) {
        int good = goodResponseCount.incrementAndGet();
        if (good <= ack) {
            merge(new ResponseEntity(response));
            if (good == ack) {
                allResponseCount.set(-from);
                ResponseEntity responseEntity = mergedResponse.get();
                sendResponse(responseEntity.toResponse());
            }
        }
        incAll();
    }

    private void merge(ResponseEntity newResponseEntity) {
        while (true) {
            Value value = newResponseEntity.value();
            ResponseEntity currentResponse = mergedResponse.get();
            if (currentResponse == null
                    || currentResponse.value().timestamp() < value.timestamp()
                    || (value.tombstone() && currentResponse.value().timestamp() == value.timestamp())) {
                if (mergedResponse.compareAndSet(currentResponse, newResponseEntity)) {
                    return;
                }
            } else {
                return;
            }
        }

    }

    private void sendResponse(Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
            session.close();
        }
    }

    private static String convertStatus(int statusCode) {
        return switch (statusCode) {
            case HttpURLConnection.HTTP_OK -> Response.OK;
            case HttpURLConnection.HTTP_CREATED -> Response.CREATED;
            case HttpURLConnection.HTTP_ACCEPTED -> Response.ACCEPTED;
            case HttpURLConnection.HTTP_NO_CONTENT -> Response.NO_CONTENT;
            case HttpURLConnection.HTTP_SEE_OTHER -> Response.SEE_OTHER;
            case HttpURLConnection.HTTP_NOT_MODIFIED -> Response.NOT_MODIFIED;
            case HttpURLConnection.HTTP_USE_PROXY -> Response.USE_PROXY;
            case HttpURLConnection.HTTP_BAD_REQUEST -> Response.BAD_REQUEST;
            case HttpURLConnection.HTTP_UNAUTHORIZED -> Response.UNAUTHORIZED;
            case HttpURLConnection.HTTP_PAYMENT_REQUIRED -> Response.PAYMENT_REQUIRED;
            case HttpURLConnection.HTTP_FORBIDDEN -> Response.FORBIDDEN;
            case HttpURLConnection.HTTP_NOT_FOUND -> Response.NOT_FOUND;
            case HttpURLConnection.HTTP_NOT_ACCEPTABLE -> Response.NOT_ACCEPTABLE;
            case HttpURLConnection.HTTP_CONFLICT -> Response.CONFLICT;
            case HttpURLConnection.HTTP_GONE -> Response.GONE;
            case HttpURLConnection.HTTP_LENGTH_REQUIRED -> Response.LENGTH_REQUIRED;
            case HttpURLConnection.HTTP_INTERNAL_ERROR -> Response.INTERNAL_ERROR;
            case HttpURLConnection.HTTP_NOT_IMPLEMENTED -> Response.NOT_IMPLEMENTED;
            case HttpURLConnection.HTTP_BAD_GATEWAY -> Response.BAD_GATEWAY;
            case HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> Response.GATEWAY_TIMEOUT;
            default -> throw new IllegalArgumentException("Status code " + statusCode + " not implemented");
        };
    }

    private static class ResponseEntity {

        private final int status;
        private final Value value;

        private ResponseEntity(Response response) {
            this.status = response.getStatus();
            this.value = response.getBody() != null ? new Value(response.getBody()) : null;
        }

        public Value value() {
            return value;
        }

        private Response toResponse() {
            return new Response(convertStatus(status), value.tombstone() ? Response.EMPTY : value.data());
        }

    }
}
