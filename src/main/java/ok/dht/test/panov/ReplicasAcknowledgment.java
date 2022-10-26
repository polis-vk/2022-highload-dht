package ok.dht.test.panov;

public class ReplicasAcknowledgment {
    public final int ack;
    public final int from;

    public ReplicasAcknowledgment(
            final String ack,
            final String from,
            final int nodesNumber
    ) throws IllegalAcknowledgmentArgumentsException {
        if (ack == null && from == null) {
            this.ack = (nodesNumber + 2) / 2;
            this.from = nodesNumber;
        } else if (ack == null || from == null) {
            throw new IllegalAcknowledgmentArgumentsException(
                    "Acknowledgment parameters should be both null or not null");
        } else {
            int parsedAck;
            int parsedFrom;

            try {
                parsedAck = Integer.parseInt(ack);
                parsedFrom = Integer.parseInt(from);
            } catch (NumberFormatException e) {
                throw new IllegalAcknowledgmentArgumentsException(
                        "Acknowledgment parameters should be numbers: " + e.getMessage(),
                        e);
            }

            if (parsedAck < 1 || parsedFrom < 1 || parsedAck > nodesNumber || parsedFrom > nodesNumber) {
                throw new IllegalAcknowledgmentArgumentsException(
                        "Acknowledgment parameters should be greater than 0 and not greater than nodes number");
            }

            if (parsedAck > parsedFrom) {
                throw new IllegalAcknowledgmentArgumentsException("ACK should be less or equals than FROM");
            }

            this.ack = parsedAck;
            this.from = parsedFrom;
        }
    }
}
