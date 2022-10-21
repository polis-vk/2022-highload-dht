package ok.dht.test.kovalenko;

import ok.dht.test.kovalenko.utils.HttpUtils;
import one.nio.http.Request;
import one.nio.http.Response;

import java.util.concurrent.ExecutionException;

public class Node {

    private static final Client client = new Client();

    private boolean isIll = false;
    private final String selfUrl;

    public Node(String selfUrl) {
        this.selfUrl = selfUrl;
    }

    public Response proxyRequest(String id, Request request)
            throws ExecutionException, InterruptedException, IllegalAccessException {
        if (isIll()) {
            throw new IllegalAccessException("Node is ill!");
        }

        Response response;
        client.setUrl(selfUrl);
        switch (request.getMethod()) {
            case Request.METHOD_GET -> response = HttpUtils.toOneNio(client.get(id));
            case Request.METHOD_PUT -> response = HttpUtils.toOneNio(client.put(id, request.getBody()));
            case Request.METHOD_DELETE -> response = HttpUtils.toOneNio(client.delete(id));
            default -> throw new IllegalArgumentException("Unexpected request method to be proxied: "
                    + request.getMethod());
        }
        return response;
    }

    public boolean isIll() {
        return isIll;
    }

    public void setIll(boolean isIll) {
        this.isIll = isIll;
    }
}
