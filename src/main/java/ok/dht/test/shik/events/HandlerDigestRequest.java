package ok.dht.test.shik.events;

public class HandlerDigestRequest extends HandlerRequest {

    private final String leaderUrl;

    public HandlerDigestRequest(RequestState state, String leaderUrl) {
        super(state);
        this.leaderUrl = leaderUrl;
    }

    public String getLeaderUrl() {
        return leaderUrl;
    }
}
