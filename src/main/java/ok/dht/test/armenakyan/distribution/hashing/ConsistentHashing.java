package ok.dht.test.armenakyan.distribution.hashing;

import ok.dht.test.armenakyan.distribution.model.Node;
import ok.dht.test.armenakyan.distribution.model.VNode;
import one.nio.util.Utf8;

import java.nio.ByteBuffer;
import java.util.*;

public class ConsistentHashing implements Hashing {
    private static final int VIRTUAL_NODE_COUNT = 5;
    private static final Comparator<VNode> COMPARATOR = Comparator.comparing(VNode::hash);

    private final KeyHasher keyHasher;
    private final VNode[] virtualNodesCircle;

    public ConsistentHashing(List<Node> nodes, KeyHasher keyHasher) {
        this.keyHasher = keyHasher;

        virtualNodesCircle = new VNode[nodes.size() * VIRTUAL_NODE_COUNT];

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);

            VNode[] virtualNodes = generateVirtualNodes(node);

            System.arraycopy(
                    virtualNodes,
                    0,
                    virtualNodesCircle,
                    i * VIRTUAL_NODE_COUNT,
                    VIRTUAL_NODE_COUNT
            );
        }

        Arrays.sort(virtualNodesCircle, COMPARATOR);
    }

    @Override
    public Set<Node> nodesByKey(String key, int nodeCount) {
        if (nodeCount <= 0) {
            return Collections.emptySet();
        }
        if (nodeCount > virtualNodesCircle.length / VIRTUAL_NODE_COUNT) {
            nodeCount = virtualNodesCircle.length / VIRTUAL_NODE_COUNT;
        }

        Set<Node> nodes = new HashSet<>();

        VNode currentVNode = virtualNodeByHash(keyHasher.hash(key));
        nodes.add(currentVNode.node());
        int nextHash = currentVNode.hash() + 1;

        while (nodes.size() != nodeCount) {
            currentVNode = virtualNodeByHash(nextHash);
            nodes.add(currentVNode.node());
            nextHash = currentVNode.hash() + 1;
        }

        return nodes;
    }

    @Override
    public Node nodeByKey(String key) {
        return virtualNodeByHash(keyHasher.hash(key)).node();
    }

    private VNode virtualNodeByHash(int hash) {
        int ind = Arrays.binarySearch(
                virtualNodesCircle,
                new VNode(null, hash),
                COMPARATOR
        );

        if (ind >= 0) {
            return virtualNodesCircle[ind];
        }

        int insertionPoint = -ind - 1;
        return virtualNodesCircle[insertionPoint % virtualNodesCircle.length];
    }

    private VNode[] generateVirtualNodes(Node node) {
        VNode[] virtualNodes = new VNode[VIRTUAL_NODE_COUNT];

        ByteBuffer nodeKeyBytes = ByteBuffer.wrap(Utf8.toBytes(node.url()));

        for (byte tail = 0; tail < VIRTUAL_NODE_COUNT; tail++) {
            nodeKeyBytes.put(tail);
            int hash = keyHasher.hash(nodeKeyBytes.array());

            virtualNodes[tail] = new VNode(node, hash);
        }

        return virtualNodes;
    }
}
