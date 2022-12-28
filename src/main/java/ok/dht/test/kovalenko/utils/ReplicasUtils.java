package ok.dht.test.kovalenko.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplicasUtils {

    private static final Logger log = LoggerFactory.getLogger(ReplicasUtils.class);

    public static Replicas recreate(Replicas replicas, int clusterSize) {
        return replicas == null
                ? new Replicas(clusterSize / 2 + 1, clusterSize)
                : replicas;
    }

    public static ReplicasValidation validate(String ack, String from) {
        try {
            if (ack == null && from == null) {
                return new ReplicasValidation(true, null);
            }

            if (ack == null || from == null) {
                return ReplicasValidation.INVALID;
            }

            int intAck = Integer.parseInt(ack);
            int intFrom = Integer.parseInt(from);

            if (intAck <= 0 || intFrom <= 0 || intAck > intFrom) {
                return ReplicasValidation.INVALID;
            }

            return new ReplicasValidation(true, new Replicas(intAck, intFrom));
        } catch (Exception e) {
            log.error("Error when validating replicas parameter", e);
            return ReplicasValidation.INVALID;
        }
    }

    public record Replicas(int ack, int from) {
        public String toHttpString() {
            return "&ack=" + ack + "&from=" + from;
        }
    }

    public record ReplicasValidation(boolean valid, Replicas replicas) {
        public static final ReplicasValidation INVALID = new ReplicasValidation(false, null);
    }
}
