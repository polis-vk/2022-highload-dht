package ok.dht.test.lutsenko.service;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import one.nio.http.Request;
import one.nio.http.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class RequestParser {

    public static final String ID_PARAM_NAME = "id=";
    public static final String ACK_PARAM_NAME = "ack=";
    public static final String FROM_PARAM_NAME = "from=";
    public static final String START_PARAM_NAME = "start=";
    public static final String END_PARAM_NAME = "end=";

    private final Request request;
    private final Map<String, Param> paramsMap = new HashMap<>();
    private boolean isFailed;
    private String failStatus;
    private List<Integer> successStatuses;

    public RequestParser(Request request) {
        this.request = request;
    }

    public static RequestParser parse(Request request) {
        return new RequestParser(request);
    }

    @CanIgnoreReturnValue
    public RequestParser checkSuccessStatusCodes() {
        if (isFailed) {
            return this;
        }
        successStatuses = ServiceUtils.successCodesFor(request.getMethod());
        if (successStatuses == null) {
            setFailedWithStatus(Response.METHOD_NOT_ALLOWED);
        }
        return this;
    }

    @CanIgnoreReturnValue
    public RequestParser checkId() {
        if (isFailed) {
            return this;
        }
        String id = request.getParameter(ID_PARAM_NAME);
        if (id == null || id.isBlank()) {
            setFailedWithStatus(Response.BAD_REQUEST);
        } else {
            paramsMap.put(ID_PARAM_NAME, new Param(id));
        }
        return this;
    }

    @CanIgnoreReturnValue
    public RequestParser checkAckFrom(int clusterUrlsSize) {
        if (isFailed) {
            return this;
        }
        String ackString = request.getParameter(ACK_PARAM_NAME);
        String fromString = request.getParameter(FROM_PARAM_NAME);
        try {
            if (ackString == null && fromString == null) {
                int from = clusterUrlsSize;
                int ack = quorum(from);
                paramsMap.put(ACK_PARAM_NAME, new Param(ack));
                paramsMap.put(FROM_PARAM_NAME, new Param(from));
            } else if (ackString == null || fromString == null) {
                setFailedWithStatus(Response.BAD_REQUEST);
            } else {
                int ack = Integer.parseInt(ackString);
                int from = Integer.parseInt(fromString);
                if (ack <= 0 || ack > from || from > clusterUrlsSize) {
                    setFailedWithStatus(Response.BAD_REQUEST);
                } else {
                    paramsMap.put(ACK_PARAM_NAME, new Param(ack));
                    paramsMap.put(FROM_PARAM_NAME, new Param(from));
                }
            }
        } catch (NumberFormatException e) {
            setFailedWithStatus(Response.BAD_REQUEST);
        }
        return this;
    }

    @CanIgnoreReturnValue
    public RequestParser checkStart() {
        if (isFailed) {
            return this;
        }
        String start = request.getParameter(START_PARAM_NAME);
        if (start == null || start.isBlank()) {
            setFailedWithStatus(Response.BAD_REQUEST);
        } else {
            paramsMap.put(START_PARAM_NAME, new Param(start));
        }
        return this;
    }

    @CanIgnoreReturnValue
    public RequestParser checkEnd() {
        if (isFailed) {
            return this;
        }
        String end = request.getParameter(END_PARAM_NAME);
        if (end != null && end.isBlank()) {
            setFailedWithStatus(Response.BAD_REQUEST);
        } else {
            paramsMap.put(END_PARAM_NAME, new Param(end));
        }
        return this;
    }

    @CanIgnoreReturnValue
    public RequestParser onSuccess(Consumer<RequestParser> consumer) {
        if (isFailed) {
            return this;
        }
        consumer.accept(this);
        return this;
    }

    @CanIgnoreReturnValue
    public RequestParser onFail(Consumer<RequestParser> consumer) {
        if (isFailed) {
            consumer.accept(this);
        }
        return this;
    }

    public List<Integer> successStatuses() {
        return successStatuses;
    }

    public String failStatus() {
        return failStatus;
    }

    public Request getRequest() {
        return request;
    }

    public Param getParam(String name) {
        return paramsMap.get(name);
    }

    private void setFailedWithStatus(String status) {
        isFailed = true;
        failStatus = status;
    }

    private static int quorum(int from) {
        return (from / 2) + 1;
    }

    public static class Param {
        final Object value;

        private Param(Object value) {
            this.value = value;
        }

        public String asString() {
            return (String) value;
        }

        public int asInt() {
            return (int) value;
        }
    }

}
