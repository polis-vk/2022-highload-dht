package ok.dht.test.frolovm;

public interface ShardingAlgorithm {

    Shard chooseShard(String shard);

}
