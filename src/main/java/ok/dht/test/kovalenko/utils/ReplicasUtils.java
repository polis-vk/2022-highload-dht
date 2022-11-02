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
        Replicas res = replicas;
        if (res == null) {
            res = new Replicas(defaultQuorums.get(clusterSize), clusterSize);
        }

        if (res.from() != clusterSize) {
            throw new IllegalArgumentException("Invalid cluster size or replicas.from parameter:" +
                    " clusterSize = " + clusterSize + ", replicas.from = " + res.from());
        }
        return res;
    }

    public static ReplicasValidation validate(String replicas) {
        try {
            if (replicas == null) {
                return new ReplicasValidation(true, null);
            }

            if (!REPLICAS_PATTERN.matcher(replicas).matches()) {
                return ReplicasValidation.INVALID;
            }

            String[] replicasParams = replicas.split(REPLICAS_PARAMETERS_SEPARATOR);
            int ack = Integer.parseInt(replicasParams[0]);
            int from = Integer.parseInt(replicasParams[1]);

            if (ack <= 0 || from <= 0 || ack > from) {
                return ReplicasValidation.INVALID;
            }

            return new ReplicasValidation(true, new Replicas(ack, from));
        } catch (Exception e) {
            log.error("Unexpected error", e);
            return ReplicasValidation.INVALID;
        }
    }

    public record Replicas(int ack, int from) {
        @Override
        public String toString() {
            return ack + REPLICAS_PARAMETERS_SEPARATOR + from;
        }
    }

    public record ReplicasValidation(boolean valid, Replicas replicas) {
        public static final ReplicasValidation INVALID = new ReplicasValidation(false, null);
    }
}
