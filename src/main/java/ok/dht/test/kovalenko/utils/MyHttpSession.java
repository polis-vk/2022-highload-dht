package ok.dht.test.kovalenko.utils;

import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.net.Socket;

public class MyHttpSession extends HttpSession {

    private String requestId;
    private ReplicasUtils.Replicas replicas;

    public MyHttpSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public ReplicasUtils.Replicas getReplicas() {
        return replicas;
    }

    public void setReplicas(ReplicasUtils.Replicas replicas) {
        this.replicas = replicas;
    }
}
