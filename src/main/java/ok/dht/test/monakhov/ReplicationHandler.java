package ok.dht.test.monakhov;

import ok.dht.test.monakhov.model.EntryWrapper;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.net.HttpURLConnection.HTTP_ACCEPTED;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_OK;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseAccepted;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseCreated;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseNotEnoughReplicas;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseNotFound;
import static ok.dht.test.monakhov.utils.ServiceUtils.responseOk;
import static one.nio.serial.Serializer.deserialize;

public class ReplicationHandler {
    private static final Log log = LogFactory.getLog(ReplicationHandler.class);
    public final AtomicInteger atomicFinished = new AtomicInteger(0);
    public final AtomicInteger atomicResponded = new AtomicInteger(0);
    public final AtomicInteger atomicAcked = new AtomicInteger(0);
    public final AtomicReference<EntryWrapper> atomicLastEntry = new AtomicReference<>();
    public final Request request;
    public final HttpSession session;
    public final int ack;
    public final int from;
    public final String selfUrl;
    public final String key;

    public ReplicationHandler(Request request, HttpSession session, int ack, int from, String selfUrl, String key) {
        this.request = request;
        this.ack = ack;
        this.from = from;
        this.session = session;
        this.selfUrl = selfUrl;
        this.key = key;
    }

    public void handleLocalResponse(Response response, String nodeUrl) {
        try {
            handleNodeResponse(response.getStatus(), response.getBody(), nodeUrl);
            tryFinishReplication(nodeUrl);
        } catch (IOException e) {
            log.error("Error occurred while responding to client", e);
        }
    }

    public void handleInternalResponse(HttpResponse<byte[]> response, Throwable e, String nodeUrl) {
        try {
            int responded = atomicResponded.incrementAndGet();

            if (response != null) {
                log.debug(String.format(
                    "Redirected response from node: %s received. This node: %s. Key: %s. Status %s. Responded %s/%s",
                    nodeUrl, selfUrl, key, response.statusCode(), responded, from)
                );

                handleNodeResponse(response.statusCode(), response.body(), nodeUrl);
            }

            if (e != null) {
                log.error(String.format(
                    "Connection error from node: %s received. This node: %s. Key: %s. Responded %s/%s",
                    nodeUrl, selfUrl, key, responded, from), e
                );
            }

            tryFinishReplication(nodeUrl);
        } catch (IOException ex) {
            log.error("Error occurred while responding to client", ex);
        }
    }

    private void tryFinishReplication(String nodeUrl) throws IOException {
        // must be called only after section with atomicAcked changes is finished!!!!
        int finished = atomicFinished.incrementAndGet();
        int acked = atomicAcked.get();

        if (finished == from && acked < ack) {
            logAndSendClientResponse(responseNotEnoughReplicas(), nodeUrl);
        }
    }

    private void handleNodeResponse(int statusCode, byte[] body, String nodeUrl) throws IOException {
        switch (request.getMethod()) {
        case Request.METHOD_GET -> {
            if (statusCode == HTTP_OK) {
                try {
                    EntryWrapper newEntry = (EntryWrapper) deserialize(body);

                    while (true) {
                        EntryWrapper oldEntry = atomicLastEntry.get();

                        if (oldEntry != null && oldEntry.compareTo(newEntry) >= 0) {
                            break;
                        }

                        if (atomicLastEntry.compareAndSet(oldEntry, newEntry)) {
                            break;
                        }
                    }
                } catch (IOException | ClassNotFoundException ex) {
                    log.error("Error occurred while deserialization", ex);
                }
            }

            int acked = atomicAcked.incrementAndGet();
            EntryWrapper last = atomicLastEntry.get();

            if (acked == ack) {
                if (last == null || last.isTombstone) {
                    logAndSendClientResponse(responseNotFound(), nodeUrl);
                } else {
                    logAndSendClientResponse(responseOk(last.bytes), nodeUrl);
                }
            }
        }
        case Request.METHOD_PUT -> {
            if (statusCode == HTTP_CREATED) {
                int acked = atomicAcked.incrementAndGet();

                if (acked == ack) {
                    logAndSendClientResponse(responseCreated(), nodeUrl);
                }
            }
        }
        case Request.METHOD_DELETE -> {
            if (statusCode == HTTP_ACCEPTED) {
                int acked = atomicAcked.incrementAndGet();

                if (acked == ack) {
                    logAndSendClientResponse(responseAccepted(), nodeUrl);
                }
            }
        }
        default -> throw new IllegalArgumentException("Unsupported request method: " + request.getMethodName());
        }
    }

    public void logAndSendClientResponse(Response response, String nodeUrl) throws IOException {
        log.debug(String.format(
            "Send response: %s to session: %s from node: %s ", response.getStatus(), session, nodeUrl
        ));
        session.sendResponse(response);
    }
}
