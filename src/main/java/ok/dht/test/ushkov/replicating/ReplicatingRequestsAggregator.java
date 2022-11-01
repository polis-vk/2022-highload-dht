package ok.dht.test.ushkov.replicating;

import one.nio.http.HttpSession;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;

public class ReplicatingRequestsAggregator {
    private final Logger log = LoggerFactory.getLogger(ReplicatingRequestsAggregator.class);
    private final HttpSession session;
    private final int ack;
    private final int from;
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicReferenceArray<Response> responses;
    private final Function<List<Response>, Response> aggregator;
    
    public ReplicatingRequestsAggregator(
            HttpSession session,
            int ack,
            int from,
            int clusterSize,
            Function<List<Response>, Response> aggregator
    ) {
        this.session = session;
        this.ack = ack;
        this.from = from;
        responses = new AtomicReferenceArray<>(clusterSize);
        this.aggregator = aggregator;
    }

    public final void success(Response response) {
        int count = successCount.incrementAndGet();
        responses.set(count - 1, response);

        if (count == ack) {
            List<Response> ackResponses = new ArrayList<>(ack);
            for (int i = 0; i < ack; ++i) {
                ackResponses.add(responses.get(i));
            }

            Response aggregatedResponse = aggregator.apply(ackResponses);

            String status = aggregatedResponse.getHeaders()[0];
            Response responseToClient = new Response(status, aggregatedResponse.getBody());

            try {
                session.sendResponse(responseToClient);
            } catch (IOException e) {
                log.error("Could not send response to client.");
            }
        }
    }

    public final void failure() {
        int count = failureCount.incrementAndGet();

        if (from - count < ack) {
            Response responseToClient = new Response("504 Not Enough Replicas",
                    Response.EMPTY);
            try {
                session.sendResponse(responseToClient);
            } catch (IOException e) {
                log.error("Could not send response to client.");
            }
        }
    }
}
