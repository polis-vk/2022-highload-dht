package ok.dht.test.slastin.replication;

import ok.dht.test.slastin.SladkiiServer;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.RequestHandler;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static ok.dht.test.slastin.Utils.notEnoughReplicas;

public abstract class ReplicasRequestHandler implements RequestHandler {
    private static final Logger log = LoggerFactory.getLogger(ReplicasRequestHandler.class);

    protected final String id;
    protected final int ack;
    protected final int from;
    protected final SladkiiServer sladkiiServer;

    protected final AtomicInteger ackCounter;
    protected final AtomicInteger failCounter;

    protected Request request;
    protected HttpSession session;
    protected List<CompletableFuture<Response>> futureResponses;

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

        tune();

        futureResponses = new ArrayList<>(from);
        for (var nodeIndex : sladkiiServer.getShardingManager().getNodeIndices(id, from)) {
            try {
                futureResponses.add(sladkiiServer.futureRequest(nodeIndex, id, request));
            } catch (RejectedExecutionException e) {
                processRejectedExecution(e);
            }
        }

        for (var futureResponse : futureResponses) {
            try {
                whenComplete(futureResponse);
            } catch (RejectedExecutionException e) {
                processRejectedExecution(e);
            }
        }
    }

    protected abstract void tune();

    protected abstract void whenComplete(CompletableFuture<Response> futureResponse);

    protected abstract Response merge();

    protected void doAck() {
        int currentAck = ackCounter.incrementAndGet();
        if (currentAck == ack) {
            Response response = merge();

            sladkiiServer.sendResponse(session, response);

            for (var futureResponse : futureResponses) {
                if (!futureResponse.isDone()) {
                    futureResponse.cancel(true);
                }
            }
        }
    }

    protected void doFail() {
        int currentFail = failCounter.incrementAndGet();
        if (from - currentFail == ack - 1) {
            sladkiiServer.sendResponse(session, notEnoughReplicas());
        }
    }

    protected void processRejectedExecution(RejectedExecutionException e) {
        log.error("Can not schedule replication task for execution", e);
        doFail();
    }
}
