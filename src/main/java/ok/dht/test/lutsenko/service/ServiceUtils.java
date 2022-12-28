package ok.dht.test.lutsenko.service;

import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.util.Map;

public final class ServiceUtils {

    private static final Map<Integer, String> HTTP_CODES_MAP = Map.ofEntries(
            Map.entry(HttpURLConnection.HTTP_OK, Response.OK),
            Map.entry(HttpURLConnection.HTTP_CREATED, Response.CREATED),
            Map.entry(HttpURLConnection.HTTP_ACCEPTED, Response.ACCEPTED),
            Map.entry(HttpURLConnection.HTTP_NOT_AUTHORITATIVE, Response.NON_AUTHORITATIVE_INFORMATION),
            Map.entry(HttpURLConnection.HTTP_NO_CONTENT, Response.NO_CONTENT),
            Map.entry(HttpURLConnection.HTTP_RESET, Response.RESET_CONTENT),
            Map.entry(HttpURLConnection.HTTP_PARTIAL, Response.PARTIAL_CONTENT),
            Map.entry(HttpURLConnection.HTTP_MULT_CHOICE, Response.MULTIPLE_CHOICES),
            Map.entry(HttpURLConnection.HTTP_MOVED_PERM, Response.MOVED_PERMANENTLY),
            Map.entry(HttpURLConnection.HTTP_MOVED_TEMP, Response.TEMPORARY_REDIRECT),
            Map.entry(HttpURLConnection.HTTP_SEE_OTHER, Response.SEE_OTHER),
            Map.entry(HttpURLConnection.HTTP_NOT_MODIFIED, Response.NOT_MODIFIED),
            Map.entry(HttpURLConnection.HTTP_USE_PROXY, Response.USE_PROXY),
            Map.entry(HttpURLConnection.HTTP_BAD_REQUEST, Response.BAD_REQUEST),
            Map.entry(HttpURLConnection.HTTP_UNAUTHORIZED, Response.UNAUTHORIZED),
            Map.entry(HttpURLConnection.HTTP_PAYMENT_REQUIRED, Response.PAYMENT_REQUIRED),
            Map.entry(HttpURLConnection.HTTP_FORBIDDEN, Response.FORBIDDEN),
            Map.entry(HttpURLConnection.HTTP_NOT_FOUND, Response.NOT_FOUND),
            Map.entry(HttpURLConnection.HTTP_BAD_METHOD, Response.METHOD_NOT_ALLOWED),
            Map.entry(HttpURLConnection.HTTP_NOT_ACCEPTABLE, Response.NOT_ACCEPTABLE),
            Map.entry(HttpURLConnection.HTTP_PROXY_AUTH, Response.PROXY_AUTHENTICATION_REQUIRED),
            Map.entry(HttpURLConnection.HTTP_CLIENT_TIMEOUT, Response.REQUEST_TIMEOUT),
            Map.entry(HttpURLConnection.HTTP_CONFLICT, Response.CONFLICT),
            Map.entry(HttpURLConnection.HTTP_GONE, Response.GONE),
            Map.entry(HttpURLConnection.HTTP_LENGTH_REQUIRED, Response.LENGTH_REQUIRED),
            Map.entry(HttpURLConnection.HTTP_PRECON_FAILED, Response.PRECONDITION_FAILED),
            Map.entry(HttpURLConnection.HTTP_ENTITY_TOO_LARGE, Response.REQUEST_ENTITY_TOO_LARGE),
            Map.entry(HttpURLConnection.HTTP_REQ_TOO_LONG, Response.REQUEST_URI_TOO_LONG),
            Map.entry(HttpURLConnection.HTTP_UNSUPPORTED_TYPE, Response.UNSUPPORTED_MEDIA_TYPE),
            Map.entry(HttpURLConnection.HTTP_INTERNAL_ERROR, Response.INTERNAL_ERROR),
            Map.entry(HttpURLConnection.HTTP_NOT_IMPLEMENTED, Response.NOT_IMPLEMENTED),
            Map.entry(HttpURLConnection.HTTP_BAD_GATEWAY, Response.BAD_GATEWAY),
            Map.entry(HttpURLConnection.HTTP_UNAVAILABLE, Response.SERVICE_UNAVAILABLE),
            Map.entry(HttpURLConnection.HTTP_GATEWAY_TIMEOUT, Response.GATEWAY_TIMEOUT),
            Map.entry(HttpURLConnection.HTTP_VERSION, Response.HTTP_VERSION_NOT_SUPPORTED)
    );
    private static final Logger LOG = LoggerFactory.getLogger(ServiceUtils.class);

    private ServiceUtils() {
    }

    public static void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (Exception e) {
            LOG.error("Service unavailable", e);
            try {
                session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
            } catch (Exception e1) {
                LOG.error("Failed send SERVICE_UNAVAILABLE response", e1);
                closeSession(session);
            }
        }
    }

    public static void sendResponse(HttpSession session, String resultCode) {
        sendResponse(session, new Response(resultCode, Response.EMPTY));
    }

    public static void sendResponse(HttpSession session, HttpResponse<byte[]> httpResponse) {
        sendResponse(session, new Response(
                HTTP_CODES_MAP.get(httpResponse.statusCode()),
                httpResponse.body() == null
                        ? Response.EMPTY
                        : httpResponse.body()
        ));
    }

    public static void closeSession(HttpSession session) {
        try {
            session.close();
        } catch (Exception e) {
            LOG.error("Failed close session", e);
        }
    }

    public static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }
}
