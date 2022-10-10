package ok.dht.test.kiselyov.dao.impl;

import ok.dht.test.kiselyov.dao.BaseEntry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;

public class CompactOperations {
    private Path compactedFile;
    private Path compactedIndex;
    private static final String FILE_START_COMPACT = "startCompact.sc";
    private static final String FILE_START_COMPACT_INDEX = "startCompactIndex.sdx";
    private static final String FILE_CONTINUE_COMPACT = "continueCompact.cc";
    private static final String FILE_CONTINUE_COMPACT_INDEX = "continueCompactIndex.cdx";
    private final String fileName;
    private final String fileExtension;
    private final String fileIndexName;
    private final String fileIndexExtension;

    public CompactOperations(String fileName, String fileExtension, String fileIndexName, String fileIndexExtension) {
        this.fileName = fileName;
        this.fileExtension = fileExtension;
        this.fileIndexName = fileIndexName;
        this.fileIndexExtension = fileIndexExtension;
    }

    Map<Path, Path> checkFiles(Path basePath) throws IOException {
        Map<Path, Path> allData = new LinkedHashMap<>();
        try (Stream<Path> filesStream = Files.list(basePath)) {
            List<Path> files = filesStream.toList();
            checkCompactConflicts(basePath, files);
            List<Path> ssTables = files
                    .stream().toList().stream()
                    .filter(f -> String.valueOf(f.getFileName()).startsWith(fileName))
                    .sorted(new PathsComparator(fileName, fileExtension))
                    .toList();
            List<Path> ssIndexes = files
                    .stream().toList().stream()
                    .filter(f -> String.valueOf(f.getFileName()).startsWith(fileIndexName))
                    .sorted(new PathsComparator(fileIndexName, fileIndexExtension))
                    .toList();
            if (ssTables.size() != ssIndexes.size()) {
                throw new NoSuchFileException("Not all files found!");
            }
            for (int i = 0; i < ssTables.size(); i++) {
                Path data = ssTables.get(i);
                Path index = ssIndexes.get(i);
                checkDataAndIndexCompliance(data, index);
                allData.put(data, index);
            }
        }
        return allData;
    }

    private void checkCompactConflicts(Path basePath, List<Path> files) throws IOException {
        for (Path file : files) {
            if (checkStartCompactConflicts(basePath, file, files)) {
                break;
            }
            if (checkContinueCompactionConflicts(basePath, file, files)) {
                break;
            }
        }
    }

    private boolean checkStartCompactConflicts(Path basePath, Path file, List<Path> files) throws IOException {
        if (file == basePath.resolve(FILE_START_COMPACT)) {
            Files.delete(basePath.resolve(FILE_START_COMPACT));
            if (files.contains(basePath.resolve(FILE_START_COMPACT_INDEX))) {
                Files.delete(basePath.resolve(FILE_START_COMPACT_INDEX));
            }
            return true;
        }
        return false;
    }

    private boolean checkContinueCompactionConflicts(Path basePath, Path file, List<Path> files) throws IOException {
        if (file == basePath.resolve(FILE_CONTINUE_COMPACT)) {
            if (files.contains(basePath.resolve(FILE_CONTINUE_COMPACT_INDEX))) {
                List<Path> ssTables = files
                        .stream().toList().stream()
                        .filter(f -> f.getFileName().toString().startsWith(fileName))
                        .sorted(new PathsComparator(fileName, fileExtension))
                        .collect(Collectors.toList());
                List<Path> ssIndexes = files
                        .stream().toList().stream()
                        .filter(f -> f.getFileName().toString().startsWith(fileIndexName))
                        .sorted(new PathsComparator(fileIndexName, fileIndexExtension))
                        .collect(Collectors.toList());
                deleteFiles(ssTables);
                deleteFiles(ssIndexes);
                Files.move(basePath.resolve(FILE_CONTINUE_COMPACT),
                        basePath.resolve(fileName + "0" + fileExtension), ATOMIC_MOVE);
                Files.move(basePath.resolve(FILE_CONTINUE_COMPACT_INDEX),
                        basePath.resolve(fileIndexName + "0" + fileIndexExtension), ATOMIC_MOVE);
            } else {
                Files.delete(basePath.resolve(FILE_CONTINUE_COMPACT));
            }
            return true;
        }
        return false;
    }

    private void deleteFiles(List<Path> filePaths) throws IOException {
        for (Path filePath : filePaths) {
            Files.delete(filePath);
        }
    }

    private void checkDataAndIndexCompliance(Path data, Path index) throws IOException {
        String dataStr = data.toString();
        String indexStr = index.toString();
        if (Integer.parseInt(dataStr.substring(dataStr.indexOf(fileName) + fileName.length(),
                dataStr.indexOf(fileExtension)))
                != Integer.parseInt(indexStr.substring(indexStr.indexOf(fileIndexName) + fileIndexName.length(),
                indexStr.indexOf(fileIndexExtension)))) {
            throw new NoSuchFileException("Not all files match!");
        }
    }

    void saveDataAndIndexesCompact(Iterator<BaseEntry<byte[]>> iterator, Path basePath) throws IOException {
        long elementsCount = 0;
        long offset = 0;
        compactedFile = basePath.resolve(FILE_START_COMPACT);
        compactedIndex = basePath.resolve(FILE_START_COMPACT_INDEX);
        Files.createFile(compactedFile);
        Files.createFile(compactedIndex);
        try (FileReaderWriter writerFile = new FileReaderWriter(compactedFile, compactedIndex)) {
            writeIndexInitialPosition(writerFile.getIndexChannel());
            while (iterator.hasNext()) {
                BaseEntry<byte[]> current = iterator.next();
                Map.Entry<byte[], BaseEntry<byte[]>> currentBaseEntry =
                        Map.entry(current.key(), new BaseEntry<>(current.key(), current.value()));
                FileOperations.writePair(writerFile.getFileChannel(), currentBaseEntry);
                offset = FileOperations.writeEntryPosition(writerFile.getIndexChannel(), currentBaseEntry, offset);
                elementsCount++;
            }
            writeIndexSize(elementsCount, writerFile.getIndexChannel());
        }
        Files.move(compactedFile, basePath.resolve(FILE_CONTINUE_COMPACT), ATOMIC_MOVE);
        Files.move(compactedIndex, basePath.resolve(FILE_CONTINUE_COMPACT_INDEX), ATOMIC_MOVE);
        compactedFile = basePath.resolve(FILE_CONTINUE_COMPACT);
        compactedIndex = basePath.resolve(FILE_CONTINUE_COMPACT_INDEX);
    }

    private static void writeIndexInitialPosition(FileChannel channel) throws IOException {
        ByteBuffer longBuffer = ByteBuffer.allocate(Long.BYTES);
        channel.position(Long.BYTES);
        longBuffer.putLong(0);
        longBuffer.flip();
        channel.write(longBuffer);
    }

    private static void writeIndexSize(long elementsCount, FileChannel channel) throws IOException {
        ByteBuffer longBuffer = ByteBuffer.allocate(Long.BYTES);
        channel.position(0);
        longBuffer.putLong(elementsCount);
        longBuffer.flip();
        channel.write(longBuffer);
    }

    void renameCompactedFile(Path basePath) throws IOException {
        Files.move(compactedFile, basePath.resolve(fileName + "0" + fileExtension), ATOMIC_MOVE);
        Files.move(compactedIndex, basePath.resolve(fileIndexName + "0" + fileIndexExtension), ATOMIC_MOVE);
    }

    void clearFileIterators(List<FileIterator> fileIterators) throws IOException {
        for (FileIterator fi : fileIterators) {
            if (fi != null) {
                fi.close();
            }
        }
        fileIterators.clear();
    }

    void deleteAllFiles(List<Path> ssTables, List<Path> ssIndexes) throws IOException {
        for (Path ssTable : ssTables) {
            Files.delete(ssTable);
        }
        for (Path ssIndex : ssIndexes) {
            Files.delete(ssIndex);
        }
    }
}
