package ok.dht.test.shakhov.http.response;

import ok.dht.test.shakhov.exception.NotEnoughAcksException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public final class ResponseHandler {
    private static final Logger log = LoggerFactory.getLogger(ResponseHandler.class);
    private static final NotEnoughAcksException NOT_ENOUGH_ACKS_EXCEPTION = new NotEnoughAcksException();

    private ResponseHandler() {
    }

    public static CompletableFuture<AtomicReferenceArray<ResponseWithTimestamp>>
    getAckResponses(Iterable<CompletableFuture<ResponseWithTimestamp>> responseCfs,
                    Set<Integer> ackStatuses,
                    int ack,
                    int from) {
        AtomicReferenceArray<ResponseWithTimestamp> responses = new AtomicReferenceArray<>(from);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);
        CompletableFuture<AtomicReferenceArray<ResponseWithTimestamp>> ackResponses = new CompletableFuture<>();
        for (CompletableFuture<ResponseWithTimestamp> responseCf : responseCfs) {
            responseCf.whenComplete((ResponseWithTimestamp r, Throwable t) -> {
                if (t == null && r != null && ackStatuses.contains(r.statusCode())) {
                    int successNum = successes.incrementAndGet();
                    responses.set(successNum - 1, r);
                    if (successNum >= ack) {
                        ackResponses.complete(responses);
                    }
                } else {
                    log.warn("Couldn't get ack, got {}", r, t);
                    int errorNum = errors.incrementAndGet();
                    if (errorNum > from - ack) {
                        ackResponses.completeExceptionally(NOT_ENOUGH_ACKS_EXCEPTION);
                    }
                }
            });
        }
        return ackResponses;
    }
}
