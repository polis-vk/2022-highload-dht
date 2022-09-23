package ok.dht.kovalenko.dao.runnables;

import ok.dht.ServiceConfig;
import ok.dht.kovalenko.dao.Serializer;
import ok.dht.kovalenko.dao.aliases.MemorySSTable;
import ok.dht.kovalenko.dao.aliases.MemorySSTableStorage;
import ok.dht.kovalenko.dao.dto.PairedFiles;
import ok.dht.kovalenko.dao.utils.DaoUtils;
import ok.dht.kovalenko.dao.utils.FileUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

public class FlushRunnable implements Runnable {

    private final ServiceConfig config;
    private final Serializer serializer;
    private final MemorySSTableStorage sstablesForWrite;
    private final MemorySSTableStorage sstablesForFlush;

    public FlushRunnable(ServiceConfig config, Serializer serializer,
                         MemorySSTableStorage sstablesForWrite, MemorySSTableStorage sstablesForFlush) {
        this.config = config;
        this.serializer = serializer;
        this.sstablesForWrite = sstablesForWrite;
        this.sstablesForFlush = sstablesForFlush;
    }

    @Override
    public void run() {
        try {
            if (DaoUtils.isEmpty(this.sstablesForWrite)) {
                return;
            }

            PairedFiles pairedFiles = FileUtils.createPairedFiles(this.config);
            MemorySSTable memorySSTable = this.sstablesForWrite.poll();

            if (memorySSTable == null) {
                deleteFiles(pairedFiles);
                return;
            }

            this.sstablesForFlush.add(memorySSTable);
            this.serializer.write(memorySSTable.values().iterator(), pairedFiles);
            this.sstablesForFlush.remove(memorySSTable); // It is impossible that any other thread will capture memorySSTable
            this.sstablesForWrite.add(new MemorySSTable());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteFiles(PairedFiles pf) {
        try {
            Files.delete(pf.indexesFile());
            Files.delete(pf.dataFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
