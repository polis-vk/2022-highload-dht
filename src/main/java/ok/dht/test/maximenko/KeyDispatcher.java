package ok.dht.test.maximenko;

import java.util.List;
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
        int hash = key.hashCode();
        return virtualNodes.higherEntry(hash).getValue();
    }
}
