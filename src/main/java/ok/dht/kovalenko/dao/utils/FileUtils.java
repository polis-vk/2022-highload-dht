package ok.dht.kovalenko.dao.utils;

import ok.dht.ServiceConfig;
import ok.dht.kovalenko.dao.dto.PairedFiles;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class FileUtils {

    public static final int INDEX_SIZE = Integer.BYTES;
    public static final String DATA_PREFIX = "data";
    public static final String INDEXES_PREFIX = "indexes";
    public static final String EXTENSION = ".txt";
    public static final String COMPACT_SUFFIX = "Log";

    public static final String DATA_FILENAME = DATA_PREFIX + "?" + EXTENSION;
    public static final String compactDataFilenameToBeSet
            = getFullFilename(DATA_FILENAME, 1);
    public static final String INDEXES_FILENAME = INDEXES_PREFIX + "?" + EXTENSION;
    public static final String compactIndexesFilenameToBeSet
            = getFullFilename(INDEXES_FILENAME, 1);
    public static final String COMPACT_DATA_FILENAME
            = DATA_PREFIX + COMPACT_SUFFIX + "?" + EXTENSION;
    public static final String COMPACT_INDEXES_FILENAME
            = INDEXES_PREFIX + COMPACT_SUFFIX + "?" + EXTENSION;
    private static final String NUMBER_PATTERN = "(\\d+)";
    private static final Pattern DATA_FILENAME_PATTERN
            = getFilePattern(DATA_PREFIX);
    private static final Pattern INDEXES_FILENAME_PATTERN
            = getFilePattern(INDEXES_PREFIX);
    private static final Pattern COMPACT_DATA_FILENAME_PATTERN
            = getFilePattern(DATA_PREFIX + COMPACT_SUFFIX);
    private static final Pattern COMPACT_INDEXES_FILENAME_PATTERN
            = getFilePattern(INDEXES_PREFIX + COMPACT_SUFFIX);
    private static final Pattern FILE_NUMBER_PATTERN
            = Pattern.compile(NUMBER_PATTERN);

    private FileUtils() {
    }

    public static boolean isDataFile(Path file) {
        return fileMatches(file, DATA_FILENAME_PATTERN);
    }

    public static boolean isIndexesFile(Path file) {
        return fileMatches(file, INDEXES_FILENAME_PATTERN);
    }

    public static boolean isCompactDataFile(Path file) {
        return fileMatches(file, COMPACT_DATA_FILENAME_PATTERN);
    }

    public static boolean isCompactIndexesFile(Path file) {
        return fileMatches(file, COMPACT_INDEXES_FILENAME_PATTERN);
    }

    public static Integer getFileNumber(Path file) {
        Matcher matcher = FILE_NUMBER_PATTERN.matcher(file.getFileName().toString());
        if (!matcher.find()) {
            throw new IllegalArgumentException("There is no file's ordinal");
        }
        return Integer.parseInt(matcher.group());
    }

    public static String getDataFilename(long ordinal) {
        return getFullFilename(DATA_FILENAME, ordinal);
    }

    public static String getIndexesFilename(long ordinal) {
        return getFullFilename(INDEXES_FILENAME, ordinal);
    }

    public static String getCompactDataFilename(long ordinal) {
        return getFullFilename(COMPACT_DATA_FILENAME, ordinal);
    }

    public static String getCompactIndexesFilename(long ordinal) {
        return getFullFilename(COMPACT_INDEXES_FILENAME, ordinal);
    }

    public static Path getFilePath(String filename, ServiceConfig config) {
        return config.workingDir().resolve(filename);
    }

    public static Path createFile(Function<Integer, String> filenameGenerator, int priority, ServiceConfig config) throws IOException {
        String filename = filenameGenerator.apply(priority);
        Path file = FileUtils.getFilePath(filename, config);
        Files.createFile(file);
        return file;
    }

    public static PairedFiles createPairedFiles(ServiceConfig config) {
        PairedFiles pairedFiles = null;
        int fileOrdinal = 1;
        while (pairedFiles == null) {
            try {
                pairedFiles = new PairedFiles(
                        FileUtils.createFile(FileUtils::getDataFilename, fileOrdinal, config),
                        FileUtils.createFile(FileUtils::getIndexesFilename, fileOrdinal, config)
                );
            } catch (FileAlreadyExistsException ignored) {
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            ++fileOrdinal;
        }
        return pairedFiles;
    }

    public static long nFiles(ServiceConfig config) throws IOException {
        try (Stream<Path> paths = Files.list(config.workingDir())) {
            return paths.count();
        }
    }

    private static Pattern getFilePattern(String filename) {
        return Pattern.compile(filename + NUMBER_PATTERN + EXTENSION);
    }

    private static boolean fileMatches(Path file, Pattern pattern) {
        return pattern.matcher(file.getFileName().toString()).matches();
    }

    private static String getFullFilename(String filename, long ordinal) {
        return filename.replace("?", String.valueOf(ordinal));
    }
}
