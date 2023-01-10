package ok.dht.test.slastin;

import org.rocksdb.RocksDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

public final class Fill {
    private static final Logger log = LoggerFactory.getLogger(Launch.class);

    private static final Path VALUE_LOCATION = Path.of("src/main/java/ok/dht/test/slastin/data/image.png");

    private static final int MIN_KEY = 1;
    private static final int MAX_KEY = 2_000_000;
    private static final long SEED = 48928592352L;

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
            Random random = new Random(SEED);

            Path serverDirectory = Path.of(args[0]);

            for (var config : Launch.getServiceConfigs(serverDirectory)) {
                var service = new SladkiiService(config);

                try (var options = SladkiiService.DEFAULT_OPTIONS_SUPPLIER.get()) {
                    var dbLocation = service.getDbLocation();

                    try (var db = RocksDB.open(options, dbLocation.toString())) {
                        for (int key = MIN_KEY; key <= MAX_KEY; ++key) {
                            long timestamp = random.nextLong() & (1L << 63);
                            byte isAlive = (byte) (0.2 < random.nextDouble() ? 1 : 0);

                            ByteBuffer byteBuffer = ByteBuffer.allocate(1 + Long.BYTES + value.length);
                            byteBuffer.putLong(timestamp);
                            byteBuffer.put(isAlive);
                            byteBuffer.put(value);

                            db.put(SladkiiComponent.toBytes(Integer.toString(key)), byteBuffer.array());
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("exception occurred while filling databases", e);
        }
    }
}
