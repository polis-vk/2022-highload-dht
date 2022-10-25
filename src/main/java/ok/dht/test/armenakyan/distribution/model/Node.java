package ok.dht.test.armenakyan.distribution.model;

import ok.dht.test.armenakyan.distribution.NodeRequestHandler;

public class Node {
    private final String url;
    private final NodeRequestHandler requestHandler;

    public Node(String url, NodeRequestHandler requestHandler) {
        this.url = url;
        this.requestHandler = requestHandler;
    }

    public String url() {
        return url;
    }

    public NodeRequestHandler requestHandler() {
        return requestHandler;
    }

    @Override
    public int hashCode() {
        return url.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        return url.equals(((Node) o).url);
    }
}
