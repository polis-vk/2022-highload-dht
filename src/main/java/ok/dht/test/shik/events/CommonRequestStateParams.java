package ok.dht.test.shik.events;

import one.nio.http.HttpSession;
import one.nio.http.Request;

public class CommonRequestStateParams {

    private final Request request;
    private final HttpSession session;
    private final String id;
    private final long timestamp;

    public CommonRequestStateParams(Request request, HttpSession session, String id, long timestamp) {
        this.request = request;
        this.session = session;
        this.id = id;
        this.timestamp = timestamp;
    }

    public Request getRequest() {
        return request;
    }

    public HttpSession getSession() {
        return session;
    }

    public String getId() {
        return id;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
