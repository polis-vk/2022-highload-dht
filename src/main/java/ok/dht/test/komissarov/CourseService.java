package ok.dht.test.komissarov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.komissarov.database.MemorySegmentDao;
import ok.dht.test.komissarov.database.models.BaseEntry;
import ok.dht.test.komissarov.database.models.Config;
import ok.dht.test.komissarov.database.models.Entry;
import ok.dht.test.komissarov.utils.CustomHttpServer;
import ok.dht.test.komissarov.utils.Validator;
import one.nio.http.HttpServerConfig;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

public class CourseService implements Service {

    private static final Validator validator = new Validator();
    private final ServiceConfig config;
    private CustomHttpServer server;
    private MemorySegmentDao dao;

    public CourseService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        dao = new MemorySegmentDao(new Config(
                Files.createTempDirectory("dao"),
                1 << 20
        ));
        server = new CustomHttpServer(createConfigFromPort(config.selfPort()));
        server.start();
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response findById(@Param(value = "id", required = true) String id) {
        if (validator.validate(id)) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }

        Entry<MemorySegment> entry = dao.get(fromString(id));
        if (entry == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        return new Response(Response.OK, entry.value().toByteArray());
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response persist(@Param(value = "id", required = true) String id,
                            Request request) {
        if (validator.validate(id)) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }

        Entry<MemorySegment> entry = new BaseEntry<>(
                fromString(id),
                MemorySegment.ofArray(request.getBody())
        );
        dao.upsert(entry);
        return new Response(Response.CREATED, Response.EMPTY);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response delete(@Param(value = "id", required = true) String id) {
        if (validator.validate(id)) {
            return new Response(Response.BAD_REQUEST, Response.EMPTY);
        }

        Entry<MemorySegment> removedEntry = new BaseEntry<>(
                fromString(id),
                null
        );
        dao.upsert(removedEntry);
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        dao.close();
        server.stop();
        return CompletableFuture.completedFuture(null);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    private static MemorySegment fromString(String value) {
        return MemorySegment.ofArray(value.toCharArray());
    }

    @ServiceFactory(stage = 1, week = 1)
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new CourseService(config);
        }

    }

}
