package ok.dht.test.panov;

import java.util.List;

public class RoutingConfig {
    public final String selfUrl;
    public final List<String> clusterUrls;

    public RoutingConfig(String selfUrl, List<String> clusterUrls) {
        this.selfUrl = selfUrl;
        this.clusterUrls = clusterUrls;
    }
}
