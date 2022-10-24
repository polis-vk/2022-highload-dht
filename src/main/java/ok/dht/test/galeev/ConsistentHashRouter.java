package ok.dht.test.galeev;

import one.nio.util.Hash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConsistentHashRouter {
    private static final int AMOUNT_OF_V_NODES = 7;
    private final SortedMap<Integer, VNode> ring = new TreeMap<>();
    private final AtomicInteger amountOfPhysicalNodes = new AtomicInteger(0);

    public void addPhysicalNode(Node physicalName) {
        amountOfPhysicalNodes.incrementAndGet();
        int amount = getAmountOfVNodes(physicalName);
        int collisionCounter = 0;
        for (int i = 1; i <= AMOUNT_OF_V_NODES; i++) {
            VNode newVNode = new VNode(physicalName, amount + i);
            int hashOfVNode = Hash.murmur3(newVNode.getKey());
            while (ring.containsKey(hashOfVNode)) {
                hashOfVNode = Hash.murmur3(newVNode.getKey() + ++collisionCounter);
            }
            ring.put(hashOfVNode, newVNode);
        }
    }

    public int getAmountOfVNodes(Node physicalNodeName) {
        int amount = 0;
        for (VNode vnode : ring.values()) {
            if (vnode.isVirtualNodeOf(physicalNodeName)) {
                amount++;
            }
        }
        return amount;
    }

    public int getAmountOfPhysicalNodes() {
        return amountOfPhysicalNodes.get();
    }

    public List<Node> getNode(String key, int from) {
        if (key == null) {
            return Collections.emptyList();
        }
        if (ring.isEmpty()) {
            return Collections.emptyList();
        }
        int hash = Hash.murmur3(key);

        SortedMap<Integer,VNode> tailMap = ring.tailMap(hash);
        int firstNodeHashVal = tailMap.isEmpty() ? ring.firstKey() : tailMap.firstKey();

        List<Node> nodeList = new ArrayList<>(from);
        for (VNode vnode : tailMap.values()) {
            // Add only unique Nodes (we have VNodes)
            if (!nodeList.contains(vnode.getPhysicalNode())) {
                nodeList.add(vnode.getPhysicalNode());
            }
            if (nodeList.size() == from) {
                return nodeList;
            }
        }
        // If in tailMap contains not enough of Nodes -> we start from beginning
        for (Map.Entry<Integer, VNode> vnodeEntry : ring.entrySet()) {
            if (!nodeList.contains(vnodeEntry.getValue().getPhysicalNode())) {
                nodeList.add(vnodeEntry.getValue().getPhysicalNode());
            }
            if (nodeList.size() == from) {
                return nodeList;
            }
            if (vnodeEntry.getKey() == firstNodeHashVal) {
                // We have checked all Nodes
                break;
            }
        }
        throw new IndexOutOfBoundsException();
    }

    public static class VNode {
        final Node physicalNode;
        final String vnodeName;

        public VNode(Node physicalNode, int vnodeIndex) {
            this.physicalNode = physicalNode;
            this.vnodeName = physicalNode.nodeAddress + "_" + vnodeIndex;
        }

        public String getKey() {
            return vnodeName;
        }

        public boolean isVirtualNodeOf(Node physicalNode) {
            return this.physicalNode.nodeAddress.equals(physicalNode.nodeAddress);
        }

        public Node getPhysicalNode() {
            return physicalNode;
        }
    }
}
