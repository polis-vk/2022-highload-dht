package ok.dht.test.frolovm;

import java.util.List;

public interface ShardingAlgorithm {

    int chooseShard(String shard);

    Shard getShardByIndex(int index);

    List<Shard> getShards();

}
