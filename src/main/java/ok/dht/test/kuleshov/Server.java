package ok.dht.test.kuleshov;

import ok.dht.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Server {
    private static final String TMP_DIRECTORY_PREFIX = "server";
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private Server() {

    }

    public static void main(String[] args) {
        String isAdded = args[0];
        String url = "http://localhost:" + args[1];

        Path tmpDirectory;
        try {
            tmpDirectory = Files.createTempDirectory(TMP_DIRECTORY_PREFIX);
        } catch (IOException e) {
            log.error("Error creating temp directory " + TMP_DIRECTORY_PREFIX + ": " + e.getMessage());
            return;
        }

        ServiceConfig cfg = new ServiceConfig(
                Integer.parseInt(args[1]),
                url,
                Stream.of(args).skip(1).map(port -> "http://localhost:" + port).collect(Collectors.toList()),
                tmpDirectory
        );
        Service service = new Service(cfg);
        CompletableFuture<?> completableFuture;
        try {
            if (isAdded.equals("--add")) {
                completableFuture = service.startAdded();
            } else {
                completableFuture = service.start();
            }
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
