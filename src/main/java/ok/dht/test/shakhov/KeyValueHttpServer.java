package ok.dht.test.shakhov;

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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public class KeyValueHttpServer extends HttpServer {
    private static final Logger log = LoggerFactory.getLogger(KeyValueHttpServer.class);

    private static final String ENDPOINT = "/v0/entity";
    private static final String ID_PARAMETER = "id=";
    private static final int QUEUE_MAX_SIZE = 50_000;
    private static final int MAX_POOL_SIZE = Runtime.getRuntime().availableProcessors() / 2;
    private static final int CORE_POOL_SIZE = MAX_POOL_SIZE / 2;
    private static final int KEEP_ALIVE_TIME_SECONDS = 30;
    private static final RejectedExecutionHandler DISCARD_POLICY = new ThreadPoolExecutor.DiscardPolicy();
    private static final int AWAIT_TERMINATION_TIMEOUT_SECONDS = 20;

    private final BiFunction<Request, String, Response> requestHandler;
    private ExecutorService executorService;

    public KeyValueHttpServer(HttpServerConfig config, BiFunction<Request, String, Response> requestHandler) throws IOException {
        super(config);
        this.requestHandler = requestHandler;
    }

    @Override
    public synchronized void start() {
        super.start();
        BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>(QUEUE_MAX_SIZE);
        executorService = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME_SECONDS, TimeUnit.SECONDS, taskQueue, DISCARD_POLICY);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        executorService.execute(() -> {
            try {
                if (!ENDPOINT.equals(request.getPath())) {
                    session.sendResponse(badRequest());
                    return;
                }

                String id = request.getParameter(ID_PARAMETER);
                if (id == null || id.isEmpty()) {
                    session.sendResponse(badRequest());
                    return;
                }

                session.sendResponse(requestHandler.apply(request, id));
            } catch (IOException e) {
                log.error("Error during sending response", e);
                session.close();
            }
        });
    }

    @Override
    public synchronized void stop() {
        super.stop();

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(AWAIT_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                executorService.awaitTermination(AWAIT_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (SelectorThread selector : selectors) {
            for (Session session : selector.selector) {
                session.close();
            }
        }
    }

    private static Response badRequest() {
        return new Response(Response.BAD_REQUEST, Response.EMPTY);
    }
}
