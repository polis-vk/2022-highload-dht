package ok.dht.test.gerasimov.server;

import ok.dht.test.gerasimov.exception.ServerException;
import ok.dht.test.gerasimov.service.HandleService;
import ok.dht.test.gerasimov.utils.ResponseEntity;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.net.Session;
import one.nio.server.SelectorThread;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;

public final class Server extends HttpServer {
    private final ScheduledExecutorService scheduledExecutorService;
    private final ExecutorService executorService;
    private final Map<String, HandleService> services;

    public Server(
            HttpServerConfig httpServerConfig,
            Map<String, HandleService> services,
            ExecutorService executorService,
            ScheduledExecutorService scheduledExecutorService
    ) throws IOException {
        super(httpServerConfig);
        this.scheduledExecutorService = scheduledExecutorService;
        this.executorService = executorService;
        this.services = services;
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        session.sendResponse(ResponseEntity.badRequest());
    }

    @Override
    public synchronized void start() {
        super.start();
    }

    @Override
    public synchronized void stop() {
        for (SelectorThread thread : selectors) {
            for (Session session : thread.selector) {
                session.socket().close();
                session.close();
            }
        }
        super.stop();
        executorService.shutdown();
        scheduledExecutorService.shutdown();
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            executorService.execute(() -> wrappedHandleRequest(request, session));
        } catch (RejectedExecutionException e) {
            session.sendResponse(ResponseEntity.serviceUnavailable());
        }
    }

    private void wrappedHandleRequest(Request request, HttpSession session) {
        try {
            HandleService service = services.get(request.getPath());

            if (service == null) {
                session.sendResponse(ResponseEntity.badRequest("Unsupported path"));
            } else {
                service.handleRequest(request, session);
            }
        } catch (IOException e) {
            throw new ServerException("Handler can not handle request", e);
        }
    }
}
