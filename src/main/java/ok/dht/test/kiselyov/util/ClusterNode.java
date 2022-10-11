package ok.dht.test.kiselyov.util;

import java.io.IOException;

public class ClusterNode {
    private final String url;

    public ClusterNode(String url) throws IOException {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
