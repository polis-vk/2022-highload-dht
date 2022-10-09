package ok.dht.test.trofimov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.trofimov.dao.BaseEntry;
import ok.dht.test.trofimov.dao.Config;
import ok.dht.test.trofimov.dao.Entry;
import ok.dht.test.trofimov.dao.MyHttpServer;
import ok.dht.test.trofimov.dao.impl.InMemoryDao;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MyService implements Service {

    public static final String PATH_V0_ENTITY = "/v0/entity";
    private static final Logger logger = LoggerFactory.getLogger(MyService.class);
    private static final long FLUSH_THRESHOLD = 1 << 20;
    private static final int REQUESTS_MAX_QUEUE_SIZE = 1024;
    private final ServiceConfig config;
    private HttpServer server;
    private InMemoryDao dao;
    private ThreadPoolExecutor requestsExecutor;

    public MyService(ServiceConfig config) {
        this.config = config;
    }

    private Config createDaoConfig() {
        return new Config(config.workingDir(), FLUSH_THRESHOLD);
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        dao = new InMemoryDao(createDaoConfig());
        initExecutor();
        server = new MyHttpServer(createConfigFromPort(config.selfPort()));
        server.start();
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    private void initExecutor() {
        int threadsCount = Runtime.getRuntime().availableProcessors();
        requestsExecutor = new ThreadPoolExecutor(threadsCount, threadsCount,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(REQUESTS_MAX_QUEUE_SIZE),
                new ThreadPoolExecutor.DiscardOldestPolicy());
        requestsExecutor.prestartAllCoreThreads();
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        server.stop();
        requestsExecutor.shutdownNow();
        dao.close();
        return CompletableFuture.completedFuture(null);
    }

    @Path(PATH_V0_ENTITY)
    @RequestMethod(Request.METHOD_GET)
    public void handleGet(@Param(value = "id", required = true) String id, HttpSession session) {
        requestsExecutor.execute(() -> {
            Response response;
            if (id.isEmpty()) {
                response = emptyResponseFor(Response.BAD_REQUEST);
            } else {
                try {
                    Entry<String> entry = dao.get(id);
                    if (entry == null) {
                        response = emptyResponseFor(Response.NOT_FOUND);
                    } else {
                        String value = entry.value();
                        char[] chars = value.toCharArray();
                        response = new Response(Response.OK, Base64.decodeFromChars(chars));
                    }
                } catch (Exception e) {
                    logger.error("error get request" + id, e);
                    response = errorResponse();
                }
            }
            sendResponse(session, response);
        });
    }

    @Path(PATH_V0_ENTITY)
    @RequestMethod(Request.METHOD_PUT)
    public void handlePut(Request request, @Param(value = "id", required = true) String id, HttpSession session) {
        requestsExecutor.execute(() -> {
            Response response;
            if (id.isEmpty()) {
                response = emptyResponseFor(Response.BAD_REQUEST);
            } else {
                int size = requestsExecutor.getQueue().size();
                if (size == 0) {
                    System.out.println(size);
                }
                byte[] value = request.getBody();
                try {
                    dao.upsert(new BaseEntry<>(id, new String(Base64.encodeToChars(value))));
                    response = emptyResponseFor(Response.CREATED);
                } catch (Exception e) {
                    logger.error("error put request" + id, e);
                    response = errorResponse();
                }
            }
            sendResponse(session, response);
        });
    }

    @Path(PATH_V0_ENTITY)
    @RequestMethod(Request.METHOD_DELETE)
    public void handleDelete(@Param(value = "id", required = true) String id, HttpSession session) {
        requestsExecutor.execute(() -> {
            Response response;
            if (id.isEmpty()) {
                response = emptyResponseFor(Response.BAD_REQUEST);
            } else {
                try {
                    dao.upsert(new BaseEntry<>(id, null));
                    response = emptyResponseFor(Response.ACCEPTED);
                } catch (Exception e) {
                    logger.error("error delete request" + id, e);
                    response = errorResponse();
                }
            }
            sendResponse(session, response);
        });

    }

    private static void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            logger.error("Error send response", e);
            closeSession(session);
        }
    }

    private static void closeSession(HttpSession session) {
        try {
            session.close();
        } catch (Exception e) {
            logger.error("Error in closing session", e);
        }
    }

    @Path(PATH_V0_ENTITY)
    @RequestMethod(Request.METHOD_POST)
    public Response handlePost() {
        return emptyResponseFor(Response.METHOD_NOT_ALLOWED);
    }

    private Response emptyResponseFor(String status) {
        return new Response(status, Response.EMPTY);
    }

    private Response errorResponse() {
        return new Response(Response.INTERNAL_ERROR, Response.EMPTY);
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
            return new MyService(config);
        }
    }
}

