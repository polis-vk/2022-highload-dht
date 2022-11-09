package ok.dht.test.shik.events;

public class HandlerRequest {

    private final RequestState state;

    public HandlerRequest(RequestState state) {
        this.state = state;
    }

    public RequestState getState() {
        return state;
    }
}
