package ok.dht.test.kovalenko.utils;

import ok.dht.test.kovalenko.MyServiceBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class ReplicasUtils {

    private static final Logger log = LoggerFactory.getLogger(ReplicasUtils.class);
    private static final Map<Integer /*from*/, Integer /*ack*/> defaultQuorums = new HashMap<>();
    private static final String REPLICAS_PARAMETERS_SEPARATOR = "/";
    private static final Pattern REPLICAS_PATTERN
            = Pattern.compile("(\\d+)" + REPLICAS_PARAMETERS_SEPARATOR + "(\\d+)");

    static {
        defaultQuorums.put(1, 1);
        defaultQuorums.put(2, 2);
        defaultQuorums.put(3, 2);
        defaultQuorums.put(4, 3);
        defaultQuorums.put(5, 3);
    }

    public static Replicas recreate(Replicas replicas, int clusterSize) {
        return replicas == null
                ? new Replicas(defaultQuorums.get(clusterSize), clusterSize)
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
            log.error("Unexpected error", e);
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
