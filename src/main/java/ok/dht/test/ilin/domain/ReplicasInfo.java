package ok.dht.test.ilin.domain;

public class ReplicasInfo {
    private final int ack;
    private final int from;

    public ReplicasInfo(int ack, int from) {
        this.ack = ack;
        this.from = from;
    }

    public ReplicasInfo(int nodesSize) {
        this.ack = nodesSize / 2 + 1;
        this.from = nodesSize;
    }

    public int ack() {
        return ack;
    }

    public int from() {
        return from;
    }
}
