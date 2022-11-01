package ok.dht.test.shashulovskiy.sharding;

import ok.dht.test.shashulovskiy.metainfo.MetadataUtils;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ResponseAccumulator {

    private static final String NOT_ENOUGH_REPLICAS_RESPONSE = "504 Not Enough Replicas";
    private static final Logger LOG = LoggerFactory.getLogger(ResponseAccumulator.class);

    private final int requiredTotal;
    private final int requiredAcks;
    private final int requestMethod;
    private final HttpSession session;
    private final boolean isInternal;

    private final AtomicInteger acksCount;
    private final AtomicInteger totalCount;
    private final AtomicReference<byte[]> latestData;

    private final AtomicBoolean responseAttempted;

    public ResponseAccumulator(int acks, int total, int requestMethod, HttpSession session, boolean isInternal) {
        this.requiredTotal = total;
        this.requiredAcks = acks;
        this.requestMethod = requestMethod;
        this.session = session;
        this.isInternal = isInternal;

        this.acksCount = new AtomicInteger(0);
        this.totalCount = new AtomicInteger(0);
        this.latestData = new AtomicReference<>(null);
        this.responseAttempted = new AtomicBoolean(false);
    }

    public void processSuccess(int statusCode, byte[] body) {
        int currentAcks = acksCount.incrementAndGet();

        // FOR GET REQUESTS ONLY
        if (statusCode == 200 && body.length != 0) {
            // CAS loop
            while (true) {
                var currentLatestData = latestData.get();

                // First response case
                if (currentLatestData == null) {
                    if (latestData.compareAndSet(null, body)) {
                        break;
                    } else {
                        continue;
                    }
                }

                if (MetadataUtils.extractTimestamp(currentLatestData) < MetadataUtils.extractTimestamp(body)) {
                    if (latestData.compareAndSet(currentLatestData, body)) {
                        break;
                    }
                } else {
                    break;
                }
            }
        }

        if (currentAcks == requiredAcks) {
            onAcksCollected();
        }
    }

    public void processAny() {
        if (totalCount.incrementAndGet() == requiredTotal) {
            onAllRequestsProcessed();
        }
    }

    private void onAcksCollected() {
        // No need to check that response here wasnt sent before since
        // the condition on entering this function is `acksCount.incrementAndGet() == requiredAcks`
        // This can only be true once.

        // However we still need to mark response as "attempted" since in onAllRequestsProcessed we need to be able
        // to tell if we need to respond with 504
        responseAttempted.set(true);

        try {
            switch (requestMethod) {
                case Request.METHOD_GET -> {
                    byte[] currentLatestData = latestData.get();

                    // For internal requests, we return data as is: with metadata etc. Only exception is
                    // when nothing is stored in database, hence we shall return 404 with no response body
                    //
                    // However when replying to the client we need to get rid of all our metadata, as well
                    // as response with 404 if the latest response is a tombstone
                    if (currentLatestData == null || (!isInternal && MetadataUtils.isTombstone(currentLatestData))) {
                        session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
                    } else {
                        session.sendResponse(new Response(Response.OK,
                                isInternal ? currentLatestData : MetadataUtils.extractData(currentLatestData)));
                    }
                }
                case Request.METHOD_PUT -> session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
                case Request.METHOD_DELETE -> session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
                default -> session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
            }
        } catch (IOException e) {
            LOG.error("Unable to send successful response", e);
        }
    }

    private void onAllRequestsProcessed() {
        if (!responseAttempted.get()) {
            try {
                session.sendResponse(new Response(NOT_ENOUGH_REPLICAS_RESPONSE, Response.EMPTY));
            } catch (IOException e) {
                LOG.error("Unable to send not enough replicas response", e);
            }
        }
    }
}
