package ok.dht.test.shik.events;

import one.nio.http.Response;

public class HandlerResponse {

    private Response response;

    public HandlerResponse() {}

    public HandlerResponse(Response response) {
        this.response = response;
    }

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }
}
