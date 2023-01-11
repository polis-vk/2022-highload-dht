package ok.dht.test.shik.events;

public class HandlerDigestRequest extends HandlerRequest {

    private final String mainNodeUrl;

    public HandlerDigestRequest(RequestState state, String mainNodeUrl) {
        super(state);
        this.mainNodeUrl = mainNodeUrl;
    }

    public String getLeaderUrl() {
        return mainNodeUrl;
    }
}
