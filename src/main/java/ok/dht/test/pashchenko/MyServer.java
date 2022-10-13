package ok.dht.test.pashchenko;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import ok.dht.test.pashchenko.dao.Config;
import ok.dht.test.pashchenko.dao.Entry;
import ok.dht.test.pashchenko.dao.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;

public class MyServer extends HttpServer {
    private static final Logger LOG = LoggerFactory.getLogger(MyServer.class);

    private final MemorySegmentDao dao;
    private final Executor executor;

    public MyServer(ServiceConfig config) throws IOException {
        super(createConfigFromPort(config.selfPort()));
        dao = new MemorySegmentDao(new Config(config.workingDir(), 1048576L));
        executor = Executors.newFixedThreadPool(16);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!"/v0/entity".equals(request.getPath())) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        String id = request.getParameter("id=");
        if (id == null || id.isEmpty()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        executor.execute(() -> {
            try {
                session.sendResponse(handleRequest(request, id));
            } catch (Exception e) {
                LOG.error("error handle request", e);
                sendError(session);
            }
        });
    }

    private static void sendError(HttpSession session) {
        try {
            session.sendResponse(new Response(Response.INTERNAL_ERROR, Response.EMPTY));
        } catch (Exception ex) {
            LOG.error("error send response", ex);
            sessionClose(session);
        }
    }

    private static void sessionClose(HttpSession session) {
        try {
            session.close();
        } catch (Exception e) {
            LOG.error("error close session", e);
        }
    }


    private Response handleRequest(Request request, String id) {
        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                Entry entry = dao.get(MemorySegment.ofArray(Utf8.toBytes(id)));
                if (entry == null) {
                    return new Response(Response.NOT_FOUND, Response.EMPTY);
                }
                return new Response(Response.OK, entry.value().toByteArray());
            }
            case Request.METHOD_PUT -> {
                dao.upsert(new Entry(MemorySegment.ofArray(Utf8.toBytes(id)), MemorySegment.ofArray(request.getBody())));
                return new Response(Response.CREATED, Response.EMPTY);
            }
            case Request.METHOD_DELETE -> {
                dao.upsert(new Entry(MemorySegment.ofArray(Utf8.toBytes(id)), null));
                return new Response(Response.ACCEPTED, Response.EMPTY);
            }
            default -> {
                return new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY);
            }
        }
    }

    @Override
    public synchronized void stop() {
        super.stop();
        for (SelectorThread selector : selectors) {
            for (Session session : selector.selector) {
                session.close();
            }
        }
        try {
            dao.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
