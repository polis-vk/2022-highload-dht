package ok.dht.test.armenakyan;

import ok.dht.ServiceConfig;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class DhtApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(DhtApplication.class);

    private DhtApplication() {
    }

    public static void main(String[] args) throws IOException, RocksDBException, InterruptedException {
        int port = Integer.parseInt(args[0]);
        String dbPath = args[1];
        String url = args[2];

        List<String> clusterUrls = Arrays.asList(args).subList(2, args.length);

        try {
            ServiceConfig serviceConfig = new ServiceConfig(
                    port,
                    url,
                    clusterUrls,
                    Path.of(dbPath)
            );

            new DhtService(serviceConfig).start().get(1, TimeUnit.SECONDS);

            LOGGER.info("Server successfully started on port={}, url={}", port, url);
            LOGGER.info("Database working dir: {}", serviceConfig.workingDir().toAbsolutePath().normalize());
        } catch (Exception e) {
            LOGGER.error("Couldn't start server: ", e);
        }
    }
}
