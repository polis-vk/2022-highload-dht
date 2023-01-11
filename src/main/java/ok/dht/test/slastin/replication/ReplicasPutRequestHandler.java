package ok.dht.test.slastin.replication;

import ok.dht.test.slastin.SladkiiServer;
import one.nio.http.Response;

import java.net.HttpURLConnection;
import java.util.concurrent.CompletableFuture;

import static ok.dht.test.slastin.Utils.created;

public class ReplicasPutRequestHandler extends ReplicasRequestHandler {

    public ReplicasPutRequestHandler(String id, int ack, int from, SladkiiServer sladkiiServer) {
        super(id, ack, from, sladkiiServer);
    }

    @Override
    protected void tune() {
        long timestamp = System.currentTimeMillis();
        request.addHeader("Timestamp: " + timestamp);
    }

    @Override
    protected void whenComplete(CompletableFuture<Response> futureResponse) {
        futureResponse.whenComplete((r, t) -> {
            if (t == null && r.getStatus() == HttpURLConnection.HTTP_CREATED) {
                doAck();
            } else {
                doFail();
            }
        });
    }

    @Override
    protected Response merge() {
        return created();
    }
}
