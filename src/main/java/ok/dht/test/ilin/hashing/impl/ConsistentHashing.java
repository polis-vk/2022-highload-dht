package ok.dht.test.ilin.hashing.impl;

import ok.dht.test.ilin.config.ConsistentHashingConfig;
import ok.dht.test.ilin.hashing.HashEvaluator;

import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

public class ConsistentHashing {
    private final NavigableMap<Integer, VirtualNode> virtualNodes;
    private final HashEvaluator hashEvaluator;

    public ConsistentHashing(List<String> topology, ConsistentHashingConfig config) {
        this.virtualNodes = initVirtualNodes(topology, config.virtualNodeCount, config.hashEvaluator);
        this.hashEvaluator = config.hashEvaluator;
    }

    public String getServerAddressFromKey(String key) {
        int hash = hashEvaluator.hash(key);
        Integer consistentKey = virtualNodes.ceilingKey(hash);
        VirtualNode virtualNode;
        if (consistentKey == null) {
            virtualNode = virtualNodes.firstEntry().getValue();
        } else {
            virtualNode = virtualNodes.get(consistentKey);
        }
        if (virtualNode != null) {
            return virtualNode.address;
        }
        return null;
    }

    private NavigableMap<Integer, VirtualNode> initVirtualNodes(
        List<String> topology,
        int virtualNodeCount,
        HashEvaluator hashEvaluator
    ) {
        NavigableMap<Integer, VirtualNode> virtualNodes = new TreeMap<>();
        for (String nodeAddress : topology) {
            for (int i = 0; i < virtualNodeCount; i++) {
                VirtualNode node = new VirtualNode(nodeAddress, i);
                virtualNodes.put(hashEvaluator.hash(node.name), node);
            }
        }
        return virtualNodes;
    }

    private static class VirtualNode {
        private static final String VIRTUAL_NODE_NAME_PREFIX = "VN";
        String name;
        String address;

        public VirtualNode(String address, int num) {
            this.name = VIRTUAL_NODE_NAME_PREFIX + address + num;
            this.address = address;
        }
    }
}
