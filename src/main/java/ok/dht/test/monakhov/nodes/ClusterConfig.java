package ok.dht.test.monakhov.nodes;

import java.util.List;

public class ClusterConfig {
    public static List<String> clusterUrls = List.of(
        "http://localhost:19234",
        "http://localhost:19235",
        "http://localhost:19236"
    );

    public static List<Integer> clusterPorts = List.of(
        19234,
        19235,
        19236
    );

}
