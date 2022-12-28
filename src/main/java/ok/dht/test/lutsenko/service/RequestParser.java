package ok.dht.test.lutsenko.service;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import one.nio.http.Request;
import one.nio.http.Response;

import java.util.List;

public class RequestParser {

    public static final String REQUEST_PATH = "/v0/entity";
    public static final String ID_PARAM_NAME = "id=";
    public static final String ACK_PARAM_NAME = "ack=";
    public static final String FROM_PARAM_NAME = "from=";

    private final Request request;
    private boolean isFailed;
    private String failStatus;
    private List<Integer> successStatuses;
    private String id;
    private int ack;
    private int from;

    public RequestParser(Request request) {
        this.request = request;
    }

    @CanIgnoreReturnValue
    public RequestParser checkPath() {
        if (isFailed) {
            return this;
        }
        if (!request.getPath().equals(REQUEST_PATH)) {
            setFailedWithStatus(Response.BAD_REQUEST);
        }
        return this;
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
        id = request.getParameter(ID_PARAM_NAME);
        if (id == null || id.isBlank()) {
            setFailedWithStatus(Response.BAD_REQUEST);
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
                from = clusterUrlsSize;
                ack = quorum(from);
            } else if (ackString == null || fromString == null) {
                setFailedWithStatus(Response.BAD_REQUEST);
            } else {
                ack = Integer.parseInt(ackString);
                from = Integer.parseInt(fromString);
                if (ack <= 0 || ack > from || from > clusterUrlsSize) {
                    setFailedWithStatus(Response.BAD_REQUEST);
                }
            }
        } catch (NumberFormatException e) {
            setFailedWithStatus(Response.BAD_REQUEST);
        }
        return this;
    }

    public List<Integer> successStatuses() {
        return successStatuses;
    }

    public String id() {
        return id;
    }

    public int ack() {
        return ack;
    }

    public int from() {
        return from;
    }

    public String failStatus() {
        return failStatus;
    }

    public boolean isFailed() {
        return isFailed;
    }

    public Request getRequest() {
        return request;
    }

    private void setFailedWithStatus(String status) {
        isFailed = true;
        failStatus = status;
    }

    private static int quorum(int from) {
        return (from / 2) + 1;
    }

}
