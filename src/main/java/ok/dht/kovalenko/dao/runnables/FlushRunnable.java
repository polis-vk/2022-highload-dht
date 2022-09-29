package ok.dht.kovalenko.dao.runnables;

import ok.dht.ServiceConfig;
import ok.dht.kovalenko.dao.LSMDao;
import ok.dht.kovalenko.dao.Serializer;
import ok.dht.kovalenko.dao.aliases.MemorySSTable;
import ok.dht.kovalenko.dao.dto.PairedFiles;
import ok.dht.kovalenko.dao.utils.FileUtils;

import java.io.IOException;

public class FlushRunnable implements Runnable {

    private final ServiceConfig config;
    private final Serializer serializer;
    private final LSMDao.MemoryStorage memoryStorage;

    public FlushRunnable(ServiceConfig config, Serializer serializer,
                         LSMDao.MemoryStorage memoryStorage) {
        this.config = config;
        this.serializer = serializer;
        this.memoryStorage = memoryStorage;
    }

    @Override
    public void run() {
        try {
            if (this.memoryStorage.writeSSTables().isEmpty()) {
                return;
            }

            MemorySSTable memorySSTable = this.memoryStorage.writeSSTables().poll();
            if (memorySSTable == null || !memorySSTable.values().iterator().hasNext()) {
                return;
            }

            this.memoryStorage.flushSSTables().add(memorySSTable);
            PairedFiles pairedFiles = FileUtils.createPairedFiles(this.config);
            this.serializer.write(memorySSTable.values().iterator(), pairedFiles);
            // It is impossible that any other thread will capture memorySSTable
            this.memoryStorage.flushSSTables().remove(memorySSTable);
            this.memoryStorage.writeSSTables().add(new MemorySSTable());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
