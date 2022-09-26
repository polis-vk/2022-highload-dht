package ok.dht.test.galeev.dao.utils;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.galeev.dao.iterators.MergeIterator;
import ok.dht.test.galeev.dao.iterators.PriorityPeekingIterator;
import ok.dht.test.galeev.dao.entry.Entry;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

public class DBReader implements AutoCloseable {
    private static final String DB_FILES_EXTENSION = ".txt";

    private final TreeSet<FileDBReader> fileReaders;

    public DBReader(Path dbDirectoryPath) throws IOException {
        fileReaders = getFileDBReaders(dbDirectoryPath);
    }

    private static TreeSet<FileDBReader> getFileDBReaders(Path dbDirectoryPath) throws IOException {
        TreeSet<FileDBReader> fileDBReadersSet = new TreeSet<>(Comparator.comparing(FileDBReader::getFileID));
        try (Stream<Path> paths = Files.list(dbDirectoryPath)
                .filter(path -> path.getFileName().toString().endsWith(DB_FILES_EXTENSION))
        ) {
            for (Path path : paths.toList()) {
                FileDBReader fileDBReader = new FileDBReader(path);
                fileDBReadersSet.add(fileDBReader);
            }
        }

        return fileDBReadersSet;
    }

    public boolean hasNoReaders() {
        return fileReaders.isEmpty();
    }

    public long getBiggestFileId() {
        if (fileReaders.isEmpty()) {
            return -1;
        }
        return fileReaders.last().getFileID();
    }

    public Iterator<Entry<MemorySegment, MemorySegment>> get(MemorySegment from, MemorySegment to) {
        if (fileReaders.isEmpty()) {
            return Collections.emptyIterator();
        }
        List<PriorityPeekingIterator<Entry<MemorySegment, MemorySegment>>> iterators = new ArrayList<>(fileReaders.size());
        for (FileDBReader reader : fileReaders) {
            FileDBReader.FileIterator fromToIterator = reader.getFromToIterator(from, to);
            if (fromToIterator.hasNext()) {
                iterators.add(new PriorityPeekingIterator<>(fromToIterator.getFileId(), fromToIterator));
            }
        }
        return new MergeIterator<>(iterators, MemorySegmentComparator.INSTANCE);
    }

    public Entry<MemorySegment, MemorySegment> get(MemorySegment key) {
        for (FileDBReader fileDBReader : fileReaders.descendingSet()) {
            Entry<MemorySegment, MemorySegment> entryByKey = fileDBReader.getEntryByKey(key);
            if (entryByKey != null) {
                return entryByKey.value() == null ? null : entryByKey;
            }
        }
        return null;
    }

    public void clear() throws IOException {
        for (FileDBReader fileDBReader : fileReaders) {
            fileDBReader.deleteFile();
        }
    }

    @Override
    public void close() throws IOException {
        for (FileDBReader fileReader : fileReaders) {
            fileReader.close();
        }
    }
}
