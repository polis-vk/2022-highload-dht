package ok.dht.test.slastin.replication;

import ok.dht.test.slastin.SladkiiServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.RequestHandler;
import one.nio.http.Response;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static ok.dht.test.slastin.Utils.notEnoughReplicas;

public abstract class ReplicasRequestHandler implements RequestHandler {
    protected final String id;
    protected final int ack;
    protected final int from;
    protected final SladkiiServer sladkiiServer;

    protected final AtomicInteger ackCounter;
    protected final AtomicInteger failCounter;

    protected Request request;
    protected HttpSession session;

    public ReplicasRequestHandler(String id, int ack, int from, SladkiiServer sladkiiServer) {
        this.id = id;
        this.ack = ack;
        this.from = from;
        this.sladkiiServer = sladkiiServer;
        ackCounter = new AtomicInteger(0);
        failCounter = new AtomicInteger(0);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        this.request = request;
        this.session = session;

        before();

        for (int nodeIndex : sladkiiServer.getShardingManager().getNodeIndices(id, from)) {
            Runnable nodeTask = createNodeTask(nodeIndex);
            if (!sladkiiServer.tryAddNodeTask(session, nodeIndex, nodeTask)) {
                doFail();
            }
        }
    }

    protected abstract void before();

    protected abstract Runnable createNodeTask(int nodeIndex);

    protected void doAck() {
        int currentAck = ackCounter.incrementAndGet();
        if (currentAck == ack) {
            Response response = merge();
            sladkiiServer.sendResponse(session, response);
        }
    }

    protected abstract Response merge();

    protected void doFail() {
        int currentFail = failCounter.incrementAndGet();
        if (from - currentFail == ack - 1) {
            sladkiiServer.sendResponse(session, notEnoughReplicas());
        }
    }
}
