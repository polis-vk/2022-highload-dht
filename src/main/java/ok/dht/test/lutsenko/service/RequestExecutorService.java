package ok.dht.test.lutsenko.service;

import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class RequestExecutorService {

    public static final int QUEUE_CAPACITY = 5;
    public static final int AWAIT_TERMINATION_SECONDS = 60;
    public static final int THREADS_NUMBER = calculateThreadNumber();
    private static final Logger LOG = LoggerFactory.getLogger(RequestExecutorService.class);

    private RequestExecutorService() {

    }

    public static ThreadPoolExecutor requestExecutorDiscard() {
        return requestExecutorOf(new ArrayBlockingQueue<>(QUEUE_CAPACITY), DISCARD_POLICY);
    }

    public static ThreadPoolExecutor requestExecutorOnStackDiscard() {
        return requestExecutorOf(new LinkedBlockingStack<>(QUEUE_CAPACITY), DISCARD_POLICY);
    }

    public static ThreadPoolExecutor requestExecutorDiscardOldest() {
        return requestExecutorOf(new ArrayBlockingQueue<>(QUEUE_CAPACITY), DISCARD_OLDEST_POLICY);
    }

    public static void shutdownAndAwaitTermination(ExecutorService executorService) throws TimeoutException {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(AWAIT_TERMINATION_SECONDS, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(AWAIT_TERMINATION_SECONDS, TimeUnit.SECONDS)) {
                    throw new TimeoutException("Request executor await termination too long");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Request executor await termination interrupted", e);
        }
    }

    private static ThreadPoolExecutor requestExecutorOf(BlockingQueue<Runnable> blockingQueue,
                                                        RejectedExecutionHandler rejectedHandler) {
        return new ThreadPoolExecutor(
                THREADS_NUMBER, THREADS_NUMBER,
                0L, TimeUnit.MILLISECONDS,
                blockingQueue,
                r -> new Thread(r, "RequestExecutorThread"),
                rejectedHandler
        );
    }

    private static final RejectedExecutionHandler DISCARD_POLICY = (r, e) -> {
        if (!e.isShutdown()) {
            if (r instanceof SessionRunnable sessionRunnable) {
                ServiceUtils.sendResponse(
                        sessionRunnable.session,
                        new Response(Response.REQUEST_TIMEOUT, Response.EMPTY)
                );
            }
        }
    };

    private static final RejectedExecutionHandler DISCARD_OLDEST_POLICY = (r, e) -> {
        if (!e.isShutdown()) {
            if (e.getQueue().poll() instanceof SessionRunnable sessionRunnable) {
                ServiceUtils.sendResponse(
                        sessionRunnable.session,
                        new Response(Response.REQUEST_TIMEOUT, Response.EMPTY)
                );
            }
            e.execute(r);
        }
    };

    private static int calculateThreadNumber() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        if (availableProcessors >= 4) {
             return availableProcessors - 2;
        } else {
            return availableProcessors;
        }
    }
}
