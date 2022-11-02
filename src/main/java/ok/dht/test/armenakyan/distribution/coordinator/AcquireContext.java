package ok.dht.test.armenakyan.distribution.coordinator;

import ok.dht.test.armenakyan.distribution.model.Value;
import ok.dht.test.armenakyan.util.ServiceUtils;
import one.nio.http.HttpSession;
import one.nio.http.Response;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class AcquireContext {
    private final HttpSession session;
    private final AtomicInteger ackLatch;
    private final AtomicInteger totalLatch;
    private final AtomicReference<ResponseHolder> responseHolder;

    AcquireContext(int ack, int from, HttpSession session) {
        this.session = session;
        this.ackLatch = new AtomicInteger(ack);
        this.totalLatch = new AtomicInteger(from);
        this.responseHolder = new AtomicReference<>(null);
    }

    public void acquireResponse(Response response) {
        if (isFailedResponse(response)) {
            decrementTotal();
            return;
        }
        int currentAckLatch = ackLatch.decrementAndGet();
        if (currentAckLatch >= 0) {
            mergeAcquired(response);

            if (currentAckLatch == 0) {
                totalLatch.incrementAndGet();
                ResponseHolder resultingHolder = responseHolder.get();
                ServiceUtils.sendResponse(session, resultingHolder.response());
                return;
            }
        }
        decrementTotal();
    }

    private void decrementTotal() {
        if (totalLatch.decrementAndGet() == 0) {
            ServiceUtils.sendResponse(session, new Response(ServiceUtils.NOT_ENOUGH_REPLICAS, Response.EMPTY));
        }
    }

    private void mergeAcquired(Response response) {
        ResponseHolder currentHolder = responseHolder.get();
        ResponseHolder newHolder = new ResponseHolder(response);
        do {
            if (currentHolder != null && isLatest(newHolder.value(), currentHolder.value())) {
                break;
            }
            currentHolder = responseHolder.get();
        } while (!responseHolder.compareAndSet(currentHolder, newHolder));
    }

    private static boolean isLatest(Value initial, Value current) {
        if (current == null) {
            return false;
        }
        if (initial == null || initial.timestamp() < current.timestamp()) {
            return true;
        }
        if (initial.timestamp() == current.timestamp()) {
            return !initial.isTombstone();
        }

        return false;
    }

    private static boolean isFailedResponse(Response response) {
        return response.getStatus() / 100 == 5;
    }

    private static class ResponseHolder {
        private final Response response;

        private final Value value;

        public ResponseHolder(Response response) {
            this.response = response;

            if (response.getBody() == null || response.getBody().length == 0) {
                this.value = null;
                return;
            }

            this.value = Value.fromBytes(response.getBody());
        }

        public int code() {
            return response.getStatus();
        }

        public Value value() {
            return value;
        }

        public Response response() {
            return new Response(
                    String.valueOf(response.getStatus()),
                    value == null || value.isTombstone() ? Response.EMPTY : value.value()
            );
        }
    }
}
