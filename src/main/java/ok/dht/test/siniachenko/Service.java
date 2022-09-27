package ok.dht.test.siniachenko;

import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import one.nio.http.*;
import one.nio.server.AcceptorConfig;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class Service implements ok.dht.Service {

    public static final Response NOT_FOUND_RESPONSE = new Response(
            Response.NOT_FOUND,
            Response.EMPTY
    );
    public static final Response CREATED_RESPONSE = new Response(
            Response.CREATED,
            Response.EMPTY
    );
    public static final Response ACCEPTED_RESPONSE = new Response(
            Response.ACCEPTED,
            Response.EMPTY
    );
    public static final Response BAD_REQUEST_RESPONSE = new Response(
            Response.BAD_REQUEST,
            Response.EMPTY
    );
    private final ServiceConfig config;
    private HttpServer server;

    public Service(ServiceConfig config) {
        this.config = config;
    }

    @java.lang.Override
    public CompletableFuture<?> start() throws IOException {
        server = new HttpServer(createConfigFromPort(config.selfPort()));
        server.start();
        System.out.println("Service started on " + config.selfUrl());
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpServerConfig = new HttpServerConfig();
        AcceptorConfig acceptorConfig = new AcceptorConfig();
        acceptorConfig.port = port;
        acceptorConfig.reusePort = true;
        httpServerConfig.acceptors = new AcceptorConfig[]{acceptorConfig};
        return httpServerConfig;
    }

    @java.lang.Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response getEntity(@Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return BAD_REQUEST_RESPONSE;
        }
        byte[] stored = db.get(id);
        if (stored == null) {
            return NOT_FOUND_RESPONSE;
        } else {
            return new Response(
                    Response.OK,
                    stored
            );
        }
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response upsertEntity(
            @Param(value = "id", required = true) String id,
            Request request
    ) {
        if (id.isEmpty()) {
            return BAD_REQUEST_RESPONSE;
        }
        db.put(id, request.getBody());
        return CREATED_RESPONSE;
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response deleteEntity(
            @Param(value = "id", required = true) String id
    ) {
        if (id.isEmpty()) {
            return BAD_REQUEST_RESPONSE;
        }
        db.remove(id);
        return ACCEPTED_RESPONSE;
    }

    @RequestMethod(Request.METHOD_GET)
    public Response defaultHandleRequest() {
        return BAD_REQUEST_RESPONSE;
    }

    @ServiceFactory(stage = 1, week = 1)
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public ok.dht.Service create(ServiceConfig config) {
            return new Service(config);
        }
    }
}
