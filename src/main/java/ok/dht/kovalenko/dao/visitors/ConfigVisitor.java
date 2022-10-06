package ok.dht.kovalenko.dao.visitors;

import ok.dht.ServiceConfig;
import ok.dht.kovalenko.dao.Serializer;
import ok.dht.kovalenko.dao.comparators.FileComparator;
import ok.dht.kovalenko.dao.dto.FileMeta;
import ok.dht.kovalenko.dao.utils.FileUtils;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

public class ConfigVisitor
        extends SimpleFileVisitor<Path> {

    private final NavigableSet<Path> dataFiles = new TreeSet<>(FileComparator.INSTANSE);
    private final NavigableSet<Path> indexesFiles = new TreeSet<>(FileComparator.INSTANSE);
    private final Serializer serializer;
    private final Path compactedDataFilePathToBeSet;
    private final Path compactedIndexesFilePathToBeSet;
    private final AtomicLong filesCounter;

    private Path compactedDataPath;
    private Path compactedIndexesPath;

    public ConfigVisitor(ServiceConfig config, Serializer serializer, AtomicLong filesCounter) {
        this.serializer = serializer;
        this.compactedDataFilePathToBeSet
                = FileUtils.getFilePath(FileUtils.COMPACT_DATA_FILENAME_TO_BE_SET, config);
        this.compactedIndexesFilePathToBeSet
                = FileUtils.getFilePath(FileUtils.COMPACT_INDEXES_FILENAME_TO_BE_SET, config);
        this.filesCounter = filesCounter;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (FileUtils.isDataFile(file)) {
            this.dataFiles.add(file);
        } else if (FileUtils.isIndexesFile(file)) {
            this.indexesFiles.add(file);
        } else if (FileUtils.isCompactDataFile(file)) {
            this.compactedDataPath = file;
        } else if (FileUtils.isCompactIndexesFile(file)) {
            this.compactedIndexesPath = file;
        } else {
            throw new IllegalStateException("Config folder contains unresolved file: " + file);
        }
        this.filesCounter.incrementAndGet();
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        boolean needToExit = finishCompact();
        if (needToExit) {
            return FileVisitResult.CONTINUE;
        }

        deleteEmptyOrIncompletelyWrittenFiles(this.dataFiles.iterator(), this.indexesFiles.iterator());
        checkIfDataAndIndexesFilesSizeMatch();

        Iterator<Path> dataIterator = this.dataFiles.iterator();
        Iterator<Path> indexesIterator = this.indexesFiles.iterator();
        for (int priority = 1; priority <= this.dataFiles.size(); ++priority) {
            Path dataFile = dataIterator.next();
            Path indexesFile = indexesIterator.next();
            checkIfFilesArePaired(dataFile, indexesFile, priority);
        }

        return FileVisitResult.CONTINUE;
    }

    private boolean finishCompact() throws IOException {
        return finishFullCompact() || finishPartialCompact();
    }

    private boolean finishFullCompact() throws IOException {
        if (this.compactedDataPath == null) {
            return false;
        }

        // It is guaranteed that this.compactedIndexesFilePath != null (see Serializer::write for more info)
        // We have to check only FileMeta's success
        {
            FileMeta meta = this.serializer.meta(this.compactedDataPath);
            if (meta.notWritten()) {
                FileUtils.deleteFile(this.compactedDataPath, filesCounter);
                FileUtils.deleteFile(this.compactedIndexesPath, filesCounter);
                return false;
            }
        }

        // ConfigVisitor calls only in the beginning of dao's initialization,
        // so we may don't worry about multithreading problems with files' deleting below
        deleteFiles(this.dataFiles);
        deleteFiles(this.indexesFiles);
        Files.move(this.compactedDataPath, this.compactedDataFilePathToBeSet, StandardCopyOption.ATOMIC_MOVE);
        Files.move(this.compactedIndexesPath, this.compactedIndexesFilePathToBeSet, StandardCopyOption.ATOMIC_MOVE);
        return true;
    }

    private boolean finishPartialCompact() throws IOException {
        if (this.compactedIndexesPath == null) {
            return false;
        }

        for (Path dataFile : this.dataFiles) {
            if (isTargetFile(dataFile)) {
                FileUtils.deleteFile(dataFile, filesCounter);
            }
        }

        deleteFiles(this.indexesFiles);
        // It is guaranteed that compacted data has success Meta
        Files.move(this.compactedIndexesPath, this.compactedIndexesFilePathToBeSet, StandardCopyOption.ATOMIC_MOVE);
        return true;
    }

    private void deleteEmptyOrIncompletelyWrittenFiles(Iterator<Path> dataFiles, Iterator<Path> indexesFiles)
            throws IOException {
        while (dataFiles.hasNext()) {
            Path dataFile = dataFiles.next();
            Path indexesFile = indexesFiles.next();
            FileMeta meta = this.serializer.meta(dataFile);
            if (meta.notWritten()) {
                FileUtils.deleteFile(dataFile, filesCounter);
                FileUtils.deleteFile(indexesFile, filesCounter);
            }
        }
    }

    private void checkIfDataAndIndexesFilesSizeMatch() {
        if (this.dataFiles.size() != this.indexesFiles.size()) {
            throw new IllegalStateException("Mismatch in the number of data-files and indexes-files;"
                    + " expected: equal, got: " + this.dataFiles.size() + ":" + this.indexesFiles.size());
        }
    }

    private void checkIfFilesArePaired(Path dataFile, Path indexesFile, int priority) {
        if (areNotPairedFiles(dataFile, indexesFile, priority)) {
            throw new IllegalStateException("Illegal order of data- and indexes-files");
        }
    }

    private boolean areNotPairedFiles(Path dataFile, Path indexesFile, int priority) {
        int dataFileNumber = FileUtils.getFileNumber(dataFile);
        int indexesFileNumber = FileUtils.getFileNumber(indexesFile);
        return dataFileNumber != priority || dataFileNumber != indexesFileNumber;
    }

    private void deleteFiles(NavigableSet<Path> files) throws IOException {
        for (Path pathToFile : files) {
            FileUtils.deleteFile(pathToFile, filesCounter);
        }
    }

    private boolean isTargetFile(Path file) {
        return !file.getFileName().toString().equals(FileUtils.COMPACT_DATA_FILENAME_TO_BE_SET);
    }
}
