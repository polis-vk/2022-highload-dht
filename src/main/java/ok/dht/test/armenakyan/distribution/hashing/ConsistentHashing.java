package ok.dht.test.armenakyan.distribution.hashing;

import ok.dht.test.armenakyan.distribution.model.Node;
import ok.dht.test.armenakyan.distribution.model.VNode;
import one.nio.util.Utf8;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        int count = nodeCount;
        if (count > virtualNodesCircle.length / VIRTUAL_NODE_COUNT) {
            count = virtualNodesCircle.length / VIRTUAL_NODE_COUNT;
        }

        Set<Node> nodes = new HashSet<>();
        int currentIndex = indexByHash(keyHasher.hash(key));

        while (nodes.size() != count) {
            nodes.add(virtualNodesCircle[currentIndex].node());
            currentIndex = (currentIndex + 1) % virtualNodesCircle.length;
        }

        return nodes;
    }

    @Override
    public Node nodeByKey(String key) {
        return virtualNodeByHash(keyHasher.hash(key)).node();
    }

    private VNode virtualNodeByHash(int hash) {
        return virtualNodesCircle[indexByHash(hash)];
    }

    private int indexByHash(int hash) {
        int ind = Arrays.binarySearch(
                virtualNodesCircle,
                new VNode(null, hash),
                COMPARATOR
        );

        if (ind >= 0) {
            return ind;
        }

        return (-ind - 1) % virtualNodesCircle.length;
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
