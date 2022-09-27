package ok.dht.test.nadutkin.database.impl;

public class Constants {
    public static final long VERSION = 0;
    public static final int INDEX_HEADER_SIZE = Long.BYTES * 3;
    public static final int INDEX_RECORD_SIZE = Long.BYTES;

    public static final String FILE_NAME = "data";
    public static final String FILE_EXT = ".dat";
    public static final String FILE_EXT_TMP = ".tmp";
    public static final String COMPACTED_FILE = FILE_NAME + "_compacted_" + FILE_EXT;
}
