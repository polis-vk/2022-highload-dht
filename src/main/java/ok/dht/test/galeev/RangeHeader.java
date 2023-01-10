package ok.dht.test.galeev;

import one.nio.http.Request;

public class RangeHeader {
    public static final String START_PARAMETER = "start=";
    public static final String END_PARAMETER = "end=";

    private final String startParameter;
    private final String endParameter;
    private final boolean isOk;

    public RangeHeader(Request request) {
        boolean tmpIsOk = true;
        String tmpStartParameter = request.getParameter(START_PARAMETER);
        String tmpEndParameter = request.getParameter(END_PARAMETER);
        if (tmpStartParameter == null
                || tmpStartParameter.isEmpty()) {
            tmpIsOk = false;
        }
        startParameter = tmpStartParameter;
        endParameter = tmpEndParameter;
        isOk = tmpIsOk;
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
