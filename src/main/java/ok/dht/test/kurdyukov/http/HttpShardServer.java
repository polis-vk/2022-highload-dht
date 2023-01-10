package ok.dht.test.kurdyukov.http;

import ok.dht.test.kurdyukov.dao.repository.DaoRepository;
import ok.dht.test.kurdyukov.sharding.Sharding;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.RejectedSessionException;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class HttpShardServer extends HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(HttpShardServer.class);
    private static final int AWAIT_TERMINATE_SECONDS = 1;
    private static final String ENTITY_ENDPOINT = "/v0/entity";
    private static final String ENTITIES_ENDPOINT = "/v0/entities";

    private final ExecutorService executorService;
    private final DaoRepository daoRepository;
    private final HttpShardService httpShardService;
    private final HttpRangeService httpRangeService;

    public HttpShardServer(HttpServerConfig config, List<String> urls, DaoRepository daoRepository,
                           ExecutorService executorService, Sharding sharding, Object... routers) throws IOException {
        super(config, routers);
        this.daoRepository = daoRepository;
        this.executorService = executorService;

        var client = new HttpClientDao();

        this.httpShardService = new HttpShardService(
                daoRepository,
                client,
                urls,
                sharding
        );

        this.httpRangeService = new HttpRangeService(
                daoRepository
        );
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        try {
            executorService.execute(
                    () -> {
                        switch (request.getPath()) {
                            case ENTITY_ENDPOINT -> httpShardService.executeOnRequest(request, session);
                            case ENTITIES_ENDPOINT -> httpRangeService.executeOnRequest(request, session);
                            default -> {
                                try {
                                    session.sendResponse(new Response(Response.BAD_REQUEST, Response.EMPTY));
                                } catch (IOException e) {
                                    logger.error("Send response is fail.", e);
                                    session.close();
                                }
                            }
                        }
                    }
            );
        } catch (RejectedExecutionException e) {
            logger.warn("Reject request", e);
            session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
        }
    }

    @Override
    public HttpSession createSession(Socket socket) throws RejectedSessionException {
        return new HttpSession(socket, this) {
            @Override
            protected void writeResponse(Response response, boolean includeBody) throws IOException {
                if (response instanceof HttpChunkedResponse castResponse) {
                    super.writeResponse(castResponse, includeBody);
                    super.write(
                            new ChunkedQueueItem(
                                    castResponse.iterator,
                                    castResponse.upperBound
                            )
                    );
                } else {
                    super.writeResponse(response, includeBody);
                }
            }
        };
    }

    @Override
    public synchronized void stop() {
        executorService.shutdown();

        try {
            if (!executorService.awaitTermination(AWAIT_TERMINATE_SECONDS, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();

            logger.error("Fail stopping thread pool workers", e);
            Thread.currentThread().interrupt();
        }

        daoRepository.close();

        try {
            for (SelectorThread thread : selectors) {
                thread.selector.forEach(Session::close);
            }
        } catch (ClosedSelectorException e) {
            logger.error("Sockets were closed.", e);
        }

        super.stop();
    }
}
