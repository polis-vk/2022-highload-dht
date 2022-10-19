package ok.dht.test.monakhov.nodes;

import ok.dht.ServiceConfig;
import ok.dht.test.monakhov.DbService;

import java.util.concurrent.TimeUnit;

public class Node3 {

    private Node3() {
    }

    public static void main(String[] args) throws Exception {
        String url = ClusterConfig.clusterUrls.get(2);
        int port = ClusterConfig.clusterPorts.get(2);
        ServiceConfig cfg = new ServiceConfig(
            port,
            url,
            ClusterConfig.clusterUrls,
            // Files.createTempDirectory("server")
            java.nio.file.Path.of("src/main/java/ok/dht/test/monakhov/storage/storage3")
        );
        new DbService(cfg).start().get(1, TimeUnit.SECONDS);
        System.out.println("Socket is ready: " + url);
    }
}
