package ok.dht.test.kiselyov.dao.impl;

import java.nio.file.Path;
import java.util.Comparator;

public class PathsComparator implements Comparator<Path> {

    private final String fileName;
    private final String fileExtension;

    public PathsComparator(String fileName, String fileExtension) {
        this.fileName = fileName;
        this.fileExtension = fileExtension;
    }

    @Override
    public int compare(Path o1, Path o2) {
        String str1 = String.valueOf(o1.getFileName());
        String str2 = String.valueOf(o2.getFileName());
        return Integer.parseInt(str2.substring(str2.indexOf(fileName) + fileName.length(),
                str2.indexOf(fileExtension)))
                - Integer.parseInt(str1.substring(str1.indexOf(fileName) + fileName.length(),
                str1.indexOf(fileExtension)));
    }
}
