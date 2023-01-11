package ok.dht.test.shik.events;

public class HandlerRangeRequest extends HandlerRequest {

    private final String start;
    private final String end;

    public HandlerRangeRequest(RequestState state, String start, String end) {
        super(state);
        this.start = start;
        this.end = end;
    }

    public String getStart() {
        return start;
    }

    public String getEnd() {
        return end;
    }
}
