package ok.dht.test.labazov;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.labazov.dao.BaseEntry;
import ok.dht.test.labazov.dao.Config;
import ok.dht.test.labazov.dao.Dao;
import ok.dht.test.labazov.dao.Entry;
import ok.dht.test.labazov.dao.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;

import java.io.IOException;

public final class HttpApi extends HttpServer {
    public static final int FLUSH_THRESHOLD_BYTES = 8 * 1024 * 1024;
    private Dao<MemorySegment, Entry<MemorySegment>> dao;
    private final ServiceConfig config;

    public HttpApi(ServiceConfig config) throws IOException {
        super(createConfigFromPort(config.selfPort()));
        this.config = config;
    }

    @Override
    public synchronized void start() {
        try {
            dao = new MemorySegmentDao(new Config(config.workingDir(), FLUSH_THRESHOLD_BYTES));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        super.start();
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread thread : selectors) {
            for (Session session : thread.selector) {
                session.close();
            }
        }

        super.stop();
        try {
            dao.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void handleDefault(final Request request, final HttpSession session) throws IOException {
        final String code;
        switch (request.getMethod()) {
            case Request.METHOD_GET, Request.METHOD_PUT, Request.METHOD_DELETE -> code = Response.BAD_REQUEST;
            default -> code = Response.METHOD_NOT_ALLOWED;
        }
        session.sendResponse(getEmptyResponse(code));
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(@Param(value = "id", required = true) final String key) throws IOException {
        if (key.isEmpty()) {
            return getEmptyResponse(Response.BAD_REQUEST);
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
    public Response handlePut(@Param(value = "id", required = true) final String key, final Request req) {
        if (key.isEmpty()) {
            return getEmptyResponse(Response.BAD_REQUEST);
        }
        dao.upsert(new BaseEntry<>(fromString(key), MemorySegment.ofArray(req.getBody())));
        return getEmptyResponse(Response.CREATED);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(@Param(value = "id", required = true) final String key) {
        if (key.isEmpty()) {
            return getEmptyResponse(Response.BAD_REQUEST);
        }
        dao.upsert(new BaseEntry<>(fromString(key), null));
        return getEmptyResponse(Response.ACCEPTED);
    }

    private static Response getEmptyResponse(final String httpCode) {
        return new Response(httpCode, Response.EMPTY);
    }

    private static MemorySegment fromString(final String data) {
        return MemorySegment.ofArray(data.toCharArray());
    }

    private static HttpServerConfig createConfigFromPort(final int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }
}
