package ok.dht.test.kovalenko;

import one.nio.http.HttpSession;
import one.nio.http.Request;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;

public class Node {

    private static final Client client = new Client();
    private final String selfUrl;
    private boolean isIll = false;

    public Node(String selfUrl) {
        this.selfUrl = selfUrl;
    }

    public HttpResponse<byte[]> proxyRequest(String id, Request request, MyHttpSession session)
            throws ExecutionException, InterruptedException, IllegalAccessException, IOException {
        if (isIll()) {
            throw new IllegalAccessException("Node is ill!");
        }

        client.setUrl(selfUrl);

        return switch (request.getMethod()) {
            case Request.METHOD_GET -> client.get(id, request.getBody(), session);
            case Request.METHOD_PUT -> client.put(id, request.getBody(), session);
            case Request.METHOD_DELETE -> client.delete(id, request.getBody(), session);
            default -> throw new IllegalArgumentException("Unexpected request method to be proxied: "
                    + request.getMethod());
        };
    }

    public boolean isIll() {
        return isIll;
    }

    public void setIll(boolean isIll) {
        this.isIll = isIll;
    }

    public String selfUrl() {
        return this.selfUrl;
    }
}
