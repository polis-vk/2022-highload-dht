package ok.dht.test.ushkov;

import ok.dht.test.ushkov.exception.InternalErrorException;
import ok.dht.test.ushkov.exception.InvalidParamsException;
import one.nio.http.Request;
import one.nio.http.Response;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

final class Util {
    private Util() {
    }

    static HttpRequest createProxyRequest(String url, Request request, long timestamp)
            throws InternalErrorException {
        return switch (request.getMethod()) {
            case Request.METHOD_GET -> HttpRequest.newBuilder()
                    .method("GET", HttpRequest.BodyPublishers.noBody())
                    .uri(URI.create(url + request.getPath() + "?id=" + request.getParameter("id=")))
                    .header("Proxy", Long.toString(timestamp))
                    .build();
            case Request.METHOD_PUT -> HttpRequest.newBuilder()
                    .method("PUT", HttpRequest.BodyPublishers.ofByteArray(request.getBody()))
                    .uri(URI.create(url + request.getPath() + "?id=" + request.getParameter("id=")))
                    .header("Proxy", Long.toString(timestamp))
                    .build();
            case Request.METHOD_DELETE -> HttpRequest.newBuilder()
                    .method("DELETE", HttpRequest.BodyPublishers.noBody())
                    .uri(URI.create(url + request.getPath() + "?id=" + request.getParameter("id=")))
                    .header("Proxy", Long.toString(timestamp))
                    .build();
            default -> throw new InternalErrorException();
        };
    }

    static Response toOneNioResponse(HttpResponse<byte[]> javaNetResponse) throws InternalErrorException {
        String status;
        status = switch (javaNetResponse.statusCode()) {
            case HttpURLConnection.HTTP_OK -> Response.OK;
            case HttpURLConnection.HTTP_CREATED -> Response.CREATED;
            case HttpURLConnection.HTTP_ACCEPTED -> Response.ACCEPTED;
            case HttpURLConnection.HTTP_NO_CONTENT -> Response.NO_CONTENT;
            case HttpURLConnection.HTTP_SEE_OTHER -> Response.SEE_OTHER;
            case HttpURLConnection.HTTP_NOT_MODIFIED -> Response.NOT_MODIFIED;
            case HttpURLConnection.HTTP_USE_PROXY -> Response.USE_PROXY;
            case HttpURLConnection.HTTP_BAD_REQUEST -> Response.BAD_REQUEST;
            case HttpURLConnection.HTTP_UNAUTHORIZED -> Response.UNAUTHORIZED;
            case HttpURLConnection.HTTP_PAYMENT_REQUIRED -> Response.PAYMENT_REQUIRED;
            case HttpURLConnection.HTTP_FORBIDDEN -> Response.FORBIDDEN;
            case HttpURLConnection.HTTP_NOT_FOUND -> Response.NOT_FOUND;
            case HttpURLConnection.HTTP_NOT_ACCEPTABLE -> Response.NOT_ACCEPTABLE;
            case HttpURLConnection.HTTP_CONFLICT -> Response.CONFLICT;
            case HttpURLConnection.HTTP_GONE -> Response.GONE;
            case HttpURLConnection.HTTP_LENGTH_REQUIRED -> Response.LENGTH_REQUIRED;
            case HttpURLConnection.HTTP_INTERNAL_ERROR -> Response.INTERNAL_ERROR;
            case HttpURLConnection.HTTP_NOT_IMPLEMENTED -> Response.NOT_IMPLEMENTED;
            case HttpURLConnection.HTTP_BAD_GATEWAY -> Response.BAD_GATEWAY;
            case HttpURLConnection.HTTP_GATEWAY_TIMEOUT -> Response.GATEWAY_TIMEOUT;
            default -> throw new InternalErrorException();
        };
        Response oneNioResponse = new Response(status, javaNetResponse.body());
        oneNioResponse.addHeader("Timestamp: " + javaNetResponse.headers().map().get("Timestamp").get(0));
        return oneNioResponse;
    }

    static int parseInt(String param) throws InvalidParamsException {
        try {
            return Integer.parseInt(param);
        } catch (NumberFormatException e) {
            throw new InvalidParamsException();
        }
    }

    @FunctionalInterface
    interface RequestExecution {
        Response execute(String id, byte[] body, long timestamp) throws InternalErrorException;
    }
}
