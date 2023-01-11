package ok.dht.test.shik.events;

import one.nio.http.HttpSession;
import one.nio.http.Request;

public abstract class AbstractRequestState implements RequestState {

    private final Request request;
    private final HttpSession session;
    private final String id;
    private final long timestamp;

    public AbstractRequestState(CommonRequestStateParams commonParams) {
        this.request = commonParams.getRequest();
        this.session = commonParams.getSession();
        this.id = commonParams.getId();
        this.timestamp = commonParams.getTimestamp();
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
