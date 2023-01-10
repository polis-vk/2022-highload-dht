package ok.dht.test.slastin.sharding;

import java.util.List;

public interface ShardingManager {
    int getNodeIndexByKey(String key);

    String getNodeUrlByNodeIndex(int nodeIndex);

    List<Integer> getNodeIndices(String key, int count);
}
