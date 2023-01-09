package ok.dht.test.slastin.replication;

import ok.dht.test.slastin.SladkiiServer;
import one.nio.http.Response;

import java.net.HttpURLConnection;

import static ok.dht.test.slastin.Utils.accepted;

public class ReplicasDeleteRequestHandler extends ReplicasRequestHandler {
    public ReplicasDeleteRequestHandler(String id, int ack, int from, SladkiiServer sladkiiServer) {
        super(id, ack, from, sladkiiServer);
    }

    @Override
    protected void before() {
        long timestamp = System.currentTimeMillis();
        request.addHeader("Timestamp: " + timestamp);
    }

    @Override
    protected Runnable createNodeTask(int nodeIndex) {
        return () -> {
            Response response = sladkiiServer.processRequest(nodeIndex, id, request);
            if (response.getStatus() == HttpURLConnection.HTTP_ACCEPTED) {
                doAck();
            } else {
                doFail();
            }
        };
    }

    @Override
    protected Response merge() {
        return accepted();
    }
}