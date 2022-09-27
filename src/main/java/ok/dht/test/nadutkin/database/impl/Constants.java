package ok.dht.test.nadutkin.database.impl;

import java.lang.ref.Cleaner;
import java.util.concurrent.ThreadFactory;

public abstract class Constants {
    public static final Cleaner CLEANER = Cleaner.create(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "Storage-Cleaner") {
                @Override
                public synchronized void start() {
                    setDaemon(true);
                    super.start();
                }
            };
        }
    });
    public static final long VERSION = 0;
    public static final int INDEX_HEADER_SIZE = Long.BYTES * 3;
    public static final int INDEX_RECORD_SIZE = Long.BYTES;

    public static final String FILE_NAME = "data";
    public static final String FILE_EXT = ".dat";
    public static final String FILE_EXT_TMP = ".tmp";
    public static final String COMPACTED_FILE = FILE_NAME + "_compacted_" + FILE_EXT;
}
