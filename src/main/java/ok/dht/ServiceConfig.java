package ok.dht;

import java.nio.file.Path;
import java.util.List;

public record ServiceConfig(
        int selfPort,
        String selfUrl,
        List<String> clusterUrls,
        Path workingDir
) {
}
