package ok.dht.test.slastin.node;

public class NodeConfig {
    private final int maxTasks;
    private final int maxWorkers;

    public NodeConfig(int maxTasks, int maxWorkers) {
        this.maxTasks = maxTasks;
        this.maxWorkers = maxWorkers;
    }

    public int maxTasks() {
        return maxTasks;
    }

    public int maxWorkers() {
        return maxWorkers;
    }
}
