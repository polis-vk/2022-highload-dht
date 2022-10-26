package ok.dht.test.ushkov;

import one.nio.http.HttpSession;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ReplicatedRequest {
    private static final String TIMESTAMP_HEADER = "Timestamp: ";

    public final int ack;
    public final int from;

    private final List<Response> responses = new ArrayList<>();
    private final AtomicInteger finishedTasks = new AtomicInteger(0);
    private boolean responseSent;

    private final Logger log = LoggerFactory.getLogger(ReplicatedRequest.class);

    public ReplicatedRequest(int ack, int from) {
        this.ack = ack;
        this.from = from;
    }

    public synchronized void onSuccess(HttpSession session, Response response) {
        responses.add(response);
        finishedTasks.incrementAndGet();
        if (!responseSent && isAckReached()) {
            responseSent = true;
            sendResponseNothrow(session, findLatest());
        } else if (!responseSent && isAckCouldNotBeReached()) {
            responseSent = true;
            sendResponseNothrow(session,
                    new Response("504 Not Enough Replicas", Response.EMPTY));
        }
    }

    public synchronized void onFailure(HttpSession session) {
        finishedTasks.incrementAndGet();
        if (!responseSent && isAckCouldNotBeReached()) {
            responseSent = true;
            sendResponseNothrow(session,
                    new Response("504 Not Enough Replicas", Response.EMPTY));
        }
    }

    private void sendResponseNothrow(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            log.error("Could not send response to client", e);
        }
    }

    private boolean isAckReached() {
        return responses.size() >= ack;
    }

    private boolean isAckCouldNotBeReached() {
        return ack - responses.size() > from - finishedTasks.get();
    }

    private Response findLatest() {
        long resultTimestamp = -1;
        Response resultResponse = null;
        for (Response response : responses) {
            if (resultTimestamp < getTimestamp(response)) {
                resultTimestamp = getTimestamp(response);
                resultResponse = response;
            }
        }
        return resultResponse;
    }

    private static long getTimestamp(Response response) {
        return Long.parseLong(response.getHeader(TIMESTAMP_HEADER));
    }
}
