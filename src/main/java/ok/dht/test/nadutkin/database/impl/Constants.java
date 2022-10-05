package ok.dht.test.nadutkin.database.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.Cleaner;

public final class Constants {
    public static final Cleaner CLEANER = Cleaner.create(r -> new Thread(r, "Storage-Cleaner") {
        @Override
        public synchronized void start() {
            setDaemon(true);
            super.start();
        }

        @Override
        public void run() {
            LOG.info("Storage-cleaner thread {} started", Thread.currentThread().getName());
            super.run();
        }
    });

    public static final Logger LOG = LoggerFactory.getLogger(MemorySegmentDao.class);
    public static final long VERSION = 0;
    public static final int INDEX_HEADER_SIZE = Long.BYTES * 3;
    public static final int INDEX_RECORD_SIZE = Long.BYTES;
    public static final String FILE_NAME = "data";

    public static final String FILE_EXT = ".dat";
    public static final String FILE_EXT_TMP = ".tmp";
    public static final String COMPACTED_FILE = FILE_NAME + "_compacted_" + FILE_EXT;

    private Constants() {
    }
}
