package ok.dht.test.lutsenko.service;

import one.nio.http.HttpSession;
import one.nio.http.Response;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class ServiceUtils {

    private ServiceUtils() {
    }

    public static void uncheckedSendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void shutdownAndAwaitTermination(ThreadPoolExecutor executorService) {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(Integer.MAX_VALUE, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
                throw new RuntimeException("Await termination too long");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Await termination interrupted", e);
        }
    }
}
