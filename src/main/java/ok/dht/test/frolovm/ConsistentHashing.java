package ok.dht.test.frolovm;

import ok.dht.test.frolovm.hasher.Hasher;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConsistentHashing implements ShardingAlgorithm {

    private static final int SHARD_VNODES = 4;
    private final List<Shard> shards;
    private final Hasher hasher;
    private final Map<Integer, Integer> hashing;

    private final int[] vnodes;

    public ConsistentHashing(List<String> shardNames, Hasher hasher) {
        this.shards = shardNames.stream().map(Shard::new).sorted(Comparator.comparing(Shard::getName)).toList();
        this.hasher = hasher;
        this.vnodes = new int[SHARD_VNODES * this.shards.size()];
        this.hashing = new HashMap<>();

        initVnodes(hasher, this.shards);
    }

    private void initVnodes(Hasher hasher, List<Shard> shards) {
        for (int curShard = 0; curShard < this.shards.size(); ++curShard) {
            for (int nodeInd = 0; nodeInd < SHARD_VNODES; ++nodeInd) {
                int hash = hasher.hash(shards.get(curShard).getName() + nodeInd);
                int currentIndexVnode = SHARD_VNODES * curShard + nodeInd;

                vnodes[currentIndexVnode] = hash;
                hashing.put(hash, curShard);
            }
        }
        Arrays.sort(vnodes);
    }

    @Override
    public int chooseShard(String shard) {
        int hash = hasher.hash(shard);
        int index = Arrays.binarySearch(vnodes, hash);
        int answer;
        if (index >= 0) {
            answer = hashing.get(vnodes[index]);
        } else {
            answer = hashing.get(vnodes[(-index - 1) % vnodes.length]);
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
