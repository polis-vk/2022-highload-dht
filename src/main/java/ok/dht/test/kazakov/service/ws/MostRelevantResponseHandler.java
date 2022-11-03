package ok.dht.test.kazakov.service.ws;

import ok.dht.test.kazakov.dao.Entry;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/* package-private */ class MostRelevantResponseHandler {

    private static final Logger LOG = LoggerFactory.getLogger(MostRelevantResponseHandler.class);
    public static final String UNEXPECTED_ERROR_ON_RESPONSE_HANDLING_MESSAGE = "Unexpected error on response handling";

    private final AtomicInteger successfulResponses;
    private final AtomicInteger totalResponses;
    private final AtomicReference<ResponseHolder> responseHolder;
    private final int needAcknowledgements;
    private final int totalRequests;

    public MostRelevantResponseHandler(final int needAcknowledgements,
                                       final int totalRequests) {
        this.successfulResponses = new AtomicInteger(0);
        this.totalResponses = new AtomicInteger(0);
        this.responseHolder = new AtomicReference<>(null);
        this.needAcknowledgements = needAcknowledgements;
        this.totalRequests = totalRequests;
    }

    public void handleResponse(@Nonnull final HttpSession session,
                               @Nullable final ResponseHolder currentResponseHolder,
                               @Nullable final Throwable throwable) {
        logResponse(currentResponseHolder, throwable);

        if (isResponseSuccessful(currentResponseHolder)) {
            ResponseHolder bestResponse = responseHolder.get();
            while (currentResponseHolder.isMoreRelevantThan(bestResponse)) {
                if (responseHolder.compareAndSet(bestResponse, currentResponseHolder)) {
                    break;
                }
                bestResponse = responseHolder.get();
            }
        }

        final int currentSuccessfulResponses;
        if (isResponseSuccessful(currentResponseHolder)) {
            currentSuccessfulResponses = successfulResponses.incrementAndGet();

            if (currentSuccessfulResponses == needAcknowledgements) {
                respondToClient(session, responseHolder.get().toResponse());
            }
        } else {
            currentSuccessfulResponses = successfulResponses.get();
        }

        final int currentTotalResponses = totalResponses.incrementAndGet();
        if (currentSuccessfulResponses >= needAcknowledgements) {
            return;
        }

        if (currentTotalResponses == totalRequests && successfulResponses.get() < needAcknowledgements) {
            respondToClient(session, null);
        }
    }

    private void logResponse(final ResponseHolder currentResponseHolder,
                             final Throwable throwable) {
        if (throwable != null) {
            if (throwable.getCause() instanceof HttpTimeoutException) {
                LOG.debug(UNEXPECTED_ERROR_ON_RESPONSE_HANDLING_MESSAGE, throwable);
                LOG.warn("Unexpected request timeout");
            } else {
                LOG.error(UNEXPECTED_ERROR_ON_RESPONSE_HANDLING_MESSAGE, throwable);
            }
        }

        LOG.debug(
                "Handling response with currentResponseHolder={}, successfulResponses={}, totalResponses={}",
                currentResponseHolder,
                successfulResponses,
                totalResponses
        );
    }

    public boolean isRespondedToClient() {
        return successfulResponses.get() == needAcknowledgements || totalResponses.get() == totalRequests;
    }

    private static boolean isResponseSuccessful(@Nullable final ResponseHolder responseHolder) {
        return responseHolder != null && responseHolder.responseCode.charAt(0) != '5';
    }

    @SuppressWarnings("ReplaceNullCheck")
    private static void respondToClient(@Nonnull final HttpSession session,
                                        @Nullable final Response response) {
        try {
            if (response == null) {
                session.sendResponse(new Response(Response.GATEWAY_TIMEOUT, Response.EMPTY));
            } else {
                session.sendResponse(response);
            }
        } catch (final IOException e) {
            LOG.error("Could not respond to client", e);
        }
    }

    public static class ResponseHolder {
        private final boolean is2xxResponse;
        private final String responseCode;
        private final Entry<?> entry;

        public ResponseHolder(@Nonnull final String responseCode,
                              @Nullable final Entry<?> entry) {
            this.responseCode = responseCode;
            this.entry = entry;
            this.is2xxResponse = responseCode.charAt(0) == '2';
        }

        private Response toResponse() {
            return new Response(
                    responseCode,
                    entry == null || entry.isTombstone() ? Response.EMPTY : entry.getValueBytes()
            );
        }

        // responses with non-null entries and greater timestamp are more relevant
        // if previous are equal, prefer 2xx response codes
        private boolean isMoreRelevantThan(@Nullable final ResponseHolder responseHolder) {
            if (responseHolder == null) {
                return true;
            }

            if (responseHolder.entry == null && entry == null) {
                return is2xxResponse;
            }

            if (responseHolder.entry == null || entry == null) {
                return responseHolder.entry == null;
            }

            if (entry.getTimestamp() == responseHolder.entry.getTimestamp()) {
                return is2xxResponse;
            }

            return entry.getTimestamp() > responseHolder.entry.getTimestamp();
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", "ResponseHolder[", "]")
                    .add("responseCode='" + responseCode + "'")
                    .add("entry=" + entry)
                    .toString();
        }
    }
}
