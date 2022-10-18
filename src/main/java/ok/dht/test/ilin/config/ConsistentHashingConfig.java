package ok.dht.test.ilin.config;

import ok.dht.test.ilin.hashing.HashEvaluator;
import ok.dht.test.ilin.hashing.impl.FNV32HashEvaluator;

public class ConsistentHashingConfig {
    public static final int DEFAULT_VIRTUAL_NODE_COUNT = 5;

    public final HashEvaluator hashEvaluator;
    public final int virtualNodeCount;

    public ConsistentHashingConfig(
        HashEvaluator hashEvaluator,
        int virtualNodeCount
    ) {
        this.hashEvaluator = hashEvaluator;
        this.virtualNodeCount = virtualNodeCount;
    }

    public ConsistentHashingConfig() {
        this.hashEvaluator = new FNV32HashEvaluator();
        this.virtualNodeCount = DEFAULT_VIRTUAL_NODE_COUNT;
    }
}
