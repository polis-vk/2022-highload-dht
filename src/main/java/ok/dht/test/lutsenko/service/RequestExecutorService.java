package ok.dht.test.lutsenko.service;

import one.nio.http.Response;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static ok.dht.test.lutsenko.service.ServiceUtils.uncheckedSendResponse;

public final class RequestExecutorService {

    public static final int THREADS_NUMBER = 10;
    public static final int QUEUE_CAPACITY = 5;

    private RequestExecutorService() {
    }

    public static ThreadPoolExecutor requestExecutorDiscard() {
        return requestExecutorOfPolicy(DISCARD_POLICY);
    }

    public static ThreadPoolExecutor requestExecutorDiscardOldest() {
        return requestExecutorOfPolicy(DISCARD_OLDEST_POLICY);
    }

    private static ThreadPoolExecutor requestExecutorOfPolicy(RejectedExecutionHandler rejectedHandler) {
        return new ThreadPoolExecutor(
                THREADS_NUMBER, THREADS_NUMBER,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                r -> new Thread(r, "RequestExecutorThread"),
                rejectedHandler
        );
    }

    private static final RejectedExecutionHandler DISCARD_POLICY = (r, e) -> {
        if (!e.isShutdown()) {
            if (r instanceof SessionRunnable sessionRunnable) {
                uncheckedSendResponse(
                        sessionRunnable.session,
                        new Response(Response.REQUEST_TIMEOUT, Response.EMPTY)
                );
            }
        }
    };

    private static final RejectedExecutionHandler DISCARD_OLDEST_POLICY = (r, e) -> {
        if (!e.isShutdown()) {
            if (e.getQueue().poll() instanceof SessionRunnable sessionRunnable) {
                uncheckedSendResponse(
                        sessionRunnable.session,
                        new Response(Response.REQUEST_TIMEOUT, Response.EMPTY)
                );
            }
            e.execute(r);
        }
    };
}
