package ok.dht.test.trofimov.dao.impl;

import ok.dht.test.trofimov.dao.BaseEntry;
import ok.dht.test.trofimov.dao.Config;
import ok.dht.test.trofimov.dao.Entry;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import static ok.dht.test.trofimov.dao.impl.InMemoryDao.DATA_EXT;
import static ok.dht.test.trofimov.dao.impl.InMemoryDao.INDEX_EXT;

public final class Utils {
    private static final Random rnd = new Random();
    private static final Lock writeFileListLock = new ReentrantLock();

    private Utils() {
    }

    public static String getUniqueFileName(Deque<FileInfo> files) {
        List<String> fileNames = files.stream().map(FileInfo::filename).toList();
        String name;
        do {
            name = generateString();
        } while (fileNames.contains(name));
        return name;
    }

    private static String generateString() {
        char[] chars = new char[rnd.nextInt(8, 9)];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) (rnd.nextInt('z' - '0') + '0');
        }
        return new String(chars);
    }

    public static Entry<String> findCeilEntry(RandomAccessFile raf, String key, Path indexPath) throws IOException {
        Entry<String> nextEntry = null;
        try (RandomAccessFile index = new RandomAccessFile(indexPath.toString(), "r")) {
            long lastPos = -1;
            raf.seek(0);
            int size = raf.readInt();
            long left = -1;
            long right = size;
            long mid;
            while (left < right - 1) {
                mid = left + (right - left) / 2;
                index.seek(mid * Long.BYTES);
                long entryPos = index.readLong();
                raf.seek(entryPos);
                raf.readByte(); //read tombstone
                String currentKey = raf.readUTF();
                int keyComparing = currentKey.compareTo(key);
                if (keyComparing == 0) {
                    lastPos = entryPos;
                    break;
                } else if (keyComparing > 0) {
                    lastPos = entryPos;
                    right = mid;
                } else {
                    left = mid;
                }
            }
            if (lastPos != -1) {
                raf.seek(lastPos);
                nextEntry = readEntry(raf);
            }

        }
        return nextEntry;
    }

    public static String readKeyFrom(RandomAccessFile raf, long pos) throws IOException {
        raf.seek(pos + Byte.BYTES);
        return raf.readUTF();
    }

    public static Entry<String> readEntry(RandomAccessFile raf) throws IOException {
        byte tombstone = raf.readByte();
        String key = raf.readUTF();
        String value = null;
        if (tombstone == 1) {
            value = raf.readUTF();
        } else if (tombstone == 2) {
            int valueSize = raf.readInt();
            byte[] valueBytes = new byte[valueSize];
            raf.read(valueBytes);
            value = new String(valueBytes, StandardCharsets.UTF_8);
        }
        return new BaseEntry<>(key, value);
    }

    public static FileInfo getFileInfo(Path basePath, String file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(basePath.resolve(file + DATA_EXT).toString(), "r");
             RandomAccessFile index =
                     new RandomAccessFile(basePath.resolve(file + INDEX_EXT).toString(), "r")) {
            int size = raf.readInt();
            long firstKeyPos = index.readLong();
            index.seek((long) (size - 1) * Long.BYTES);
            long lastKeyPos = index.readLong();

            String firstKey = readKeyFrom(raf, firstKeyPos);
            String lastKey = readKeyFrom(raf, lastKeyPos);

            return new FileInfo(file, firstKey, lastKey);
        }
    }

    public static void writeEntry(RandomAccessFile output, Entry<String> entry) throws IOException {
        String val = entry.value();
        if (val == null) {
            output.writeByte(-1);
            output.writeUTF(entry.key());
        } else {
            if (val.length() < 65536) {
                output.writeByte(1);
                output.writeUTF(entry.key());
                output.writeUTF(val);
            } else {
                output.writeByte(2);
                output.writeUTF(entry.key());
                byte[] b = val.getBytes(StandardCharsets.UTF_8);
                output.writeInt(b.length);
                output.write(b);
            }
        }
    }

    public static void removeOldFiles(Config config, Deque<FileInfo> filesListCopy) throws IOException {
        for (FileInfo fileToDelete : filesListCopy) {
            Files.deleteIfExists(config.basePath().resolve(fileToDelete.filename() + DATA_EXT));
            Files.deleteIfExists(config.basePath().resolve(fileToDelete.filename() + INDEX_EXT));
        }
    }

    static void writeFileListToDisk(AtomicReference<Deque<FileInfo>> filesList,
                                    RandomAccessFile allFilesOut) throws IOException {
        writeFileListLock.lock();
        try {
            // newer is at the end
            allFilesOut.setLength(0);
            Iterator<FileInfo> filesListIterator = filesList.get().descendingIterator();
            while (filesListIterator.hasNext()) {
                allFilesOut.writeUTF(filesListIterator.next().filename());
            }
        } finally {
            writeFileListLock.unlock();
        }
    }
}
