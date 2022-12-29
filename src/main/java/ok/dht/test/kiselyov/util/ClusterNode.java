package ok.dht.test.kiselyov.util;

public class ClusterNode {
    private final String url;

    public ClusterNode(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public boolean hasUrl(String verifiableUrl) {
        return url.equals(verifiableUrl);
    }
}
