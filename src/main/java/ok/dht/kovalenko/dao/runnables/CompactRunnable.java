package ok.dht.kovalenko.dao.runnables;

import ok.dht.ServiceConfig;
import ok.dht.kovalenko.dao.Serializer;
import ok.dht.kovalenko.dao.aliases.MappedFileDiskSSTableStorage;
import ok.dht.kovalenko.dao.aliases.TypedIterator;
import ok.dht.kovalenko.dao.dto.ByteBufferRange;
import ok.dht.kovalenko.dao.dto.PairedFiles;
import ok.dht.kovalenko.dao.iterators.MergeIterator;
import ok.dht.kovalenko.dao.utils.FileUtils;
import ok.dht.kovalenko.dao.visitors.CompactVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class CompactRunnable implements Runnable {

    private final ServiceConfig config;
    private final Serializer serializer;
    private final AtomicLong lastNFiles = new AtomicLong();
    private final MappedFileDiskSSTableStorage diskStorage;
    private final AtomicBoolean wasCompacted;

    public CompactRunnable(ServiceConfig config, Serializer serializer,
                           MappedFileDiskSSTableStorage diskStorage, AtomicBoolean wasCompacted) {
        this.config = config;
        this.serializer = serializer;
        this.diskStorage = diskStorage;
        this.wasCompacted = wasCompacted;
    }

    @Override
    public void run() {
        try {
            if (this.wasCompacted.get()) {
                return;
            }

            this.lastNFiles.set(FileUtils.nFiles(this.config));
            TypedIterator mergeIterator
                    = new MergeIterator(Collections.emptyList(), this.diskStorage.get(ByteBufferRange.ALL_RANGE));
            if (!mergeIterator.hasNext()) {
                return;
            }

            PairedFiles pairedFiles = FileUtils.createPairedFiles(this.config);
            this.serializer.write(mergeIterator, pairedFiles);
            Files.walkFileTree(this.config.workingDir(), new CompactVisitor(this.config, pairedFiles, this.serializer));
            this.lastNFiles.set(FileUtils.nFiles(this.config));
            this.wasCompacted.set(true);
        } catch (IOException | ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
