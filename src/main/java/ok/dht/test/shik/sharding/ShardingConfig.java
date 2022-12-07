package ok.dht.test.shik.sharding;

public class ShardingConfig {

    private final int virtualNodesNumber;

    public ShardingConfig() {
        virtualNodesNumber = 1;
    }

    public ShardingConfig(int virtualNodesNumber) {
        this.virtualNodesNumber = virtualNodesNumber;
    }

    public int getVNodesNumber() {
        return virtualNodesNumber;
    }

}

