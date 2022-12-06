package ok.dht.test.zhidkov.services;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.zhidkov.model.storage.BaseEntry;
import ok.dht.test.zhidkov.model.storage.Config;
import ok.dht.test.zhidkov.model.storage.Dao;
import ok.dht.test.zhidkov.model.storage.Entry;
import ok.dht.test.zhidkov.model.storage.MemorySegmentDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.AcceptorConfig;
import one.nio.server.SelectorThread;
import one.nio.util.Utf8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

public class ZhidkovService implements Service {
    private static final long FLUSH_THRESHOLD_BYTES = 1 << 20;
    private static final int QUEUE_SIZE = 100;
    private static final RejectedExecutionHandler HANDLER_TYPE = new ThreadPoolExecutor.DiscardPolicy();
    private final ServiceConfig config;
    private Dao<MemorySegment, Entry<MemorySegment>> dao;
    private HttpServer server;
    private RequestExecutorService executorService;

    public ZhidkovService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        this.dao = new MemorySegmentDao(new Config(config.workingDir(), FLUSH_THRESHOLD_BYTES));

        this.executorService = new RequestExecutorService(QUEUE_SIZE, HANDLER_TYPE);

        this.server = new HttpServer(createConfigFromPort(config.selfPort())) {

            @Override
            public void handleRequest(Request request, HttpSession session) throws IOException {
                try {
                    executorService.submitTask(() -> {
                        try {
                            if (!request.getPath().equals("/v0/entity")) {
                                session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                                return;
                            }

                            String parameter = request.getParameter("id=");
                            switch (request.getMethod()) {
                                case Request.METHOD_GET -> handleGet(parameter, session);
                                case Request.METHOD_PUT -> handelPut(parameter, request.getBody(), session);
                                case Request.METHOD_DELETE -> handleDelete(parameter, session);
                                default ->
                                        session.sendResponse(new Response(Response.METHOD_NOT_ALLOWED, Response.EMPTY));
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                } catch (RejectedExecutionException e) {
                    session.sendError(Response.REQUEST_TIMEOUT, "Request execution timeout");
                }
            }

            @Override
            public synchronized void stop() {
                for (SelectorThread selector : selectors) {
                    for (Session session : selector.selector) {
                        session.close();
                    }
                }
            }
        };

        this.server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        this.server.stop();
        this.executorService.shutdown();
        this.dao.close();
        return CompletableFuture.completedFuture(null);
    }

    public void handleGet(String id, HttpSession session) throws IOException {
        if (id == null || id.isBlank()) { // Может быть null, если не подали параметр вообще
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        Entry<MemorySegment> value = dao.get(MemorySegment.ofArray(Utf8.toBytes(id)));
        if (value != null) {
            session.sendResponse(new Response(Response.OK, value.value().toByteArray()));
            return;
        }
        session.sendResponse(new Response(Response.NOT_FOUND, Response.EMPTY));
    }

    public void handelPut(String id, byte[] body, HttpSession session) throws IOException {
        if (id == null || id.isBlank()) { // Может быть null, если не подали параметр вообще
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        dao.upsert(new BaseEntry<>(MemorySegment.ofArray(Utf8.toBytes(id)), MemorySegment.ofArray(body)));
        session.sendResponse(new Response(Response.CREATED, Response.EMPTY));
    }

    public void handleDelete(String id, HttpSession session) throws IOException {
        if (id == null || id.isBlank()) { // Может быть null, если не подали параметр вообще
            session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
            return;
        }

        dao.upsert(new BaseEntry<>(MemorySegment.ofArray(Utf8.toBytes(id)), null));
        session.sendResponse(new Response(Response.ACCEPTED, Response.EMPTY));
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 2, week = 2)
    public static class ZhidkovFactory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ZhidkovService(config);
        }
    }
}

