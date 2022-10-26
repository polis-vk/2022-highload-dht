package ok.dht.test.shik.illness;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class IllNodesService {

    private static final Log LOG = LogFactory.getLog(IllNodesService.class);
    private static final int ILLNESS_RATE_MILLIS = 5 * 60 * 1000;
    private static final int FAILURES_THRESHOLD = 5;
    private static final int TIMEOUT_MILLIS = 10000;
    private static final String PATH_PREFIX = "/v0/entity";

    private final AtomicBoolean[] nodeIllness;
    private final AtomicIntegerArray nodeFailures;
    private final Map<String, Integer> clusterUrlToIndex;

    private ScheduledThreadPoolExecutor illNodesUpdaterPool;

    public IllNodesService(List<String> clusterUrls) {
        int clusterSize = clusterUrls.size();
        nodeIllness = new AtomicBoolean[clusterSize];
        nodeFailures = new AtomicIntegerArray(clusterSize);
        clusterUrlToIndex = new HashMap<>(clusterSize);
        for (int i = 0; i < clusterSize; ++i) {
            clusterUrlToIndex.put(clusterUrls.get(i), i);
        }
    }

    public void start() {
        for (int i = 0; i < nodeIllness.length; ++i) {
            nodeIllness[i] = new AtomicBoolean(false);
        }
        illNodesUpdaterPool = new ScheduledThreadPoolExecutor(1);
        illNodesUpdaterPool.scheduleAtFixedRate(() -> {
            for (AtomicBoolean illness : nodeIllness) {
                illness.set(false);
            }
            for (int i = 0; i < nodeFailures.length(); ++i) {
                nodeFailures.set(i, 0);
            }
        }, ILLNESS_RATE_MILLIS, ILLNESS_RATE_MILLIS, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        illNodesUpdaterPool.shutdown();
        try {
            if (!illNodesUpdaterPool.awaitTermination(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                illNodesUpdaterPool.shutdownNow();
                if (!illNodesUpdaterPool.awaitTermination(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    LOG.warn("Cannot terminate illness thread pool");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            illNodesUpdaterPool.shutdownNow();
        }
    }

    public void markNodeIll(URI uri) {
        String path = uri.toString();
        String clusterUrl = path.substring(0, path.indexOf(PATH_PREFIX));
        int nodeIndex = clusterUrlToIndex.get(clusterUrl);
        if (nodeFailures.incrementAndGet(nodeIndex) >= FAILURES_THRESHOLD) {
            nodeIllness[nodeIndex].set(true);
        }
    }

    public int getIllNodesCount(List<String> shardUrls) {
        int illNodes = 0;
        for (String shardUrl : shardUrls) {
            if (nodeIllness[clusterUrlToIndex.get(shardUrl)].get()) {
                ++illNodes;
            }
        }
        return illNodes;
    }
}
