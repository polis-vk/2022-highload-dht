package ok.dht.test.ushkov;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConsistentHashing implements KeyManager {
    private MessageDigest md;
    private static final int[] SEEDS = new int[]{
            392682413,
            938985121,
            684318209,
            143357953,
            581029391
    };
    private static final int KEY_SEED = 288729359;
    private static final Logger LOG = LoggerFactory.getLogger(ConsistentHashing.class);
    private final List<Entry> entries = new ArrayList<>();

    public ConsistentHashing() {
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Could not create MessageDigest instance", e);
        }
    }

    @Override
    public void addNode(String nodeId) {
        List<Entry> newEntries = Arrays.stream(SEEDS)
                .mapToObj(seed -> new Entry(nodeId, hash(nodeId, seed)))
                .toList();
        entries.addAll(newEntries);
        entries.sort(Entry::compareTo);
    }

    @Override
    public void removeNode(String nodeId) {
        entries.removeIf(entry -> entry.getNodeId().equals(nodeId));
    }

    @Override
    public String getNodeIdByKey(String key) {
        int hash = hash(key, KEY_SEED);
        int i = 0;
        while (i < entries.size() && hash > entries.get(i).getHash()) {
            i++;
        }
        return entries.get(i % entries.size()).getNodeId();
    }

    private int hash(String id, int seed) {
        md.reset();
        return Arrays.hashCode(md.digest((id + seed).getBytes(StandardCharsets.UTF_8)));
    }
}
