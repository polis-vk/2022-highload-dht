package ok.dht.test.mikhaylov.internal;

import one.nio.http.Request;

public class ReplicaRequirements {
    private final int ack;

    private final int from;

    public ReplicaRequirements(Request request, int clusterSize) {
        String ackStr = request.getParameter("ack=");
        String fromStr = request.getParameter("from=");
        if (ackStr == null && fromStr == null) {
            ack = clusterSize / 2 + 1;
            from = clusterSize;
        } else if (ackStr == null || fromStr == null) {
            throw new IllegalArgumentException("Both ack and from must be specified (or neither)");
        } else {
            try {
                ack = Integer.parseInt(ackStr);
                from = Integer.parseInt(fromStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Could not parse ack or from", e);
            }
            if (ack <= 0) {
                throw new IllegalArgumentException("Ack must be positive");
            }
            if (from <= 0) {
                throw new IllegalArgumentException("From must be positive");
            }
            if (from > clusterSize) {
                throw new IllegalArgumentException("From must be less than cluster size");
            }
            if (ack > from) {
                throw new IllegalArgumentException("Ack must be less than or equal to from");
            }
        }
    }

    public int getAck() {
        return ack;
    }

    public int getFrom() {
        return from;
    }
}
