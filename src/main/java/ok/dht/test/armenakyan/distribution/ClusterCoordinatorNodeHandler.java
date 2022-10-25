package ok.dht.test.armenakyan.distribution;

import ok.dht.test.armenakyan.distribution.hashing.ConsistentHashing;
import ok.dht.test.armenakyan.distribution.hashing.KeyHasher;
import ok.dht.test.armenakyan.distribution.model.Node;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClusterCoordinatorNodeHandler implements NodeRequestHandler {
    private final ConsistentHashing consistentHashing;
    private final List<Node> nodes;

    public ClusterCoordinatorNodeHandler(String selfUrl,
                                         NodeRequestHandler selfHandler,
                                         List<String> nodeUrls,
                                         KeyHasher keyHasher) {
        List<Node> nodeList = new ArrayList<>();
        nodeList.add(new Node(selfUrl, selfHandler));

        for (String nodeUrl : nodeUrls) {
            if (nodeUrl.equals(selfUrl)) {
                continue;
            }
            nodeList.add(new Node(nodeUrl, new ProxyNodeHandler(nodeUrl)));
        }

        this.nodes = nodeList;
        this.consistentHashing = new ConsistentHashing(nodeList, keyHasher);
    }

    @Override
    public Response handleForKey(String key, Request request) throws IOException {
        return consistentHashing
                .nodeByKey(key)
                .requestHandler()
                .handleForKey(key, request);
    }

    @Override
    public void handleForKey(String key, Request request, HttpSession session) throws IOException {
        session.sendResponse(handleForKey(key, request));
    }

    @Override
    public void close() throws IOException {
        for (Node node : nodes) {
            node.requestHandler().close();
        }
    }
}
