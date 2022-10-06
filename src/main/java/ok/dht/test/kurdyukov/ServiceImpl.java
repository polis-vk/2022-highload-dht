package ok.dht.test.kurdyukov;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;
import one.nio.server.ServerConfig;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.iq80.leveldb.impl.Iq80DBFactory.bytes;
import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

public class ServiceImpl implements Service {
    private static final Logger logger = LoggerFactory.getLogger(ServiceImpl.class);
    private static final String PATH = "/v0/entity";
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
        httpServer.start();
        httpServer.addRequestHandlers(this);

        levelDB = createDao(serviceConfig.workingDir());

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        httpServer.stop();
        levelDB.close();

        return CompletableFuture.completedFuture(null);
    }

    @Path(PATH)
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(
            @Param(value = "id", required = true) String id
    ) {
        if (id.isBlank()) {
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

    @Path(PATH)
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(
            @Param(value = "id", required = true) String id,
            Request request
    ) {
        if (id.isBlank()) {
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

    @Path(PATH)
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(
            @Param(value = "id", required = true) String id
    ) {
        if (id.isBlank()) {
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
        return new HttpServer(
                httpServerConfig(
                        port,
                        url
                )
        ) {
            @Override
            public void handleDefault(
                    Request request,
                    HttpSession session
            ) throws IOException {
                if (request.getPath().equals(PATH) && !supportMethods.contains(request.getMethod())) {
                    session.sendResponse(responseEmpty(Response.METHOD_NOT_ALLOWED));
                } else {
                    session.sendResponse(responseEmpty(Response.BAD_REQUEST));
                }
            }

            @Override
            public synchronized void stop() {
                for (SelectorThread thread : selectors) {
                    thread.selector.forEach(Session::close);
                }

                super.stop();
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

        for (var acceptor : httpConfig.acceptors) {
            acceptor.reusePort = true;
            acceptor.port = port;
        }

        return httpConfig;
    }

    @ServiceFactory(stage = 1, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
