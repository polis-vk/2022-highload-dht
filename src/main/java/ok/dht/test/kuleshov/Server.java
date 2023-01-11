package ok.dht.test.kuleshov;

import ok.dht.ServiceConfig;
import ok.dht.test.kuleshov.sharding.ClusterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class Server {
    private static final String TMP_DIRECTORY_PREFIX = "server";
    private static final Logger log = LoggerFactory.getLogger(Server.class);

    private Server() {

    }

    public static void main(String[] args) {
        Map<String, List<String>> options = new HashMap<>();

        if (args.length == 0) {
            throw new IllegalArgumentException("arguments is empty");
        }

        if (args[0].charAt(0) == '-') {
            throw new IllegalArgumentException("arguments parameters should start with -");
        }

        String last = "";
        for (String arg : args) {
            if (arg.charAt(0) == '-') {
                options.put(arg, new ArrayList<>());
                last = arg;
            } else {
                options.get(last).add(arg);
            }
        }

        int localPort = Integer.parseInt(options.get("-p").get(0));
        boolean isAdded = options.containsKey("-a");
        String url = "http://localhost:" + localPort;

        Path tmpDirectory;
        try {
            tmpDirectory = Files.createTempDirectory(TMP_DIRECTORY_PREFIX);
        } catch (IOException e) {
            log.error("Error creating temp directory " + TMP_DIRECTORY_PREFIX + ": " + e.getMessage());
            return;
        }

        ClusterConfig clusterConfig = new ClusterConfig();

        clusterConfig.urlToHash = Map.of(
                "http://localhost:19234", new ArrayList<>()
        );

        ServiceConfig cfg = new ServiceConfig(
                localPort,
                url,
                clusterConfig.urlToHash.keySet().stream().toList(),
                tmpDirectory
        );
        Service service = new Service(cfg);
        CompletableFuture<?> completableFuture;

        try {
            if (isAdded) {

                completableFuture = service.startAdded(clusterConfig);
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
