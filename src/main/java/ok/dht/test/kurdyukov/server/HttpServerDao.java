package ok.dht.test.kurdyukov.server;

import ok.dht.test.kurdyukov.client.HttpClientDao;
import ok.dht.test.kurdyukov.sharding.Sharding;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.channels.ClosedSelectorException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.iq80.leveldb.impl.Iq80DBFactory.bytes;

public class HttpServerDao extends HttpServer {
    private static final Logger logger = LoggerFactory.getLogger(HttpServerDao.class);
    private static final int AWAIT_TERMINATE_SECONDS = 1;

    private static final Set<Integer> supportMethods = Set.of(
            Request.METHOD_GET,
            Request.METHOD_PUT,
            Request.METHOD_DELETE
    );

    public static final String ENDPOINT = "/v0/entity";
    public static final String PING = "/ping";
    private final Map<String, HttpClientDao> clients;

    private final DB levelDB;
    private final ExecutorService executorService;
    private final Sharding sharding;
    private final String selfUrl;

    public HttpServerDao(
            HttpServerConfig config,
            Map<String, HttpClientDao> clients,
            DB levelDB,
            ExecutorService executorService,
            Sharding sharding,
            String selfUrl,
            Object... routers
    ) throws IOException {
        super(config, routers);
        this.clients = clients;
        this.levelDB = levelDB;
        this.executorService = executorService;
        this.sharding = sharding;
        this.selfUrl = selfUrl;
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (request.getPath().equals(PING)) {
            session.sendResponse(responseEmpty(Response.OK));
            return;
        }

        if (!request.getPath().equals(ENDPOINT)) {
            session.sendResponse(responseEmpty(Response.BAD_REQUEST));
            return;
        }

        int method = request.getMethod();

        if (!supportMethods.contains(method)) {
            session.sendResponse(responseEmpty(Response.METHOD_NOT_ALLOWED));
            return;
        }

        String id = request.getParameter("id=");

        if (id == null || id.isBlank()) {
            session.sendResponse(responseEmpty(Response.BAD_REQUEST));
            return;
        }

        try {
            executorService.execute(() -> {
                        try {
                            final String urlNode = sharding.getShardUrlByKey(id);

                            if (urlNode.equals(selfUrl)) {
                                session.sendResponse(handle(request, method, id));
                            } else {
                                HttpClientDao httpClientDao = clients.get(urlNode);

                                if (httpClientDao.isNotConnect.get()) {
                                    logger.error("Fail node is ill");

                                    session.sendResponse(
                                            responseEmpty(Response.INTERNAL_ERROR)
                                    );
                                    return;
                                }

                                httpClientDao
                                        .requestNode(
                                                String.format("%s%s%s", urlNode, ENDPOINT, "?id=" + id),
                                                method,
                                                request
                                        )
                                        .handleAsync(
                                                (httpResponse, throwable) -> {
                                                    try {
                                                        if (throwable != null) {
                                                            logger.error("Fail send to other node", throwable);

                                                            session.sendResponse(
                                                                    responseEmpty(Response.INTERNAL_ERROR)
                                                            );
                                                        } else {
                                                            Response response = new Response(
                                                                    String.valueOf(httpResponse.statusCode()),
                                                                    httpResponse.body()
                                                            );
                                                            session.sendResponse(response);
                                                        }
                                                    } catch (IOException e) {
                                                        httpClientDao.isNotConnect.set(true);
                                                        logger.error("Fail send response", e);
                                                        throw new UncheckedIOException(e);
                                                    }
                                                    return null;
                                                }
                                        );
                            }
                        } catch (URISyntaxException e) {
                            logger.error("Fail invalid uri! Unreal case.", e);
                            throw new RuntimeException(e);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
            );
        } catch (RejectedExecutionException e) {
            logger.warn("Reject request", e);
            session.sendResponse(responseEmpty(Response.SERVICE_UNAVAILABLE));
        }
    }

    @Override
    public synchronized void stop() {
        try {
            for (SelectorThread thread : selectors) {
                thread.selector.forEach(Session::close);
            }
        } catch (ClosedSelectorException e) {
            logger.error("Sockets were closed.", e);
        }

        clients.forEach((key, value) -> value.close());

        super.stop();
        executorService.shutdown();

        try {
            if (executorService.awaitTermination(AWAIT_TERMINATE_SECONDS, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Fail stopping thread pool workers", e);
            Thread.currentThread().interrupt();
        }

        try {
            levelDB.close();
        } catch (IOException e) {
            logger.error("Fail db close.", e);
        }
    }

    private Response handle(Request request, int method, String id) {
        return switch (method) {
            case Request.METHOD_GET -> handleGet(id);
            case Request.METHOD_PUT -> handlePut(request, id);
            case Request.METHOD_DELETE -> handleDelete(id);
            default -> throw new IllegalArgumentException("Unsupported method!");
        };
    }

    private Response handleGet(String id) {
        byte[] value;

        try {
            value = levelDB.get(bytes(id));
        } catch (DBException e) {
            logger.error("Fail on get method with id: " + id, e);

            return responseEmpty(Response.INTERNAL_ERROR);
        }

        if (value == null) {
            return responseEmpty(Response.NOT_FOUND);
        } else {
            return new Response(Response.OK, value);
        }
    }

    private Response handlePut(Request request, String id) {
        try {
            levelDB.put(bytes(id), request.getBody());
            return responseEmpty(Response.CREATED);
        } catch (DBException e) {
            logger.error("Fail on put method with id: " + id, e);
            return responseEmpty(Response.INTERNAL_ERROR);
        }
    }

    private Response handleDelete(String id) {
        try {
            levelDB.delete(bytes(id));
            return responseEmpty(Response.ACCEPTED);
        } catch (DBException e) {
            logger.error("Fail on delete method with id: " + id, e);
            return responseEmpty(Response.INTERNAL_ERROR);
        }
    }

    private static Response responseEmpty(String status) {
        return new Response(status, Response.EMPTY);
    }
}
