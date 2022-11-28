package ok.dht.test.kuleshov;

import ok.dht.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class Server {
    private static final String TMP_DIRECTORY_PREFIX = "server";
    private static final int SERVER_PORT = 19234;
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private Server() {

    }

    public static void main(String[] args) {
        String url = "http://localhost:" + SERVER_PORT;

        Path tmpDirectory;
        try {
            tmpDirectory = Files.createTempDirectory(TMP_DIRECTORY_PREFIX);
        } catch (IOException e) {
            log.error("Error creating temp directory " + TMP_DIRECTORY_PREFIX + ": " + e.getMessage());
            return;
        }

        ServiceConfig cfg = new ServiceConfig(
                SERVER_PORT,
                url,
                Collections.singletonList(url),
                tmpDirectory
        );
        Service service = new Service(cfg);
        CompletableFuture<?> completableFuture;
        try {
            completableFuture = service.start();
        } catch (IOException e) {
            log.error("Error starting service: " + e.getMessage());
            return;
        }

        try {
            completableFuture.get(1, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            log.error("Error starting service: " + e.getMessage());
            return;
        }

        log.info("Socket is ready: " + url);
    }
}
