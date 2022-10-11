package ok.dht.kovalenko.dao.dto;

import java.nio.file.Path;

public record PairedFiles(Path dataFile, Path indexesFile) {
}
