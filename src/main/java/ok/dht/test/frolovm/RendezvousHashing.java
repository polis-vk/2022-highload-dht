package ok.dht.test.frolovm;

import ok.dht.test.frolovm.hasher.Hasher;

import java.util.Comparator;
import java.util.List;

public class RendezvousHashing implements ShardingAlgorithm {

    private final List<Shard> shards;

    private final Hasher hasher;

    public RendezvousHashing(List<String> shards, Hasher hasher) {
        this.shards = shards.stream().map(Shard::new).sorted(Comparator.comparing(Shard::getName)).toList();
        this.hasher = hasher;
    }

    @Override
    public int chooseShard(final String current) {

        int answer = -1;

        int maxHash = Integer.MIN_VALUE;

        for (int i = 0; i < shards.size(); ++i) {
            Shard shard = shards.get(i);
            int hash = hasher.hash(current + shard.getName());
            if (maxHash < hash) {
                answer = i;
                maxHash = hash;
            }
        }
        if (answer == -1) {
            throw new IllegalArgumentException("Can't find node for key");
        }
        return answer;
    }

    @Override
    public Shard getShardByIndex(int index) {
        return shards.get(index);
    }

    @Override
    public List<Shard> getShards() {
        return shards;
    }
}
