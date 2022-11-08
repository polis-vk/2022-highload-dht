package ok.dht.test.mikhaylov.internal;

import ok.dht.test.mikhaylov.DatabaseUtilities;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class ShardResponseProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ShardResponseProcessor.class);

    private static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";

    private final HttpSession session;

    private final ReplicaRequirements requirements;

    private final AtomicInteger acks = new AtomicInteger();

    private final AtomicInteger errors = new AtomicInteger();

    private final AtomicReferenceArray<Response> responses;

    private final int requestMethod;

    public ShardResponseProcessor(HttpSession session, ReplicaRequirements requirements, int requestMethod) {
        this.session = session;
        this.requirements = requirements;
        responses = new AtomicReferenceArray<>(requirements.getAck());
        if (requestMethod != Request.METHOD_GET && requestMethod != Request.METHOD_PUT && requestMethod != Request.METHOD_DELETE) {
            throw new IllegalArgumentException("Unsupported request method: " + requestMethod);
        }
        this.requestMethod = requestMethod;
    }

    public void process(@Nullable Response response) throws IOException {
        if (response == null) {
            int curErrors = this.errors.incrementAndGet();
            if (curErrors + requirements.getAck() > requirements.getFrom()) {
                session.sendResponse(new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY));
            }
            return;
        }
        int curAcks = this.acks.incrementAndGet();
        if (curAcks > requirements.getAck()) {
            return; // we have responded already or are responding right now
        }
        responses.set(curAcks - 1, response);
        if (curAcks < requirements.getAck()) {
            return;
        }
        switch (requestMethod) {
            case Request.METHOD_PUT -> session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
            case Request.METHOD_DELETE -> session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
            case Request.METHOD_GET -> {
                byte[] latestBody = null;
                long latestTimestamp = 0;
                boolean allNotFound = true;
                for (int i = 0; i < requirements.getAck(); i++) {
                    while (responses.get(i) == null) {
                        // a thread has already incremented acks but has not yet set the response
                        Thread.yield();
                    }
                    Response curResponse = responses.get(i);
                    if (curResponse.getStatus() == 404) {
                        continue;
                    }
                    allNotFound = false;
                    if (curResponse.getStatus() == 200) {
                        byte[] body = curResponse.getBody();
                        long curTimestamp = DatabaseUtilities.getTimestamp(body);
                        if (curTimestamp > latestTimestamp) {
                            latestTimestamp = curTimestamp;
                            latestBody = body;
                        }
                    }
                }
                if (allNotFound || latestBody == null || DatabaseUtilities.isTombstone(latestBody)) {
                    session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
                } else {
                    byte[] value = DatabaseUtilities.getValue(latestBody);
                    session.sendResponse(new Response(Response.OK, value));
                }
            }
            default -> logger.error("Unsupported request method: {}", requestMethod);
        }
    }
}
