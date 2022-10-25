package ok.dht.test.slastin.replication;

import ok.dht.test.slastin.SladkiiServer;
import one.nio.http.Response;

import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import static ok.dht.test.slastin.Utils.notFound;

public class ReplicasGetRequestHandler extends ReplicasRequestHandler {
    Queue<Response> responses;

    public ReplicasGetRequestHandler(String id, int ack, int from, SladkiiServer sladkiiServer) {
        super(id, ack, from, sladkiiServer);
        responses = new LinkedBlockingQueue<>(ack);
    }

    @Override
    protected void before() {
        // don't have to upgrade initial request for get query
    }

    @Override
    protected Runnable createNodeTask(int nodeIndex) {
        return () -> {
            Response response = sladkiiServer.processRequest(nodeIndex, id, request);
            int status = response.getStatus();
            if (status == HttpURLConnection.HTTP_OK || status == HttpURLConnection.HTTP_NOT_FOUND) {
                int currentAck = ackCounter.incrementAndGet();
                if (currentAck <= ack) {
                    responses.offer(response);
                }
                if (currentAck == ack) {
                    sladkiiServer.sendResponse(session, merge());
                }
            } else {
                doFail();
            }
        };
    }

    @Override
    protected Response merge() {
        int size = responses.size();
        while (size != ack) {
            // just wait for it (contention doesn't have to be long (see report))
            size = responses.size();
        }

        ByteBuffer resultBuffer = getResultBuffer();

        // check whether all responses were NOT_FOUND or last response was dead (tombstone)
        if (resultBuffer == null || resultBuffer.get() == 0) {
            return notFound();
        }

        byte[] resultValue = new byte[resultBuffer.remaining()];
        resultBuffer.get(resultValue);
        return new Response(Response.OK, resultValue);
    }

    private ByteBuffer getResultBuffer() {
        long resultTimestamp = -1;
        ByteBuffer resultBuffer = null;
        for (Response response : responses) {
            if (response.getStatus() != HttpURLConnection.HTTP_OK) {
                continue;
            }
            ByteBuffer buffer = ByteBuffer.wrap(response.getBody());
            long timestamp = buffer.getLong();
            if (timestamp > resultTimestamp) {
                resultTimestamp = timestamp;
                resultBuffer = buffer;
            }
        }
        return resultBuffer;
    }
}
