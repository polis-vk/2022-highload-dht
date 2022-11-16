package ok.dht.test.kovalenko.utils;

import ok.dht.test.kovalenko.dao.aliases.TypedTimedEntry;
import one.nio.http.HttpServer;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.net.Socket;

import java.io.IOException;
import java.util.Iterator;

public class MyHttpSession extends HttpSession {

    private String requestId;
    private HttpUtils.Replicas replicas;
    private HttpUtils.Range range;
    private Iterator<TypedTimedEntry> mergeIterator;

    public MyHttpSession(Socket socket, HttpServer server) {
        super(socket, server);
    }

    @Override
    protected void writeResponse(Response response, boolean includeBody) throws IOException {
        super.writeResponse(response, includeBody);
        if (response instanceof MyHttpResponse.ChunkedResponse) {
            super.write(new MyQueueItem(mergeIterator));
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

    public Iterator<TypedTimedEntry> getMergeIterator() {
        return mergeIterator;
    }

    public void setMergeIterator(Iterator<TypedTimedEntry> mergeIterator) {
        this.mergeIterator = mergeIterator;
    }
}
