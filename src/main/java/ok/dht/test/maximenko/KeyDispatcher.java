package ok.dht.test.maximenko;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class KeyDispatcher {

    private final static int virtualNodesPerMachine = 5;
    TreeMap<Integer, Integer> virtualNodes;
    KeyDispatcher(List<Integer> nodes) {
        int nodeAmount = nodes.size();
        int interval = Integer.MAX_VALUE / (nodeAmount * virtualNodesPerMachine);
        virtualNodes = new TreeMap<>();
        int border = interval;
        for (int i = 0; i < virtualNodesPerMachine; i++) {
            for (Integer node : nodes) {
                virtualNodes.put(border, node);
                border += interval;
            }
        }
    }

    int getNode(String key) {
        int hash = Math.abs(key.hashCode());
        int node = virtualNodes.higherEntry(hash).getValue();
        return node;
    }

    public ArrayDeque<Integer> getReplicas(String key, int replicasAmount) {
        ArrayDeque<Integer> result = new ArrayDeque<>();
        int hash = Math.abs(key.hashCode());
        for (int i = 0; i < replicasAmount; i++) {
            Map.Entry<Integer, Integer> virtualNode = virtualNodes.higherEntry(hash);
            result.add(virtualNode.getValue());
            hash = virtualNode.getKey() + 1;
        }
        return result;
    }
}
