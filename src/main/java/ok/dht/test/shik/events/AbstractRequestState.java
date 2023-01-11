package ok.dht.test.shik.events;

import one.nio.http.HttpSession;
import one.nio.http.Request;

public abstract class AbstractRequestState implements RequestState {

    private final Request request;
    private final HttpSession session;
    private final String id;

    public AbstractRequestState(Request request, HttpSession session, String id) {
        this.request = request;
        this.session = session;
        this.id = id;
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

}
