package ok.dht.test.maximenko.dao;

import jdk.incubator.foreign.MemorySegment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class Compactor implements Runnable {
    private final MemorySegmentDao memorySegmentDao;

    public Compactor(MemorySegmentDao memorySegmentDao) {
        this.memorySegmentDao = memorySegmentDao;
    }

    @Override
    public void run() {
        try {
            List<Table> ssTablesToCompact = new ArrayList<>(memorySegmentDao.ssTables.size());

            memorySegmentDao.dbTablesLock.readLock().lock();
            try {
                if (memorySegmentDao.ssTables.size() <= 1) {
                    memorySegmentDao.dbTablesLock.readLock().unlock();
                    return;
                }
                ssTablesToCompact.addAll(memorySegmentDao.ssTables);
            } finally {
                memorySegmentDao.dbTablesLock.readLock().unlock();
            }

            Files.deleteIfExists(memorySegmentDao.fileManager.getCompactingInProcessFile());
            memorySegmentDao.writeValuesToFile(() -> {
                return createMergeIterator(ssTablesToCompact);
            }, memorySegmentDao.fileManager.getCompactingInProcessFile());

            int compactLogIndex;
            memorySegmentDao.fileManager.lock();
            try {
                Files.move(memorySegmentDao.fileManager.getCompactingInProcessFile(),
                        memorySegmentDao.fileManager.getCompactTmpFile(),
                        StandardCopyOption.ATOMIC_MOVE);
                memorySegmentDao.fileManager
                        .removeLogFilesWithoutLockingWithFixingFurtherLogs(ssTablesToCompact.size());
                compactLogIndex = memorySegmentDao.fileManager
                        .addLogWithoutLocking(memorySegmentDao.fileManager.getCompactTmpFile());
            } finally {
                memorySegmentDao.fileManager.unlock();
            }

            memorySegmentDao.dbTablesLock.writeLock().lock();
            try {
                memorySegmentDao.ssTables.clear();
                memorySegmentDao.ssTables.add(new SSTable(memorySegmentDao.fileManager.getLogName(compactLogIndex),
                        memorySegmentDao.scope));
            } finally {
                memorySegmentDao.dbTablesLock.writeLock().unlock();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Iterator<Entry<MemorySegment>> createMergeIterator(List<Table> ssTablesToCompact) {
        try {
            return new BorderedMergeIterator(ssTablesToCompact);
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyIterator();
        }
    }
}
