package ok.dht.test.shik.events;

import one.nio.http.HttpSession;
import one.nio.http.Request;

public class HandlerRequest {

    private Request request;
    private HttpSession session;
    private String id;

    public HandlerRequest(Request request, HttpSession session, String id) {
        this.request = request;
        this.session = session;
        this.id = id;
    }

    public Request getRequest() {
        return request;
    }

    public String getId() {
        return id;
    }

    public HttpSession getSession() {
        return session;
    }

    public void setRequest(Request request) {
        this.request = request;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setSession(HttpSession session) {
        this.session = session;
    }
}
