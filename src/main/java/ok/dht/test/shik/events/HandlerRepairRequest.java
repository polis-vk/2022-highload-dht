package ok.dht.test.shik.events;

import ok.dht.test.shik.model.DBValue;

public class HandlerRepairRequest extends HandlerRequest {

    private final DBValue actualValue;

    public HandlerRepairRequest(RequestState state, DBValue actualValue) {
        super(state);
        this.actualValue = actualValue;
    }

    public DBValue getActualValue() {
        return actualValue;
    }
}
