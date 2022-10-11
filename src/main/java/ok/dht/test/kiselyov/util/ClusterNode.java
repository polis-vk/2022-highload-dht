package ok.dht.test.kiselyov.util;

import java.io.IOException;

public class ClusterNode {
    private final String url;
    private final Boolean isCurrent;

    public ClusterNode(String url, Boolean isCurrent) throws IOException {
        this.url = url;
        this.isCurrent = isCurrent;
    }

    public String getUrl() {
        return url;
    }

    public Boolean getIsCurrent() {
        return isCurrent;
    }
}
