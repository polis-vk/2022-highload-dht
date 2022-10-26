package ok.dht.test.shik.events;

public class HandlerTimedRequest extends HandlerRequest {

    private long timestamp;

    public HandlerTimedRequest(RequestState state, long timestamp) {
        super(state);
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
