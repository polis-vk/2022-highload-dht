package ok.dht.test.maximenko;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.maximenko.dao.BaseEntry;
import ok.dht.test.maximenko.dao.Config;
import ok.dht.test.maximenko.dao.Dao;
import ok.dht.test.maximenko.dao.Entry;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.RequestMethod;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import ok.dht.test.maximenko.dao.MemorySegmentDao;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;


public class DatabaseService implements Service {
    static final int flushDaoThresholdBytes = 10000000;
    final private java.nio.file.Path daoFilesPath = Files.createTempDirectory("dao_files");
    static final Logger logger = Logger.getLogger("Service");

    static final private Response badRequest = new Response(
            String.valueOf(HttpURLConnection.HTTP_BAD_REQUEST),
            Response.EMPTY
    );
    private final ServiceConfig config;
    private HttpServer server;
    private Dao dao;
    public DatabaseService(ServiceConfig config) throws IOException {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        Config daoConfig = new Config(daoFilesPath, flushDaoThresholdBytes);
        dao = new MemorySegmentDao(daoConfig);
        server = new HttpServer(createConfig(config.selfPort())) {
            @Override
            public void handleDefault(Request request, HttpSession session) throws IOException {
                session.sendResponse(badRequest);
            }
        };
        server.start();
        server.addRequestHandlers(this);
        return CompletableFuture.completedFuture(null);
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_GET)
    public Response handleGet(Request request) throws IOException {
        String keyString = request.getParameter("id=");
        if (keyString == null) {
            return badRequest;
        }
        MemorySegment key = MemorySegment.ofArray(keyString.getBytes());
        Entry<MemorySegment> value = dao.get(key);
        if (value != null) {
            return new Response(
                    String.valueOf(HttpURLConnection.HTTP_OK),
                    value.value().asByteBuffer().array()
            );
        }

        return new Response(
                String.valueOf(HttpURLConnection.HTTP_NOT_FOUND),
                Response.EMPTY
        );
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_DELETE)
    public Response handleDelete(Request request) throws IOException {
        String keyString = request.getParameter("id=");
        if (keyString == null) {
            return badRequest;
        }
        MemorySegment key = MemorySegment.ofArray(keyString.getBytes(StandardCharsets.UTF_8));
        Entry entry = new BaseEntry(key, null);
        dao.upsert(entry);
        return new Response(
                String.valueOf(HttpURLConnection.HTTP_ACCEPTED),
                Response.EMPTY
        );
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_PUT)
    public Response handlePut(Request request) {
        String keyString = request.getParameter("id=");
        if (keyString == null) {
            return badRequest;
        }
        MemorySegment key = MemorySegment.ofArray(keyString.getBytes(StandardCharsets.UTF_8));
        MemorySegment value = MemorySegment.ofArray(request.getBody());
        Entry entry = new BaseEntry(key, value);
        dao.upsert(entry);
        return new Response(
                String.valueOf(HttpURLConnection.HTTP_CREATED),
                Response.EMPTY
        );
    }

    @Path("/v0/entity")
    @RequestMethod(Request.METHOD_POST)
    public Response handlePost() {
        return new Response(
                String.valueOf(HttpURLConnection.HTTP_BAD_METHOD),
                Response.EMPTY
        );
    }

    private static HttpServerConfig createConfig(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptorConfig = new AcceptorConfig();
        acceptorConfig.port = port;
        acceptorConfig.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptorConfig};
        return httpConfig;
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        return CompletableFuture.completedFuture(null);
    }

    @ServiceFactory(stage = 6, week = 5)
    public static class Factory implements ServiceFactory.Factory {

        @Override
        public Service create(ServiceConfig config) {
            try {
                return new DatabaseService(config);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Can't create a service");
                return null;
            }
        }
    }
}
