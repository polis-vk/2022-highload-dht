package ok.dht.test.anikina.replication;

public class ReplicationParameters {
    private final int numberOfAcks;
    private final int numberOfReplicas;

    public ReplicationParameters(int numberOfAcks, int numberOfReplicas) {
        this.numberOfAcks = numberOfAcks;
        this.numberOfReplicas = numberOfReplicas;
    }

    public int getNumberOfAcks() {
        return numberOfAcks;
    }

    public int getNumberOfReplicas() {
        return numberOfReplicas;
    }

    public boolean areInvalid() {
        return numberOfAcks == 0 || numberOfAcks > numberOfReplicas;
    }

    public static ReplicationParameters parse(String from, String ack, int numberOfNodes) {
        int numberOfAcks;
        int numberOfReplicas;

        if (from == null || from.equals("") || ack == null || ack.equals("")) {
            numberOfReplicas = numberOfNodes;
            numberOfAcks = numberOfNodes / 2 + 1;
        } else {
            numberOfReplicas = Integer.parseInt(from);
            numberOfAcks = Integer.parseInt(ack);
        }

        return new ReplicationParameters(numberOfAcks, numberOfReplicas);
    }
}
