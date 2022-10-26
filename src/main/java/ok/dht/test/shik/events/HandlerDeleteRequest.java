package ok.dht.test.shik.events;

public class HandlerDeleteRequest extends HandlerRequest {

    private long timestamp;

    public HandlerDeleteRequest(RequestState state, long timestamp) {
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
