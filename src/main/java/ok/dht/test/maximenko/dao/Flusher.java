package ok.dht.test.maximenko.dao;

import java.io.IOException;

// this class only exists because of the file size limit (250 lines)
public class Flusher implements Runnable {
    private final MemorySegmentDao memorySegmentDao;

    public Flusher(MemorySegmentDao memorySegmentDao) {
        this.memorySegmentDao = memorySegmentDao;
    }

    @Override
    public void run() {
        try {
            MemTable tableToFlush = memorySegmentDao.flushingTables.poll();
            assert tableToFlush != null;

            memorySegmentDao.dbTablesLock.writeLock().lock();
            memorySegmentDao.flushingTable = tableToFlush;
            memorySegmentDao.dbTablesLock.writeLock().unlock();

            if (!memorySegmentDao.flushingTable.isEmpty()) {
                memorySegmentDao.writeValuesToFile(memorySegmentDao.flushingTable,
                        memorySegmentDao.fileManager.getFlushTmpFile());

                int newLogIndex = memorySegmentDao.fileManager
                        .addLog(memorySegmentDao.fileManager.getFlushTmpFile());
                memorySegmentDao.dbTablesLock.writeLock().lock();
                memorySegmentDao.ssTables.add(0,
                        new SSTable(memorySegmentDao.fileManager.getLogName(newLogIndex),
                                memorySegmentDao.scope));
                memorySegmentDao.dbTablesLock.writeLock().unlock();
            }
        } catch (IOException exception) {
            exception.printStackTrace();
            // can't throw because of code style check
            // I want to throw something like IllegalThreadStateException
        }
    }
}
