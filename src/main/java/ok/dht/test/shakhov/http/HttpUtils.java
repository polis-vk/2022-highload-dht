package ok.dht.test.shakhov.http;

import one.nio.http.HttpServerConfig;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;

public final class HttpUtils {
    public static final String X_LEADER_TIMESTAMP_HEADER = "X-Leader-Timestamp";
    public static final String ONE_NIO_X_LEADER_TIMESTAMP_HEADER = X_LEADER_TIMESTAMP_HEADER + ':';
    public static final String X_RECORD_TIMESTAMP_HEADER = "X-Record-Timestamp";
    public static final String ONE_NIO_X_RECORD_TIMESTAMP_HEADER = X_RECORD_TIMESTAMP_HEADER + ':';
    public static final String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";

    public static final String ID_PARAMETER = "id=";
    public static final String ACK_PARAM = "ack=";
    public static final String FROM_PARAM = "from=";
    public static final String START_PARAMETER = "start=";
    public static final String END_PARAMETER = "end=";

    private HttpUtils() {
    }

    public static int getIntParameter(Request request, String parameterKey, int defaultValue) {
        String parameter = request.getParameter(parameterKey);
        if (parameter == null || parameter.isEmpty()) {
            return defaultValue;
        } else {
            return Integer.parseInt(parameter);
        }
    }

    public static HttpServerConfig createHttpServerConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[] { acceptor };
        return httpConfig;
    }

    public static Response internalError() {
        return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
    }

    public static Response methodNotAllowed() {
        return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
    }

    public static Response badRequest() {
        return new Response(Response.BAD_REQUEST, Response.EMPTY);
    }
}
