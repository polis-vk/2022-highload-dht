package ok.dht.test.kondraev;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.kondraev.dao.Dao;
import ok.dht.test.kondraev.dao.MemorySegmentEntry;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;

import java.io.IOException;

public class Server extends HttpServer {
    public static final String ENTITY_PATH = "/v0/entity";
    final Dao dao;

    public Server(HttpServerConfig config, Dao dao) throws IOException {
        super(config);
        this.dao = dao;
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        session.sendResponse(badRequest());
    }

    @RequestMethod(Request.METHOD_GET)
    @Path(ENTITY_PATH)
    public Response handleGet(@Param(value = "id", required = true) String id) throws IOException {
        if (id.isEmpty()) {
            return badRequest();
        }
        MemorySegmentEntry result = dao.get(segment(id));
        if (result == null) {
            return new Response(Response.NOT_FOUND, Response.EMPTY);
        }
        return Response.ok(result.value.toByteArray());
    }

    @RequestMethod(Request.METHOD_PUT)
    @Path(ENTITY_PATH)
    public Response handlePut(@Param(value = "id", required = true) String id, Request request) {
        if (id.isEmpty()) {
            return badRequest();
        }
        dao.upsert(MemorySegmentEntry.of(segment(id), MemorySegment.ofArray(request.getBody())));
        return new Response(Response.CREATED, Response.EMPTY);
    }

    @RequestMethod(Request.METHOD_DELETE)
    @Path(ENTITY_PATH)
    public Response handleDelete(@Param(value = "id", required = true) String id) {
        if (id.isEmpty()) {
            return badRequest();
        }
        dao.upsert(MemorySegmentEntry.of(segment(id), null));
        return new Response(Response.ACCEPTED, Response.EMPTY);
    }

    @RequestMethod(Request.METHOD_POST)
    @Path(ENTITY_PATH)
    public Response handlePost() {
        return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
    }

    private static Response badRequest() {
        return new Response(Response.BAD_REQUEST, Response.EMPTY);
    }

    public static MemorySegment segment(String data) {
        return MemorySegment.ofArray(data.toCharArray());
    }

    @Override
    public synchronized void stop() {
        super.stop();
        for (SelectorThread thread : selectors) {
            for (Session session : thread.selector) {
                session.close();
            }
        }
    }
}
