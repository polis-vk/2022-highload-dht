package ok.dht.test.nadutkin.impl.replicas;

import ok.dht.test.nadutkin.impl.utils.Constants;
import ok.dht.test.nadutkin.impl.utils.StoredValue;
import ok.dht.test.nadutkin.impl.utils.UtilsClass;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.IOException;
import java.net.HttpURLConnection;

public class ResponseProcessor {
    private Long timestamp = -1L;
    private byte[] answer = null;
    private final Integer method;
    private int need;

    public ResponseProcessor(Integer method, Integer ack) {
        this.method = method;
        this.need = ack;
    }

    public boolean process(Response response) {
        if (response == null) {
            return false;
        }
        if (response.getStatus() == HttpURLConnection.HTTP_OK) {
            try {
                StoredValue value = UtilsClass.segmentToValue(response.getBody());
                need--;
                if (value.timestamp() > timestamp) {
                    answer = value.value();
                    timestamp = value.timestamp();
                }
            } catch (IOException | ClassNotFoundException ignored) {
                Constants.LOG.error("Cannot get value from response");
            }
        } else {
            need--;
        }
        return need == 0;
    }

    public Response response() {
        if (need != 0) {
            return new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY);
        }
        switch (this.method) {
            case Request.METHOD_GET -> {
                if (answer != null) {
                    return new Response(Response.OK, answer);
                } else {
                    return new Response(Response.NOT_FOUND, Response.EMPTY);
                }
            }
            case Request.METHOD_PUT -> {
                return new Response(Response.CREATED, Response.EMPTY);
            }
            case Request.METHOD_DELETE -> {
                return new Response(Response.ACCEPTED, Response.EMPTY);
            }
            default -> {
                return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            }
        }
    }
}
