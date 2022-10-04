package ok.dht.test.siniachenko.service;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.siniachenko.storage.BaseEntry;
import ok.dht.test.siniachenko.storage.Config;
import ok.dht.test.siniachenko.storage.Entry;
import ok.dht.test.siniachenko.storage.MemorySegmentDao;
import one.nio.http.*;
import one.nio.server.AcceptorConfig;
import one.nio.util.Utf8;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.DbImpl;
import org.iq80.leveldb.impl.Level;
import org.iq80.leveldb.impl.Level0;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class Service implements ok.dht.Service {

    public static final long FLUSH_THRESHOLD_BYTES = (long) 1E15;

    private final ServiceConfig config;
//    private MemorySegmentDao db;
    private DB levelDb;
    private HttpServer server;

    public Service(ServiceConfig config) {
        this.config = config;
    }

    @java.lang.Override
    public CompletableFuture<?> start() throws IOException {
        levelDb = new DbImpl(new Options(), config.workingDir().toFile());
//        db = new MemorySegmentDao(new Config(config.workingDir(), FLUSH_THRESHOLD_BYTES));
        System.out.println("Started DB in directory " + config.workingDir());
        server = new HttpServer(createConfigFromPort(config.selfPort())) {
            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
                session.sendResponse(response);
            }
        };
        server.addRequestHandlers(this);
        server.start();
        System.out.println("Service started on " + config.selfUrl());
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
//        db.close();
        levelDb.close();
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response getEntity(@Param(value = "id", required = true) String id) {
        if (id == null || id.isEmpty()) {
            return new Response(
                Response.BAD_REQUEST,
                Response.EMPTY
            );
        }
//        Entry<MemorySegment> valueSegment = db.get(MemorySegment.ofArray(Utf8.toBytes(id)));
        byte[] value = levelDb.get(Utf8.toBytes(id));
        if (value == null) {
            return new Response(
                Response.NOT_FOUND,
                Response.EMPTY
            );
        } else {
//            byte[] value = valueSegment.value().toByteArray();
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
        if (id == null || id.isEmpty()) {
            return new Response(
                Response.BAD_REQUEST,
                Response.EMPTY
            );
        }
//        db.upsert(
//            new BaseEntry<>(
//                MemorySegment.ofArray(Utf8.toBytes(id)),
//                MemorySegment.ofArray(request.getBody())
//            )
//        );
        levelDb.put(Utf8.toBytes(id), request.getBody());
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
        if (id == null || id.isEmpty()) {
            return new Response(
                Response.BAD_REQUEST,
                Response.EMPTY
            );
        }
//        db.upsert(
//            new BaseEntry<>(
//                MemorySegment.ofArray(Utf8.toBytes(id)),
//                null
//            )
//        );
        levelDb.delete(Utf8.toBytes(id));
        return new Response(
            Response.ACCEPTED,
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
