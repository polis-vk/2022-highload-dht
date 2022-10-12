package ok.dht.test.frolovm;

import java.util.Map;

public interface ShardingAlgorithm {

    Shard chooseShard(String shard);

    Map<String, Integer> getStatistic();
}
