package ok.dht.test.dergunov;

import one.nio.http.Response;
import java.net.HttpURLConnection;
public class ConverterStatusCode {

    public static String fromHttpResponseStatusJavaToOneNoi(int statusCode) {
        return switch (statusCode) {
            case HttpURLConnection.HTTP_OK -> Response.OK;
            case HttpURLConnection.HTTP_CREATED -> Response.CREATED;
            case HttpURLConnection.HTTP_ACCEPTED -> Response.ACCEPTED;
            case HttpURLConnection.HTTP_NOT_AUTHORITATIVE -> Response.NON_AUTHORITATIVE_INFORMATION;
            case HttpURLConnection.HTTP_NO_CONTENT -> Response.NO_CONTENT;
            case HttpURLConnection.HTTP_RESET -> Response.RESET_CONTENT;
            case HttpURLConnection.HTTP_PARTIAL -> Response.PARTIAL_CONTENT;
            case HttpURLConnection.HTTP_MULT_CHOICE -> Response.MULTIPLE_CHOICES;
            case HttpURLConnection.HTTP_MOVED_PERM -> Response.MOVED_PERMANENTLY;
            case HttpURLConnection.HTTP_MOVED_TEMP -> Response.FOUND;
            case HttpURLConnection.HTTP_SEE_OTHER -> Response.SEE_OTHER;
            case HttpURLConnection.HTTP_NOT_MODIFIED -> Response.NOT_MODIFIED;
            case HttpURLConnection.HTTP_USE_PROXY -> Response.USE_PROXY;
            case HttpURLConnection.HTTP_BAD_REQUEST -> Response.BAD_REQUEST;
            case HttpURLConnection.HTTP_UNAUTHORIZED -> Response.UNAUTHORIZED;
            case HttpURLConnection.HTTP_PAYMENT_REQUIRED -> Response.PAYMENT_REQUIRED;
            case HttpURLConnection.HTTP_FORBIDDEN -> Response.FORBIDDEN;
            case HttpURLConnection.HTTP_NOT_FOUND -> Response.NOT_FOUND;
            case HttpURLConnection.HTTP_BAD_METHOD -> Response.METHOD_NOT_ALLOWED;
            case HttpURLConnection.HTTP_NOT_ACCEPTABLE -> Response.NOT_ACCEPTABLE;
            case HttpURLConnection.HTTP_PROXY_AUTH -> Response.PROXY_AUTHENTICATION_REQUIRED;
            case HttpURLConnection.HTTP_CLIENT_TIMEOUT -> Response.REQUEST_TIMEOUT;
            case HttpURLConnection.HTTP_CONFLICT -> Response.CONFLICT;
            case HttpURLConnection.HTTP_GONE -> Response.GONE;
            case HttpURLConnection.HTTP_LENGTH_REQUIRED -> Response.LENGTH_REQUIRED;
            case HttpURLConnection.HTTP_PRECON_FAILED -> Response.PRECONDITION_FAILED;
            case HttpURLConnection.HTTP_ENTITY_TOO_LARGE -> Response.REQUEST_ENTITY_TOO_LARGE;
            case HttpURLConnection.HTTP_REQ_TOO_LONG -> Response.REQUEST_URI_TOO_LONG;
            case HttpURLConnection.HTTP_UNSUPPORTED_TYPE -> Response.UNSUPPORTED_MEDIA_TYPE;
            case HttpURLConnection.HTTP_NOT_IMPLEMENTED -> Response.NOT_IMPLEMENTED;
            case HttpURLConnection.HTTP_BAD_GATEWAY -> Response.BAD_GATEWAY;
            case HttpURLConnection.HTTP_UNAVAILABLE -> Response.SERVICE_UNAVAILABLE;
            case HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> Response.GATEWAY_TIMEOUT;
            case HttpURLConnection.HTTP_VERSION -> Response.HTTP_VERSION_NOT_SUPPORTED;
            default -> Response.INTERNAL_ERROR;
        };
    }
}


