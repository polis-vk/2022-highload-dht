package ok.dht.test.lutsenko.service;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.lutsenko.dao.PersistenceRangeDao;
import ok.dht.test.lutsenko.dao.common.BaseEntry;
import ok.dht.test.lutsenko.dao.common.DaoConfig;
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
import java.nio.file.Files;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import static ok.dht.test.lutsenko.service.ServiceUtils.shutdownAndAwaitTermination;
import static ok.dht.test.lutsenko.service.ServiceUtils.uncheckedSendResponse;

public class DemoService implements Service {

    private final ServiceConfig config;
    private HttpServer server;
    private PersistenceRangeDao dao;
    private ThreadPoolExecutor requestExecutor;

    public DemoService(ServiceConfig config) {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        requestExecutor = RequestExecutorService.requestExecutorDiscard();
        dao = new PersistenceRangeDao(DaoConfig.defaultConfig());
        if (Files.notExists(config.workingDir())) {
            Files.createDirectory(config.workingDir());
        }
        server = new HttpServer(createConfigFromPort(config.selfPort())) {

            @Override
            public void handleDefault(Request request, HttpSession session) {
                requestExecutor.execute(new SessionRunnable(session,
                        () -> {
                            Response response = new Response(Response.BAD_REQUEST, Response.EMPTY);
                            uncheckedSendResponse(session, response);
                        })
                );
            }

            @Override
            public synchronized void stop() {
                for (SelectorThread thread : selectors) {
                    for (Session session : thread.selector) {
                        session.close();
                    }
                }
                super.stop();
            }
        };
        server.addRequestHandlers(this);
        server.start();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        shutdownAndAwaitTermination(requestExecutor);
        server.stop();
        dao.close();
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public void handleGet(HttpSession session, @Param(value = "id", required = true) String id) {
        requestExecutor.execute(new SessionRunnable(session,
                () -> {
                    if (id.isBlank()) {
                        uncheckedSendResponse(session, new Response(Response.BAD_REQUEST, Response.EMPTY));
                        return;
                    }
                    BaseEntry<String> entry = dao.get(id);
                    Response response = entry == null
                            ? new Response(Response.NOT_FOUND, Response.EMPTY)
                            : new Response(Response.OK, Base64.getDecoder().decode(entry.value()));
                    uncheckedSendResponse(session, response);
                })
        );
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public void handlePut(Request request, HttpSession session, @Param(value = "id", required = true) String id) {
        requestExecutor.execute(new SessionRunnable(session,
                () -> {
                    if (id.isBlank()) {
                        uncheckedSendResponse(session, new Response(Response.BAD_REQUEST, Response.EMPTY));
                        return;
                    }
                    dao.upsert(new BaseEntry<>(id, Base64.getEncoder().encodeToString(request.getBody())));
                    uncheckedSendResponse(session, new Response(Response.CREATED, Response.EMPTY));
                })
        );
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public void handleDelete(HttpSession session, @Param(value = "id", required = true) String id) {
        requestExecutor.execute(new SessionRunnable(session,
                () -> {
                    if (id.isBlank()) {
                        uncheckedSendResponse(session, new Response(Response.BAD_REQUEST, Response.EMPTY));
                        return;
                    }
                    dao.upsert(new BaseEntry<>(id, null));
                    uncheckedSendResponse(session, new Response(Response.ACCEPTED, Response.EMPTY));
                })
        );
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    @ServiceFactory(stage = 2, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new DemoService(config);
        }
    }

}
