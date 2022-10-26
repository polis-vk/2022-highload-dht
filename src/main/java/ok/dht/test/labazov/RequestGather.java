package ok.dht.test.labazov;

import one.nio.http.HttpSession;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RequestGather {
    private static final Logger LOG = LoggerFactory.getLogger(RequestGather.class);

    public final int acks;
    public final int froms;

    private final List<Response> responses = new ArrayList<>();
    private int finishedTasks = 0;
    private boolean replied;

    public RequestGather(int acks, int froms) {
        this.acks = acks;
        this.froms = froms;
    }

    public synchronized void submitGoodResponse(HttpSession session, Response response) {
        finishedTasks++;
        if (!replied) {
            responses.add(response);
            if (responses.size() >= acks) {
                replied = true;
                long maxTimestamp = -1;
                Response result = null;
                for (final Response resp : responses) {
                    final long timestamp = getTimestamp(resp);
                    if (maxTimestamp < timestamp) {
                        maxTimestamp = timestamp;
                        result = resp;
                    }
                }
                forwardResponse(session, result);
            } else {
                checkFailureAndTryResponse(session);
            }
        }
    }

    private static long getTimestamp(Response resp) {
        final String timestampHeader = resp.getHeader("Timestamp: ");
        return timestampHeader == null ? 0 : Long.parseLong(timestampHeader);
    }

    public synchronized void submitFailure(HttpSession session) {
        finishedTasks++;
        if (!replied) {
            checkFailureAndTryResponse(session);
        }
    }

    private void forwardResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            LOG.error("Failed to forward response ", e);
        }
    }

    private void checkFailureAndTryResponse(final HttpSession session) {
        if (froms - finishedTasks < acks - responses.size()) {
            replied = true;
            forwardResponse(session, new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
        }
    }
}
