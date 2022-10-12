package ok.dht.test.frolovm;

import ok.dht.test.frolovm.hasher.Hasher;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RendezvousHashing implements ShardingAlgorithm {

    private final List<Shard> shards;

    private final Hasher hasher;

    public Map<String, Integer> getStatistic() {
        return statistic;
    }

    private final Map<String, Integer> statistic;

    public RendezvousHashing(List<String> shards, Hasher hasher) {
        this.shards = shards.stream().map(Shard::new).sorted(Comparator.comparing(Shard::getName)).toList();
        this.hasher = hasher;
        statistic = new HashMap<>();
        for (String shard : shards) {
            statistic.put(shard, 0);
        }
    }

    @Override
    public Shard chooseShard(final String current) {

        Shard answer = null;

        int maxHash = Integer.MIN_VALUE;

        for (Shard shard : shards) {
            int hash = hasher.hash(current + shard.getName());
            if (maxHash < hash) {
                answer = shard;
                maxHash = hash;
            }
        }
        statistic.put(answer.getName(), statistic.getOrDefault(answer.getName(), 0) + 1);
        return answer;
    }
}
