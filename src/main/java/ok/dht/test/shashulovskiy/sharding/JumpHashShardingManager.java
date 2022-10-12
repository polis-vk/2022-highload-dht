package ok.dht.test.shashulovskiy.sharding;

import java.util.List;
import java.util.Random;

public class JumpHashShardingManager implements ShardingManager {

    private final int totalShards;
    private final Shard[] shards;
    private long handledKeys;
    private final String thisUrl;

    public JumpHashShardingManager(List<String> shardUrls, String thisUrl) {
        this.totalShards = shardUrls.size();
        this.shards = new Shard[totalShards];
        this.handledKeys = 0L;
        this.thisUrl = thisUrl;

        buildShards(shardUrls);
    }

    @Override
    public Shard getShard(String key) {
        Random random = new Random(key.hashCode());
        double lastJump = -1;
        double nextJump = 0;

        while (nextJump < totalShards) {
            lastJump = nextJump;
            nextJump = Math.floor((lastJump + 1) / random.nextDouble());
        }

        Shard shard = shards[(int) lastJump];

        if (thisUrl.equals(shard.getShardUrl())) {
            handledKeys++;
            return null;
        } else {
            return shard;
        }
    }

    @Override
    public long getHandledKeys() {
        return 0;
    }

    private void buildShards(List<String> shardUrls) {
        for (int ind = 0; ind < shardUrls.size(); ++ind) {
            shards[ind] = new Shard(shardUrls.get(ind), "shard-" + ind);
        }
    }
}
