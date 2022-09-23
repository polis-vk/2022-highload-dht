package ok.dht.kovalenko.dao.runnables;

import ok.dht.ServiceConfig;
import ok.dht.kovalenko.dao.Serializer;
import ok.dht.kovalenko.dao.aliases.MemorySSTableStorage;
import ok.dht.kovalenko.dao.aliases.TypedEntry;
import ok.dht.kovalenko.dao.dto.PairedFiles;
import ok.dht.kovalenko.dao.iterators.MergeIterator;
import ok.dht.kovalenko.dao.utils.FileUtils;
import ok.dht.kovalenko.dao.visitors.CompactVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

public class CompactRunnable implements Runnable {

    private final ServiceConfig config;
    private final Serializer serializer;
    private final AtomicLong lastNFiles = new AtomicLong();

    public CompactRunnable(ServiceConfig config, Serializer serializer) {
        this.config = config;
        this.serializer = serializer;
    }

    @Override
    public void run() {
        try {
            long nFiles = FileUtils.nFiles(this.config);
            if (nFiles == 0
                    || (nFiles == 2 && serializer.meta(this.serializer.get(1).dataFile()).notTombstoned())) {
                return;
            }

            this.lastNFiles.set(FileUtils.nFiles(this.config));
            Iterator<TypedEntry> mergeIterator
                    = new MergeIterator(null, null, this.serializer, this.config, new MemorySSTableStorage(0));
            if (!mergeIterator.hasNext()) {
                return;
            }

            PairedFiles pairedFiles = FileUtils.createPairedFiles(config);
            serializer.write(mergeIterator, pairedFiles);
            Files.walkFileTree(config.workingDir(), new CompactVisitor(config, pairedFiles, serializer));
            this.lastNFiles.set(FileUtils.nFiles(this.config));
        } catch (IOException | ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
