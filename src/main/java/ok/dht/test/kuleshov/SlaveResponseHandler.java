package ok.dht.test.kuleshov;

import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static ok.dht.test.kuleshov.utils.ResponseUtils.emptyResponse;

public class SlaveResponseHandler {
    private final int ack;
    private final int from;
    private final AtomicInteger ackCount = new AtomicInteger(0);
    private final AtomicInteger fromCount = new AtomicInteger(0);
    private final HttpSession session;
    private final AtomicReference<HandleResponse> lastResponse = new AtomicReference<>(null);
    private final Logger log = LoggerFactory.getLogger(SlaveResponseHandler.class);

    public SlaveResponseHandler(int ack, int from, HttpSession session) {
        this.ack = ack;
        this.from = from;
        this.session = session;
    }

    public void handleFrom() {
        int currentAll = fromCount.incrementAndGet();

        if (currentAll == from && ack > ackCount.get()) {
            try {
                session.sendResponse(new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
            } catch (IOException e) {
                session.close();
            }
        }
    }

    public boolean handleAck(Response response) {
        int currentAck = ackCount.incrementAndGet();

        if (currentAck == ack) {
            sendResponse(response);
            return true;
        }

        return false;
    }

    private void sendResponse(Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            log.error(e.getMessage());
            session.close();
        }
    }

    public void handleResponse(int method, HandleResponse response) {
        switch (method) {
            case Request.METHOD_PUT -> {
                if (response.getStatusCode() == 201) {
                    if (handleAck(new Response(Response.CREATED, Response.EMPTY))) {
                        return;
                    }
                }
            }
            case Request.METHOD_DELETE -> {
                if (response.getStatusCode() == 202) {
                    handleAck(new Response(Response.ACCEPTED, Response.EMPTY));
                }
            }
            case Request.METHOD_GET -> {
                if (response.getStatusCode() == 200 || response.getStatusCode() == 404) {
                    while (true) {
                        HandleResponse currentLastResponse = lastResponse.get();
                        if (currentLastResponse == null
                                || response.getTimestamp() > currentLastResponse.getTimestamp()) {
                            if (lastResponse.compareAndSet(currentLastResponse, response)) {
                                break;
                            }
                        } else {
                            break;
                        }
                    }

                    HandleResponse currentLastResponse = lastResponse.get();
                    handleAck(new Response(currentLastResponse.getStringStatusCode(), currentLastResponse.getBody()));
                }
            }
            default -> sendResponse(emptyResponse(Response.METHOD_NOT_ALLOWED));
        }

        handleFrom();
    }
}
