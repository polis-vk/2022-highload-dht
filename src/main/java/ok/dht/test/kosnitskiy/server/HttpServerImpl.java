package ok.dht.test.kosnitskiy.server;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.kosnitskiy.dao.BaseEntry;
import ok.dht.test.kosnitskiy.dao.Entry;
import ok.dht.test.kosnitskiy.dao.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HttpServerImpl extends HttpServer {
    private static final Logger LOG = LoggerFactory.getLogger(HttpServerImpl.class);
    private final ThreadPoolExecutor executor;
    private final MemorySegmentDao memorySegmentDao;

    private static final long SHUTDOWN_WAIT_TIME_SECONDS = 60;

    public HttpServerImpl(HttpServerConfig config,
                          MemorySegmentDao memorySegmentDao,
                          ThreadPoolExecutor executor, Object... routers) throws IOException {
        super(config, routers);
        this.executor = executor;
        this.memorySegmentDao = memorySegmentDao;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        String id = request.getParameter("id=");
        if (!isTypeSupported(request)) {
            session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
        }

        if (!"/v0/entity".equals(request.getPath()) || id == null || id.isEmpty()) {
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
        }

        executor.execute(() -> {
            try {
                handleSupported(request, session, id);
            } catch (Exception e) {
                try {
                    session.sendResponse(new Response(
                            Response.INTERNAL_ERROR,
                            ("Internal server error has occurred: " + e.getMessage()).getBytes(StandardCharsets.UTF_8)
                    ));
                } catch (IOException ex) {
                    LOG.error("Unable to send answer to " + session.getRemoteHost());
                    session.scheduleClose();
                }
            }
        });
    }

    @Override
    public synchronized void stop() {
        executor.shutdown();
        boolean isTerminated;
        try {
            isTerminated = executor.awaitTermination(SHUTDOWN_WAIT_TIME_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            isTerminated = false;
            LOG.error("Waiting for tasks to finish timed out");
            Thread.currentThread().interrupt();
        }
        if (!isTerminated) {
            try {
                executor.shutdownNow();
            } catch (Exception exception) {
                LOG.error("Server won't shutdown");
                System.exit(1);
            }
        }
        for (SelectorThread selectorThread : selectors) {
            for (Session session : selectorThread.selector) {
                session.scheduleClose();
            }
        }
        super.stop();
    }

    private boolean isTypeSupported(Request request) {
        return request.getMethod() == Request.METHOD_GET
                || request.getMethod() == Request.METHOD_PUT
                || request.getMethod() == Request.METHOD_DELETE;
    }

    private void handleSupported(Request request, HttpSession session, String id) throws IOException {
        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                Entry<MemorySegment> entry;
                try {
                    entry = memorySegmentDao.get(MemorySegment.ofArray(id.toCharArray()));
                } catch (Exception e) {
                    LOG.error("Error occurred while getting " + id + ' ' + e.getMessage());
                    session.sendResponse(new Response(
                            Response.INTERNAL_ERROR,
                            e.getMessage().getBytes(StandardCharsets.UTF_8)
                    ));
                    return;
                }
                if (entry != null) {
                    session.sendResponse(new Response(
                            Response.OK,
                            entry.value().toByteArray()
                    ));
                    return;
                }
                session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
            }
            case Request.METHOD_PUT -> {
                try {
                    memorySegmentDao.upsert(new BaseEntry<>(MemorySegment.ofArray(id.toCharArray()),
                            MemorySegment.ofArray(request.getBody())));
                } catch (Exception e) {
                    LOG.error("Error occurred while inserting " + id + ' ' + e.getMessage());
                    session.sendResponse(new Response(
                            Response.INTERNAL_ERROR,
                            e.getMessage().getBytes(StandardCharsets.UTF_8)
                    ));
                    return;
                }
                session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
            }
            case Request.METHOD_DELETE -> {
                try {
                    memorySegmentDao.upsert(new BaseEntry<>(MemorySegment.ofArray(id.toCharArray()), null));
                } catch (Exception e) {
                    LOG.error("Error occurred while deleting " + id + ' ' + e.getMessage());
                    session.sendResponse(new Response(
                            Response.INTERNAL_ERROR,
                            e.getMessage().getBytes(StandardCharsets.UTF_8)
                    ));
                    return;
                }
                session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
            }
            default -> {
                session.sendResponse(new Response(
                        Response.INTERNAL_ERROR,
                        "Unsupported method was invoked somehow".getBytes(StandardCharsets.UTF_8)
                ));
            }
        }
    }
}
