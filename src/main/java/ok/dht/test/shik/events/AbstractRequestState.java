package ok.dht.test.shik.events;

import one.nio.http.HttpSession;
import one.nio.http.Request;

public abstract class AbstractRequestState implements RequestState {

    private final Request request;
    private final HttpSession session;
    private final String id;
    private final long timestamp;

    public AbstractRequestState(Request request, HttpSession session, String id, long timestamp) {
        this.request = request;
        this.session = session;
        this.id = id;
        this.timestamp = timestamp;
    }

    @Override
    public Request getRequest() {
        return request;
    }

    @Override
    public HttpSession getSession() {
        return session;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

}
