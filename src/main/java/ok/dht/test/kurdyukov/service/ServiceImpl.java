package ok.dht.test.kurdyukov.service;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.kurdyukov.server.HttpServerAsync;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.ServerConfig;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.iq80.leveldb.impl.Iq80DBFactory.bytes;
import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

public class ServiceImpl implements Service {
    private static final Logger logger = LoggerFactory.getLogger(ServiceImpl.class);
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();
    public static final int THREAD_POOL_SIZE = 32;
    public static final int SELECTOR_SIZE = AVAILABLE_PROCESSORS / 2;
    public static final String ENDPOINT = "/v0/entity";
    private static final Set<Integer> supportMethods = Set.of(
            Request.METHOD_GET,
            Request.METHOD_PUT,
            Request.METHOD_DELETE
    );

    private final ServiceConfig serviceConfig;

    private HttpServer httpServer;
    private DB levelDB;

    public ServiceImpl(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        httpServer = createHttpServer(
                serviceConfig.selfPort(),
                serviceConfig.selfUrl()
        );
        httpServer.addRequestHandlers(this);
        httpServer.start();

        levelDB = createDao(serviceConfig.workingDir());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        httpServer.stop();
        levelDB.close();

        return CompletableFuture.completedFuture(null);
    }

    @Path(ENDPOINT)
    @RequestMethod({Request.METHOD_GET})
    public Response handleGet(
            @Param(value = "id") String id
    ) {
        if (inValid(id)) {
            return responseEmpty(Response.BAD_REQUEST);
        }

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

    @Path(ENDPOINT)
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(
            @Param(value = "id") String id,
            Request request
    ) {
        if (inValid(id)) {
            return responseEmpty(Response.BAD_REQUEST);
        }

        try {
            levelDB.put(bytes(id), request.getBody());
            return responseEmpty(Response.CREATED);
        } catch (DBException e) {
            logger.error("Fail on put method with id: " + id, e);
            return responseEmpty(Response.INTERNAL_ERROR);
        }
    }

    @Path(ENDPOINT)
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(
            @Param(value = "id") String id
    ) {
        if (inValid(id)) {
            return responseEmpty(Response.BAD_REQUEST);
        }

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

    private static boolean inValid(String param) {
        return param == null || param.isBlank();
    }

    private static DB createDao(
            java.nio.file.Path workingDir
    ) throws IOException {
        Options options = new Options();

        try {
            return factory.open(new File(workingDir.toString()), options);
        } catch (IOException e) {
            logger.error("Error create database.", e);

            throw e;
        }
    }

    private static HttpServer createHttpServer(
            int port,
            String url
    ) throws IOException {
        return new HttpServerAsync(
                httpServerConfig(
                        port,
                        url
                ),
                new ThreadPoolExecutor(
                        THREAD_POOL_SIZE, // fixed thread pool
                        THREAD_POOL_SIZE,
                        0,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(THREAD_POOL_SIZE * 4)
                )
        ) {
            @Override
            public void handleDefault(
                    Request request,
                    HttpSession session
            ) throws IOException {
                if (request.getPath().equals(ENDPOINT) && !supportMethods.contains(request.getMethod())) {
                    session.sendResponse(responseEmpty(Response.METHOD_NOT_ALLOWED));
                } else {
                    session.sendResponse(responseEmpty(Response.BAD_REQUEST));
                }
            }
        };
    }

    private static HttpServerConfig httpServerConfig(
            int port,
            String url
    ) {
        ServerConfig serverConfig = ServerConfig.from(url);

        HttpServerConfig httpConfig = new HttpServerConfig();

        httpConfig.acceptors = serverConfig.acceptors;
        httpConfig.selectors = SELECTOR_SIZE;

        for (var acceptor : httpConfig.acceptors) {
            acceptor.reusePort = true;
            acceptor.port = port;
        }

        return httpConfig;
    }

    @ServiceFactory(stage = 2, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
