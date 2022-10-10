package ok.dht.test.slastin;

import ok.dht.ServiceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public final class Launch {
    private static final Logger log = LoggerFactory.getLogger(Launch.class);

    static final int[] DEFAULT_CLUSTER_PORTS = new int[]{2022, 2023, 2024};
    static final String DEFAULT_SERVER_NAME = "server";

    private Launch() {
        // Only main method
    }

    private static Path createServerDirectory(final String serverDirectoryName) throws IOException {
        final Path serverDirectory = Path.of(serverDirectoryName);
        if (Files.notExists(serverDirectory)) {
            Files.createDirectories(serverDirectory);
        }
        return serverDirectory;
    }

    static List<ServiceConfig> getServiceConfigs(Path serverDirectory) {
        List<String> clusterUrls = Arrays.stream(DEFAULT_CLUSTER_PORTS)
                .mapToObj(port -> "http://localhost:" + port)
                .toList();

        return IntStream.range(0, DEFAULT_CLUSTER_PORTS.length)
                .mapToObj(clusterIndex -> new ServiceConfig(
                        DEFAULT_CLUSTER_PORTS[clusterIndex],
                        clusterUrls.get(clusterIndex),
                        clusterUrls,
                        serverDirectory))
                .toList();
    }

    public static void main(String[] args) {
        try {
            Path serverDirectory = args.length == 0
                    ? Files.createTempDirectory(DEFAULT_SERVER_NAME)
                    : createServerDirectory(args[0]);

            for (var config : getServiceConfigs(serverDirectory)) {
                var serviceBuilder = new SladkiiService.Builder(config);
                serviceBuilder.setDbOptionsSupplier(SladkiiService.DEFAULT_OPTIONS_WITH_BLOOM_SUPPLIER);
                serviceBuilder.build().start().get(1, TimeUnit.SECONDS);
                log.info("Server started {}", config.selfUrl());
            }
        } catch (Exception e) {
            log.error("Error occurred with server", e);
        }
    }
}
