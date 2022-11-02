package ok.dht.test.nadutkin.impl.replicas;

import ok.dht.test.nadutkin.impl.utils.Constants;
import ok.dht.test.nadutkin.impl.utils.StoredValue;
import ok.dht.test.nadutkin.impl.utils.UtilsClass;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ResponseProcessor {
    private final AtomicReference<ControllerStatus> status;
    private final Integer method;
    private final AtomicInteger need;
    private final AtomicInteger left;

    public ResponseProcessor(Integer method, Integer ack, Integer from) {
        this.method = method;
        this.status = new AtomicReference<>(new ControllerStatus());
        this.need = new AtomicInteger(ack);
        this.left = new AtomicInteger(from);
    }

    public boolean process(Response response) {
        if (response != null) {
            if (response.getStatus() == HttpURLConnection.HTTP_OK) {
                try {
                    StoredValue value = UtilsClass.segmentToValue(response.getBody());
                    ControllerStatus newStatus = new ControllerStatus(value.timestamp(), value.value());
                    while (true) {
                        ControllerStatus currentStatus = status.get();
                        if (newStatus.timestamp > currentStatus.timestamp) {
                            if (status.compareAndSet(currentStatus, newStatus)) {
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                } catch (IOException | ClassNotFoundException ignored) {
                    Constants.LOG.error("Cannot get value from response");
                }
            }
            if (need.decrementAndGet() == 0) {
                return true;
            }
        }
        return left.decrementAndGet() == 0;
    }

    public Response response() {
        if (need.get() > 0) {
            return new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY);
        }
        switch (this.method) {
            case Request.METHOD_GET -> {
                ControllerStatus currentStatus = status.get();
                if (currentStatus.answer != null) {
                    return new Response(Response.OK, currentStatus.answer);
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
