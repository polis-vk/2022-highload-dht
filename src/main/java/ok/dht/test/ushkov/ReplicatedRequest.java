package ok.dht.test.ushkov;

import one.nio.http.HttpSession;
import one.nio.http.Response;

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
    private boolean responseSent = false;
    private Response response = null;

    public ReplicatedRequest(int ack, int from) {
        this.ack = ack;
        this.from = from;
    }

    public synchronized void onSuccess(HttpSession session, Response response) {
        responses.add(response);
        finishedTasks.incrementAndGet();
        if (!responseSent && isAckReached()) {
            responseSent = true;
            try {
                session.sendResponse(findLatest());
            } catch (IOException e) {
                // No ops.
            }
        } else if (!responseSent && isAckCouldNotBeReached()) {
            responseSent = true;
            try {
                session.sendResponse(new Response("504 Not Enough Replicas", Response.EMPTY));
            } catch (IOException e) {
                // No ops.
            }
        }
    }

    public synchronized void onFailure(HttpSession session) {
        finishedTasks.incrementAndGet();
        if (!responseSent && isAckCouldNotBeReached()) {
            responseSent = true;
            try {
                session.sendResponse(new Response("504 Not Enough Replicas", Response.EMPTY));
            } catch (IOException e) {
                // No ops.
            }
        }
    }

    public Response getResponse() {
        return response;
    }

    private boolean isAckReached() {
        int count = 0;
        for (Response response : responses) {
            if (Set.of(200, 201, 202, 404).contains(response.getStatus())) {
                count++;
            }
        }
        return count >= ack;
    }

    private boolean isAckCouldNotBeReached() {
        return ack > from - finishedTasks.get();
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
