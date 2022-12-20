package ok.dht.test.maximenko;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class KeyDispatcher {

    private static final int VIRTUAL_NODES_PER_MACHINE = 5;
    Map<Integer, Integer> virtualNodes;
    private final int maxBorder;
    final List<Integer> nodes;

    KeyDispatcher(List<Integer> nodes) {
        int nodeAmount = nodes.size();
        int interval = Integer.MAX_VALUE / (nodeAmount * VIRTUAL_NODES_PER_MACHINE);
        virtualNodes = new TreeMap<>();
        int border = interval;
        for (int i = 0; i < VIRTUAL_NODES_PER_MACHINE; i++) {
            for (Integer node : nodes) {
                virtualNodes.put(border, node);
                border += interval;
            }
        }
        maxBorder = border;
        this.nodes = nodes;
    }

    public ArrayDeque<Integer> getReplicas(String key, int replicasAmount) {
        ArrayDeque<Integer> result = new ArrayDeque<>();
        int hash = Math.abs(key.hashCode());
        for (int i = 0; i < replicasAmount; i++) {
            Map.Entry<Integer, Integer> virtualNode = ((TreeMap<Integer, Integer>)virtualNodes).higherEntry(hash);
            result.add(virtualNode.getValue());
            hash = (virtualNode.getKey() + 1) % maxBorder;
        }
        return result;
    }

    public List<Integer> getNodesCoverage(int replicaFactor) {
        int nodeIndex = 0;
        List<Integer> result = new ArrayList<>();
        while (nodeIndex < nodes.size()) {
            result.add(nodes.get(nodeIndex));
            nodeIndex += replicaFactor;
        }

        return result;
    }
}
