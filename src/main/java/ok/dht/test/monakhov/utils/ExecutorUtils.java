package ok.dht.test.monakhov.utils;

import org.apache.commons.logging.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class ExecutorUtils {

    private ExecutorUtils() {
    }

    public static void shutdownGracefully(ExecutorService executor) {
        shutdownGracefully(executor, null);
    }

    public static void shutdownGracefully(ExecutorService executor, Log log) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(10, TimeUnit.MILLISECONDS) && log != null) {
                    log.error("Unable to shutdown executor service: " + executor);
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
