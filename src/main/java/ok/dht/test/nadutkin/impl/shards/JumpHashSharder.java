package ok.dht.test.nadutkin.impl.shards;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class JumpHashSharder extends Sharder {

    public JumpHashSharder(List<String> shards) {
        super(shards);
    }

    private int jumpHash(String key) {
        Random random = new Random(key.hashCode());
        int lastJump = -1;
        int nextJump = 0;
        while (nextJump < shards.size()) {
            lastJump = nextJump;
            double step = random.nextDouble(0, 1);
            if (step == 0) {
                break;
            }
            nextJump = (int) ((double) (lastJump + 1) / step);
        }
        return lastJump;
    }

    @Override
    public List<String> getShardUrls(String key, int from) {
        int index = jumpHash(key);
        List<String> destinations = new ArrayList<>();
        for (int i = 0; i < from; i++) {
            destinations.add(shards.get((index + i) % shards.size()));
        }
        return destinations;
    }
}
