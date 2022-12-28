package ok.dht.test.mikhaylov.dao.artyomdrozdov;

import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import ok.dht.test.mikhaylov.dao.Entry;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class FileUtilities {

    private static final String FILE_NAME = "data";
    private static final String FILE_EXT = ".dat";
    static final String COMPACTED_FILE = FILE_NAME + "_compacted_" + FILE_EXT;
    private static final String FILE_EXT_TMP = ".tmp";

    private FileUtilities() {
    }

    static Path getFileName(Path base, int fileNumber) {
        return base.resolve(FILE_NAME + fileNumber + FILE_EXT);
    }

    static String getTmpFileName(String base) {
        return base + FILE_EXT_TMP;
    }

    @SuppressWarnings("DuplicateThrows")
    static MemorySegment mapForRead(ResourceScope scope, Path file) throws NoSuchFileException, IOException {
        long size = Files.size(file);

        return MemorySegment.mapFile(file, 0, size, FileChannel.MapMode.READ_ONLY, scope);
    }

    static void finishCompact(Path basePath, Path compactedFile) throws IOException {
        for (int i = 0; ; i++) {
            Path nextFile = getFileName(basePath, i);
            if (!Files.deleteIfExists(nextFile)) {
                break;
            }
        }

        Files.move(compactedFile, getFileName(basePath, 0), StandardCopyOption.ATOMIC_MOVE);
    }

    static long getSize(Entry<MemorySegment> entry) {
        return Long.BYTES + entry.key().byteSize() + Long.BYTES
                + (entry.value() == null ? 0 : entry.value().byteSize());
    }
}
