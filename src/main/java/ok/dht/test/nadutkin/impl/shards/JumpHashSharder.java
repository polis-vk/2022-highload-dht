package ok.dht.test.nadutkin.impl.shards;

import java.util.Random;

public class JumpHashSharder extends Sharder {

    public JumpHashSharder(Integer shards) {
        super(shards);
    }

    private int jumpHash(String key) {
        Random random = new Random(key.hashCode());
        int lastJump = -1;
        int nextJump = 0;
        while (nextJump < shards) {
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
    public Integer getShard(String key) {
        return jumpHash(key);
    }
}
