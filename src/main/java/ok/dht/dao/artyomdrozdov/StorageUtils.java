package ok.dht.dao.artyomdrozdov;

import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;


public class StorageUtils {

    @SuppressWarnings("DuplicateThrows")
    public static MemorySegment mapForRead(ResourceScope scope, Path file) throws NoSuchFileException, IOException {
        long size = Files.size(file);

        return MemorySegment.mapFile(file, 0, size, FileChannel.MapMode.READ_ONLY, scope);
    }

    public static RuntimeException checkForClose(Storage storage, IllegalStateException e) {
        if (storage.isClosed()) {
            throw new StorageClosedException(e);
        } else {
            throw e;
        }
    }
}
