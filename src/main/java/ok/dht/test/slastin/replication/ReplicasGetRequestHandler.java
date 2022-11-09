package ok.dht.test.slastin.replication;

import ok.dht.test.slastin.SladkiiServer;
import one.nio.http.Response;

import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static ok.dht.test.slastin.Utils.notFound;

public class ReplicasGetRequestHandler extends ReplicasRequestHandler {
    final AtomicReference<ReplicaResponse> atomicMergedResponse;

    public ReplicasGetRequestHandler(String id, int ack, int from, SladkiiServer sladkiiServer) {
        super(id, ack, from, sladkiiServer);
        atomicMergedResponse = new AtomicReference<>();
    }

    @Override
    protected void tune() {
        // do nothing
    }

    @Override
    protected void whenComplete(CompletableFuture<Response> futureResponse) {
        futureResponse.whenComplete((r, t) -> {
            int status = r.getStatus();
            if (t != null || !(status == HttpURLConnection.HTTP_OK || status == HttpURLConnection.HTTP_NOT_FOUND)) {
                doFail();
                return;
            }

            if (status == HttpURLConnection.HTTP_OK) {
                ByteBuffer buffer = ByteBuffer.wrap(r.getBody());
                long timestamp = buffer.getLong();
                var currentResponse = new ReplicaResponse(timestamp, buffer);

                ReplicaResponse oldResponse;
                boolean repeat = true;
                while (repeat) {
                    oldResponse = atomicMergedResponse.get();
                    repeat = oldResponse == null || currentResponse.getTimestamp() > oldResponse.getTimestamp();
                    if (repeat) {
                        repeat = !atomicMergedResponse.compareAndSet(oldResponse, currentResponse);
                    }
                }
            }

            doAck();
        });
    }

    @Override
    protected Response merge() {
        ReplicaResponse mergedResponse = atomicMergedResponse.get();

        // check whether all responses were NOT_FOUND or last response was dead (tombstone)
        return mergedResponse == null || !mergedResponse.isAlive()
                ? notFound()
                : new Response(Response.OK, mergedResponse.getValue());
    }

    static class ReplicaResponse {
        private final long timestamp;
        private final ByteBuffer buffer;
        private Boolean isAlive;
        private byte[] value;

        ReplicaResponse(long timestamp, ByteBuffer buffer) {
            this.timestamp = timestamp;
            this.buffer = buffer;
            this.isAlive = null;
            this.value = null;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public ByteBuffer getBuffer() {
            return buffer;
        }

        public boolean isAlive() {
            if (isAlive == null) {
                isAlive = buffer.get() == 1;
            }
            return isAlive;
        }

        public byte[] getValue() {
            if (value == null) {
                isAlive();

                value = new byte[buffer.remaining()];
                buffer.get(value);
            }
            return value;
        }
    }
}
