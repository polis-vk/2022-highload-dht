package ok.dht.kovalenko.dao.visitors;

import ok.dht.ServiceConfig;
import ok.dht.kovalenko.dao.Serializer;
import ok.dht.kovalenko.dao.dto.PairedFiles;
import ok.dht.kovalenko.dao.utils.FileUtils;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;

public class CompactVisitor extends SimpleFileVisitor<Path> {

    private final Path compactedDataPath;
    private final Path compactedIndexesPath;
    private final Path dataPathToBeSet;
    private final Path indexesPathToBeSet;
    private final Serializer serializer;
    private final AtomicLong filesCounter;

    private int numberOfDeletedFiles;

    public CompactVisitor(ServiceConfig config, PairedFiles pairedFiles, Serializer serializer,
                          AtomicLong filesCounter) {
        this.compactedDataPath = pairedFiles.dataFile();
        this.compactedIndexesPath = pairedFiles.indexesFile();
        this.dataPathToBeSet = FileUtils.getFilePath(FileUtils.COMPACT_DATA_FILENAME_TO_BE_SET, config);
        this.indexesPathToBeSet = FileUtils.getFilePath(FileUtils.COMPACT_INDEXES_FILENAME_TO_BE_SET, config);
        this.serializer = serializer;
        this.filesCounter = filesCounter;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (isTargetFile(file) && serializer.meta(file).written()) {
            FileUtils.deleteFile(file, filesCounter);
            ++numberOfDeletedFiles;
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        if (numberOfDeletedFiles % 2 == 1) {
            throw new IllegalStateException("Config folder was corrupted (odd number of files)");
        }
        Files.move(this.compactedDataPath, this.dataPathToBeSet, StandardCopyOption.ATOMIC_MOVE);
        Files.move(this.compactedIndexesPath, this.indexesPathToBeSet, StandardCopyOption.ATOMIC_MOVE);
        return FileVisitResult.CONTINUE;
    }

    private boolean isTargetFile(Path file) {
        return FileUtils.isDataFile(file) || FileUtils.isIndexesFile(file);
    }
}
