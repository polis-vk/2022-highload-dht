package ok.dht.test.armenakyan.sharding;

import ok.dht.test.armenakyan.sharding.hashing.ConsistentHashing;
import ok.dht.test.armenakyan.sharding.hashing.KeyHasher;
import ok.dht.test.armenakyan.sharding.model.Shard;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClusterCoordinatorShardHandler implements ShardRequestHandler {
    private final ConsistentHashing consistentHashing;
    private final List<Shard> shards;

    public ClusterCoordinatorShardHandler(String selfUrl,
                                          ShardRequestHandler selfHandler,
                                          List<String> shardUrls,
                                          KeyHasher keyHasher) {
        List<Shard> shardList = new ArrayList<>();
        shardList.add(new Shard(selfUrl, selfHandler));

        for (String shardUrl : shardUrls) {
            if (shardUrl.equals(selfUrl)) {
                continue;
            }
            shardList.add(new Shard(shardUrl, new ProxyShardHandler(shardUrl)));
        }

        this.shards = shardList;
        this.consistentHashing = new ConsistentHashing(shardList, keyHasher);
    }

    @Override
    public Response handleForKey(String key, Request request) throws IOException {
        return consistentHashing
                .shardByKey(key)
                .requestHandler()
                .handleForKey(key, request);
    }

    @Override
    public void close() throws IOException {
        for (Shard shard : shards) {
            shard.requestHandler().close();
        }
    }
}
