package ok.dht.test.shik.utils;

import ok.dht.test.shik.workers.WorkersConfig;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;

public final class HttpServerUtils {

    private static final Log LOG = LogFactory.getLog(HttpServerUtils.class);

    private HttpServerUtils() {

    }

    public static ExecutorService createExecutor(WorkersConfig config) {
        RejectedExecutionHandler rejectedHandler = config.getQueuePolicy() == WorkersConfig.QueuePolicy.FIFO
            ? new ThreadPoolExecutor.DiscardPolicy()
            : new ThreadPoolExecutor.DiscardOldestPolicy();
        return new ThreadPoolExecutor(config.getCorePoolSize(), config.getMaxPoolSize(),
            config.getKeepAliveTime(), config.getUnit(), new ArrayBlockingQueue<>(config.getQueueCapacity()),
            r -> new Thread(r, "httpClientThread"), rejectedHandler);
    }

    public static void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            LOG.error("Cannot send response ", e);
            HttpServerUtils.sendError(session, e);
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
