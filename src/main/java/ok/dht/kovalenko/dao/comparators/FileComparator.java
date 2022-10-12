package ok.dht.kovalenko.dao.comparators;

import ok.dht.kovalenko.dao.utils.FileUtils;

import java.nio.file.Path;
import java.util.Comparator;

public final class FileComparator
        implements Comparator<Path> {

    public static final FileComparator INSTANSE = new FileComparator();

    private FileComparator() {
    }

    @Override
    public int compare(Path p1, Path p2) {
        return FileUtils.getFileNumber(p1).compareTo(FileUtils.getFileNumber(p2));
    }
}
