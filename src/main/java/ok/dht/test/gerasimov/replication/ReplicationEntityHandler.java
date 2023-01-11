package ok.dht.test.gerasimov.replication;

import ok.dht.test.gerasimov.exception.ReplicationHandlerException;
import ok.dht.test.gerasimov.model.DaoEntry;
import ok.dht.test.gerasimov.utils.ObjectMapper;
import ok.dht.test.gerasimov.utils.ResponseEntity;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.net.HttpURLConnection.HTTP_ACCEPTED;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_OK;

public class ReplicationEntityHandler {
    private final AtomicInteger acked = new AtomicInteger(0);
    private final AtomicInteger finished = new AtomicInteger(0);
    private final AtomicReference<DaoEntry> lastRecord = new AtomicReference<>(new DaoEntry(0L, null, true));
    private final HttpSession session;
    private final int method;
    private final int ack;
    private final int from;

    public ReplicationEntityHandler(HttpSession session, int method, int ack, int from) {
        this.session = session;
        this.method = method;
        this.ack = ack;
        this.from = from;
    }

    public void handleResponse(int statusCode, byte[] body) {
        if (body != null) {
            switch (method) {
                case Request.METHOD_GET -> {
                    if (statusCode == HTTP_OK) {
                        try {
                            DaoEntry recordFromResponse;
                            recordFromResponse = ObjectMapper.deserialize(body);

                            while (true) {
                                DaoEntry recordToReturn = lastRecord.get();

                                if (recordFromResponse.compareTo(recordToReturn) > 0) {
                                    if (lastRecord.compareAndSet(recordToReturn, recordFromResponse)) {
                                        break;
                                    }
                                } else {
                                    break;
                                }
                            }
                        } catch (ClassNotFoundException | IOException e) {
                            throw new ReplicationHandlerException("Exception during deserialize entry dao", e);
                        }
                    }

                    DaoEntry recordToReturn = lastRecord.get();

                    if (acked.incrementAndGet() == ack) {
                        if (recordToReturn.isTombstone()) {
                            returnDecision(ResponseEntity.notFound());
                        } else {
                            returnDecision(ResponseEntity.ok(recordToReturn.getValue()));
                        }
                    }
                }
                case Request.METHOD_PUT -> {
                    if (statusCode == HTTP_CREATED && acked.incrementAndGet() == ack) {
                        returnDecision(ResponseEntity.created());
                    }
                }
                case Request.METHOD_DELETE -> {
                    if (statusCode == HTTP_ACCEPTED && acked.incrementAndGet() == ack) {
                        returnDecision(ResponseEntity.accepted());
                    }
                }
                default -> throw new IllegalStateException("Unsupported method");
            }
        }

        int finished = this.finished.incrementAndGet();
        int acked = this.acked.get();

        if (finished == from && acked < ack) {
            returnDecision(ResponseEntity.notEnoughReplicas());
        }
    }

    private void returnDecision(Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            throw new ReplicationHandlerException("Can not send response", e);
        }
    }
}
