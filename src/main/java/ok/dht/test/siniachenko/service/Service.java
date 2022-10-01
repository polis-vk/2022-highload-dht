package ok.dht.test.siniachenko.service;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.siniachenko.storage.BaseEntry;
import one.nio.http.*;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

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
    private DB levelDBStore;
    private HttpServer server;

    public Service(ServiceConfig config) {
        this.config = config;
    }

    @java.lang.Override
    public CompletableFuture<?> start() throws IOException {
        Options options = new Options();
        levelDBStore = factory.open(config.workingDir().toFile(), options);
        System.out.println("Started DB in directory " + config.workingDir());
        server = new HttpServer(createConfigFromPort(config.selfPort()));
        server.start();
        System.out.println("Service started on " + config.selfUrl());
        server.addRequestHandlers(this);
        for (int i = 0; i < 100_000_000; ++i) {
            levelDBStore.put(stringToBytes("key" + i), stringToBytes("value"));
        }
        System.out.println("added 100_000_000 values to DB");
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
        levelDBStore.close();
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response getEntity(@Param(value = "id", required = true) String id) {
//        System.out.println("GET");
        if (id.isEmpty()) {
            return new Response(
                Response.BAD_REQUEST,
                Response.EMPTY
            );
        }
        byte[] value = levelDBStore.get(stringToBytes(id));
//        byte[] value = Utf8.toBytes("3234123");
        if (value == null) {
            return new Response(
                Response.NOT_FOUND,
                Response.EMPTY
            );
        } else {
            return new Response(
                Response.OK,
                value
            );
        }
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response upsertEntity(
        @Param(value = "id", required = true) String id,
        Request request
    ) {
//        System.out.println("PUT");
//        System.out.println(id);
//        System.out.println(Utf8.toString(request.getBody()));
        if (id.isEmpty()) {
            return new Response(
                Response.BAD_REQUEST,
                Response.EMPTY
            );
        }
        levelDBStore.put(stringToBytes(id), request.getBody());
        return new Response(
            Response.CREATED,
            Response.EMPTY
        );
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response deleteEntity(
        @Param(value = "id", required = true) String id
    ) {
//        System.out.println("DELETE");
        if (id.isEmpty()) {
            return new Response(
                Response.BAD_REQUEST,
                Response.EMPTY
            );
        }
        levelDBStore.delete(stringToBytes(id));
        return new Response(
            Response.ACCEPTED,
            Response.EMPTY
        );
    }

    private static byte[] stringToBytes(String string) {
        return Utf8.toBytes(string);
    }

    @RequestMethod(Request.METHOD_GET)
    public Response defaultHandleRequest() {
        return new Response(
            Response.BAD_REQUEST,
            Response.EMPTY
        );
    }

    @ServiceFactory(stage = 1, week = 1)
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public ok.dht.Service create(ServiceConfig config) {
            return new Service(config);
        }
    }
}
