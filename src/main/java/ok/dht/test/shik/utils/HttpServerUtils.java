package ok.dht.test.shik.utils;

import ok.dht.test.shik.events.HandlerRequest;
import ok.dht.test.shik.events.HandlerResponse;
import ok.dht.test.shik.events.RequestState;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

public final class HttpServerUtils {

    private static final Log LOG = LogFactory.getLog(HttpServerUtils.class);

    private HttpServerUtils() {

    }

    public static <T extends HandlerRequest, U extends HandlerResponse>
    void handleConcreteRequest(RequestState state, T request, U response, BiConsumer<T, U> method) {
        try {
            method.accept(request, response);
            state.onShardResponseSuccess(response.getResponse());
        } catch (Exception e) {
            state.onShardResponseFailure();
        }
    }

    public static void sendError(HttpSession session, Exception e) {
        try {
            String response;
            if (BufferOverflowException.class == e.getClass()) {
                response = Response.REQUEST_ENTITY_TOO_LARGE;
            } else if (TimeoutException.class == e.getClass()) {
                response = Response.GATEWAY_TIMEOUT;
            } else {
                response = Response.SERVICE_UNAVAILABLE;
            }
            session.sendError(response, e.getMessage());
        } catch (IOException e1) {
            LOG.error("Error while sending message about error: ", e1);
        }
    }
}
