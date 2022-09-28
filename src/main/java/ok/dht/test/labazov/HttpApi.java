package ok.dht.test.labazov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.labazov.dao.BaseEntry;
import ok.dht.test.labazov.dao.Dao;
import ok.dht.test.labazov.dao.Entry;
import ok.dht.test.labazov.dao.Config;
import ok.dht.test.labazov.dao.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.server.AcceptorConfig;

import java.io.IOException;

public class HttpApi extends HttpServer {
    private Dao<MemorySegment, Entry<MemorySegment>> dao;
    private final ServiceConfig config;
    private static final Response HTTP_BAD_REQUEST = new Response(Response.BAD_REQUEST, Response.EMPTY);

    public HttpApi(ServiceConfig config) throws IOException {
        super(createConfigFromPort(config.selfPort()));
        this.config = config;
    }

    @Override
    public synchronized void start() {
        try {
            dao = new MemorySegmentDao(new Config(config.workingDir(), 8 * 1024 * 1024));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        super.start();
    }

    @Override
    public synchronized void stop() {
        super.stop();
        try {
            dao.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        session.sendResponse(HTTP_BAD_REQUEST);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id", required = true) String key) throws IOException {
        if (key.isEmpty()) {
            return HTTP_BAD_REQUEST;
        }
        Entry<MemorySegment> result = dao.get(fromString(key));
        if (result == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        } else {
            return Response.ok(result.value().toByteArray());
        }
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(@Param(value = "id", required = true) String key, Request req) {
        if (key.isEmpty()) {
            return HTTP_BAD_REQUEST;
        }
        dao.upsert(new BaseEntry<>(fromString(key), MemorySegment.ofArray(req.getBody())));
        return new Response(
                Response.CREATED,
                Response.EMPTY
        );
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(value = "id", required = true) String key) {
        if (key.isEmpty()) {
            return HTTP_BAD_REQUEST;
        }
        dao.upsert(new BaseEntry<>(fromString(key), null));
        return new Response(
                Response.ACCEPTED,
                Response.EMPTY
        );
    }

    private static MemorySegment fromString(String data) {
        return data == null ? null : MemorySegment.ofArray(data.toCharArray());
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }
}
