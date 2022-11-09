package ok.dht.test.panov;

import one.nio.http.HttpSession;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ResponseResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseResolver.class);

    private final ReplicasAcknowledgment replicasAcknowledgment;
    private final AtomicInteger acknowledgedReqs = new AtomicInteger(0);
    private final AtomicInteger readyReqs = new AtomicInteger(0);
    private final AtomicInteger totalReqs = new AtomicInteger(0);
    private final AtomicReference<Response> actualResponse = new AtomicReference<>(null);

    private final ExecutorService executorService;

    public ResponseResolver(ReplicasAcknowledgment replicasAcknowledgment, ExecutorService executorService) {
        this.replicasAcknowledgment = replicasAcknowledgment;
        this.executorService = executorService;
    }

    public void add(CompletableFuture<Response> response, HttpSession session) {
        response.whenCompleteAsync((resp, throwable) -> {
            int curTotalReqs = totalReqs.incrementAndGet();

            int curReadyReqs = readyReqs.get();

            try {
                if (throwable == null && resp.getStatus() < 500) {
                    int ackReqs = acknowledgedReqs.incrementAndGet();
                    curReadyReqs = handleValidResponse(ackReqs, resp);

                    if (curReadyReqs == replicasAcknowledgment.ack) {
                        session.sendResponse(actualResponse.get());
                    }
                }

                if (curTotalReqs == replicasAcknowledgment.from && curReadyReqs < replicasAcknowledgment.ack) {
                    session.sendResponse(new Response("504 Not Enough Replicas", Response.EMPTY));
                }
            } catch (IOException e) {
                LOGGER.error("Error during response sending");
            }
        }, executorService);
    }

    private int handleValidResponse(int ackReqs, Response resp) {
        while (true) {
            Response curResponse = actualResponse.get();
            Response relevantResponse = moreRelevant(curResponse, resp);

            if (ackReqs <= replicasAcknowledgment.ack
                    && actualResponse.compareAndSet(curResponse, relevantResponse)) {
                return readyReqs.incrementAndGet();
            }
        }
    }

    private static Response moreRelevant(Response first, Response second) {
        if (first == null) {
            return second;
        } else if (second == null) {
            return first;
        } else {
            String firstHeader = first.getHeader(ServiceImpl.DELEGATE_HEADER);
            String secondHeader = second.getHeader(ServiceImpl.DELEGATE_HEADER);

            if (firstHeader == null) {
                return second;
            } else if (secondHeader == null) {
                return first;
            } else {
                long firstTimestamp = Long.parseLong(firstHeader);
                long secondTimestamp = Long.parseLong(secondHeader);

                if (firstTimestamp > secondTimestamp) {
                    return first;
                } else {
                    return second;
                }
            }
        }
    }
}
