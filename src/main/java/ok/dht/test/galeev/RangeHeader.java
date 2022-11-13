package ok.dht.test.galeev;

import one.nio.http.Request;

public class RangeHeader {
    public static final String START_PARAMETER = "start=";
    public static final String END_PARAMETER = "end=";

    private final String startParameter;
    private final String endParameter;
    private final boolean isOk;

    public RangeHeader(Request request) {
        startParameter = request.getParameter(START_PARAMETER);
        endParameter = request.getParameter(END_PARAMETER);
        isOk = true;
    }

    public String getStartParameter() {
        return startParameter;
    }

    public String getEndParameter() {
        return endParameter;
    }

    public boolean isOk() {
        return isOk;
    }
}
