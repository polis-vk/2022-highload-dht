package ok.dht.test.kuleshov.sharding;

import java.io.Serializable;
import java.util.List;

public class ShardAddBody implements Serializable {
    private final String url;
    private final List<Integer> hashes;

    public ShardAddBody(String url, List<Integer> hashes) {
        this.url = url;
        this.hashes = hashes;
    }

    public String getUrl() {
        return url;
    }

    public List<Integer> getHashes() {
        return hashes;
    }

    @Override
    public String toString() {
        return "ShardAddBody{"
                + "url='" + url + '\''
                + ", hashes=" + hashes
                + '}';
    }
}
