package ok.dht.test.galeev;

import one.nio.util.Hash;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConsistentHashRouter {
    private static final int AMOUNT_OF_V_NODES = 7;
    private final SortedMap<Integer, VNode> ring = new TreeMap<>();

    public void addPhysicalNode(Node physicalName) {
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
        for (VNode vNode : ring.values()) {
            if (vNode.isVirtualNodeOf(physicalNodeName)) {
                amount++;
            }
        }
        return amount;
    }

    public Node getNode(String key) {
        if (key == null) {
            return null;
        }
        if (ring.isEmpty()) {
            return null;
        }
        int hash = Hash.murmur3(key);
        SortedMap<Integer,VNode> tailMap = ring.tailMap(hash);
        int nodeHashVal = !tailMap.isEmpty() ? tailMap.firstKey() : ring.firstKey();
        return ring.get(nodeHashVal).getPhysicalNode();
    }

    public static class VNode {
        final Node physicalNode;
        final String vNodeName;

        public VNode(Node physicalNode, int vNodeIndex) {
            this.physicalNode = physicalNode;
            this.vNodeName = physicalNode.nodeAddress + "_" + vNodeIndex;
        }

        public String getKey() {
            return vNodeName;
        }

        public boolean isVirtualNodeOf(Node physicalNode) {
            return this.physicalNode.nodeAddress.equals(physicalNode.nodeAddress);
        }

        public Node getPhysicalNode() {
            return physicalNode;
        }
    }

    public static class Node {
        public final AtomicInteger errorCount;
        public final String nodeAddress;
        public volatile boolean isAlive;

        public Node(String nodeAddress) {
            this.isAlive = true;
            this.nodeAddress = nodeAddress;
            this.errorCount = new AtomicInteger(0);
        }
    }
}
