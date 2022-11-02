package ok.dht.test.monakhov.utils;

import ok.dht.ServiceConfig;
import ok.dht.test.monakhov.AsyncHttpServerConfig;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;

import static ok.dht.test.monakhov.DaoService.QUEUE_SIZE;

public final class ServiceUtils {

    private ServiceUtils() {
    }

    public static Response responseNotFound() {
        return new Response(Response.NOT_FOUND, Response.EMPTY);
    }

    public static Response responseAccepted() {
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    public static Response responseCreated() {
        return new Response(Response.CREATED, Response.EMPTY);
    }

    public static Response responseBadRequest() {
        return new Response(Response.BAD_REQUEST, Response.EMPTY);
    }

    public static Response responseMethodNotAllowed() {
        return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
    }

    public static Response responseInternalError() {
        return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
    }

    public static Response responseNotEnoughReplicas() {
        return new Response("504 Not Enough Replicas", Response.EMPTY);
    }

    public static Response responseOk(byte[] body) {
        return new Response(Response.OK, body);
    }

    public static boolean isInvalidReplica(String ack, String from) {
        return (ack == null && from != null) || (ack != null && from == null);
    }

    public static AsyncHttpServerConfig createConfigFromPort(ServiceConfig serviceConfig) {
        AsyncHttpServerConfig httpConfig = new AsyncHttpServerConfig();
        httpConfig.clusterUrls = serviceConfig.clusterUrls();
        httpConfig.selfUrl = serviceConfig.selfUrl();
        httpConfig.workersNumber = Math.max(1, Runtime.getRuntime().availableProcessors() / 4);
        httpConfig.queueSize = QUEUE_SIZE;
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = serviceConfig.selfPort();
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[] {acceptor};
        return httpConfig;
    }
}
