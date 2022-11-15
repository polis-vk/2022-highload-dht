package ok.dht.test.kovalenko.utils;

import ok.dht.test.pashchenko.MyServer;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.net.Socket;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MyHttpSession extends HttpSession {

    private String requestId;
    private HttpUtils.Replicas replicas;
    private HttpUtils.Range range;

    public MyHttpSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    @Override
    protected void writeResponse(Response response, boolean includeBody) throws IOException {
        if (response instanceof MyHttpResponse.ChunkedResponse) {
            super.write(new QueueItem() {
                int count = 0;

                @Override
                public int remaining() {
                    return 1;
                }

                @Override
                public int write(Socket socket) throws IOException {
                    byte[] bytes = (count++ + "\n").getBytes(StandardCharsets.UTF_8);
                    return socket.write(bytes, 0, bytes.length);
                }
            });
        } else {
            super.writeResponse(response, includeBody);
        }
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public HttpUtils.Replicas getReplicas() {
        return replicas;
    }

    public void setReplicas(HttpUtils.Replicas replicas) {
        this.replicas = replicas;
    }

    public HttpUtils.Range getRange() {
        return range;
    }

    public void setRange(HttpUtils.Range range) {
        this.range = range;
    }
}
