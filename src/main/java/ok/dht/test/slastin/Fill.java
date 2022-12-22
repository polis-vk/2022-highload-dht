package ok.dht.test.slastin;

import org.rocksdb.RocksDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Fill {
    private static final Logger log = LoggerFactory.getLogger(Launch.class);

    private static final Path VALUE_LOCATION = Path.of("src/main/java/ok/dht/test/slastin/data/image.png");

    private static final int MIN_KEY = 1;
    private static final int MAX_KEY = 2_000_000;

    private Fill() {
        // Only main method
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            log.error("first argument must be server directory");
            return;
        }
        try {
            byte[] value = Files.readAllBytes(VALUE_LOCATION);

            Path serverDirectory = Path.of(args[0]);

            for (var config : Launch.getServiceConfigs(serverDirectory)) {
                var service = new SladkiiService(config);

                try (var options = SladkiiService.DEFAULT_OPTIONS_SUPPLIER.get()) {
                    var dbLocation = service.getDbLocation();

                    try (var db = RocksDB.open(options, dbLocation.toString())) {
                        for (int key = MIN_KEY; key <= MAX_KEY; ++key) {
                            db.put(SladkiiComponent.toBytes(Integer.toString(key)), value);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("exception occurred while filling databases", e);
        }
    }
}
