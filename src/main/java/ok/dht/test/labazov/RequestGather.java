package ok.dht.test.labazov;

import one.nio.http.HttpSession;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class RequestGather {
    private static final Logger LOG = LoggerFactory.getLogger(RequestGather.class);

    public final int acks;
    public final int froms;

    private final AtomicReferenceArray<Response> responses;
    private final AtomicInteger finishedTasks = new AtomicInteger(0);
    private final AtomicInteger failedTasks = new AtomicInteger(0);

    public RequestGather(int acks, int froms) {
        this.acks = acks;
        this.froms = froms;
        responses = new AtomicReferenceArray<>(froms);
    }

    public void submitGoodResponse(HttpSession session, Response response) {
        int sz = finishedTasks.incrementAndGet();
        responses.set(sz - 1, response);
        if (sz == acks) {
            long maxTimestamp = -1;
            Response result = null;
            for (int i = 0; i < acks; i++) {
                final Response resp = responses.get(i);
                final long timestamp = getTimestamp(resp);
                if (maxTimestamp < timestamp) {
                    maxTimestamp = timestamp;
                    result = resp;
                }
            }
            forwardResponse(session, result);
        }
    }

    private static long getTimestamp(Response resp) {
        final String timestampHeader = resp.getHeader("Timestamp: ");
        return timestampHeader == null ? 0 : Long.parseLong(timestampHeader);
    }

    public void submitFailure(HttpSession session) {
        int failed = failedTasks.incrementAndGet();
        if (froms - acks < failed) {
            forwardResponse(session, new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
        }
    }

    private void forwardResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            LOG.error("Failed to forward response ", e);
        }
    }

}
