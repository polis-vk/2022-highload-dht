package ok.dht.test.shik.events;

public class HandlerPutRequest extends HandlerRequest {

    private long timestamp;

    public HandlerPutRequest(RequestState state, long timestamp) {
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
