package ok.dht.test.maximenko.dao;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class FileManager {
    private static final String TMP_COMPACT_FILE = "compact_log_tmp";
    private static final String TMP_FLUSH_FILE = "flush_log_tmp";
    private static final String COMPACTING_FILE = "compacting_tmp";
    private static final String LOG_NAME = "log";
    private static final int LOG_INDEX_START = 0;

    private int logFilesAmount;
    private final ReadWriteLock logDirectoryLock = new ReentrantReadWriteLock();
    private final Config config;

    public FileManager(Config config) {
        this.config = config;

        int logIndex = LOG_INDEX_START;
        while (true) {
            Path filename = getLogName(logIndex);
            if (Files.exists(filename)) {
                logIndex++;
            } else {
                break;
            }
        }

        logFilesAmount = logIndex;
    }

    public Path getLogName(int logNumber) {
        return config.basePath().resolve(LOG_NAME + logNumber);
    }

    public Path getCompactTmpFile() {
        return config.basePath().resolve(TMP_COMPACT_FILE);
    }

    public Path getFlushTmpFile() {
        return config.basePath().resolve(TMP_FLUSH_FILE);
    }

    public Path getCompactingInProcessFile() {
        return config.basePath().resolve(COMPACTING_FILE);
    }

    public Path getNextLogNameWithoutLocking() {
        return config.basePath().resolve(LOG_NAME + logFilesAmount);
    }

    public Path getNextLogName() {
        logDirectoryLock.readLock().lock();
        Path result = getNextLogNameWithoutLocking();
        logDirectoryLock.readLock().unlock();
        return result;
    }

    public int addLog(Path newLogFile) throws IOException {
        logDirectoryLock.writeLock().lock();

        int newLogIndex = logFilesAmount;
        try {
            Files.move(newLogFile, getNextLogNameWithoutLocking(), StandardCopyOption.ATOMIC_MOVE);
            logFilesAmount++;
        } finally {
            logDirectoryLock.writeLock().unlock();
        }

        return newLogIndex;
    }

    public List<Path> getLogPaths() {
        List<Path> result = new LinkedList<>();
        logDirectoryLock.readLock().lock();
        try {
            for (int i = 0; i < logFilesAmount; i++) {
                Path filename = getLogName(i);
                result.add(filename);
            }
        } finally {
            logDirectoryLock.readLock().unlock();
        }

        return result;
    }

    public void lock() {
        logDirectoryLock.writeLock().lock();
    }

    public void unlock() {
        logDirectoryLock.writeLock().unlock();
    }

    // suppose we have logs: 0 1 2 3 4 5, size equals 2
    // 1) we get 2 3 2 3 4 5
    // 2) then 2 3 4 5 4 5
    // 3) then 2 3 4 5
    // #fix Me implement this
    public void removeLogFilesWithoutLockingWithFixingFurtherLogs(int size) throws IOException {
        assert size >= 0;
        for (int logIndex = logFilesAmount - 1; logIndex > 0; logIndex--) {
            Path filename = config.basePath().resolve(LOG_NAME + logIndex);
            Files.delete(filename);
        }
        logFilesAmount = 0;
    }

    public int addLogWithoutLocking(Path newLogFile) throws IOException {
        int newLogIndex = logFilesAmount;

        Files.move(newLogFile, getNextLogName(), StandardCopyOption.ATOMIC_MOVE);
        logFilesAmount++;

        return newLogIndex;
    }
}
