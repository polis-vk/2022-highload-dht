package ok.dht.kovalenko.dao.runnables;

import ok.dht.ServiceConfig;
import ok.dht.kovalenko.dao.Serializer;
import ok.dht.kovalenko.dao.aliases.MappedFileDiskSSTableStorage;
import ok.dht.kovalenko.dao.aliases.TypedIterator;
import ok.dht.kovalenko.dao.dto.PairedFiles;
import ok.dht.kovalenko.dao.iterators.MergeIterator;
import ok.dht.kovalenko.dao.utils.DaoUtils;
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
    private final MappedFileDiskSSTableStorage diskStorage;
    private final AtomicBoolean wasCompacted;
    private final AtomicLong filesCounter;

    public CompactRunnable(ServiceConfig config, Serializer serializer, MappedFileDiskSSTableStorage diskStorage,
                           AtomicBoolean wasCompacted, AtomicLong filesCounter) {
        this.config = config;
        this.serializer = serializer;
        this.diskStorage = diskStorage;
        this.wasCompacted = wasCompacted;
        this.filesCounter = filesCounter;
    }

    @Override
    public void run() {
        try {
            if (this.wasCompacted.get()) {
                return;
            }

            TypedIterator mergeIterator
                    = new MergeIterator(Collections.emptyList(), this.diskStorage.get(DaoUtils.EMPTY_BYTEBUFFER, null));
            if (!mergeIterator.hasNext()) {
                return;
            }

            PairedFiles pairedFiles = FileUtils.createPairedFiles(this.config, this.filesCounter);
            this.serializer.write(mergeIterator, pairedFiles);
            Files.walkFileTree(this.config.workingDir(),
                    new CompactVisitor(this.config, pairedFiles, this.serializer, this.filesCounter));
            this.wasCompacted.set(true);
        } catch (IOException | ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
