package ok.dht.test.monakhov;

import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class ExecutorUtils {

    private ExecutorUtils() {
    }

    public static void shutdownGracefully(ExecutorService executor) {
        shutdownGracefully(executor, null);
    }

    public static void shutdownGracefully(ExecutorService executor, Logger log) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(1, TimeUnit.SECONDS) && log != null) {
                    log.error("Unable to shutdown executor service: {}", executor);
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
