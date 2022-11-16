package ok.dht.test.frolovm;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.ServiceFactory;
import ok.dht.test.frolovm.hasher.Hasher;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Param;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import one.nio.util.Hash;
import one.nio.util.Utf8;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ServiceImpl implements Service {

    private static final String PATH_ENTITY = "/v0/entity";

    private static final String PATH_RANGE = "/v0/entities";
    private static final String PARAM_ID_NAME = "id=";

    private static final String PARAM_ACK_NAME = "ack";

    private static final String PARAM_FROM_NAME = "from";

    private static final String PARAM_START = "start";

    private static final String PARAM_END = "end";

    private static final int MAX_REQUEST_TRIES = 100;

    private static final int CORE_POLL_SIZE = 2;
    private static final int KEEP_ALIVE_TIME = 0;
    private static final int QUEUE_CAPACITY = 1024;

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceImpl.class);
    private final ServiceConfig config;
    private final ShardingAlgorithm algorithm;
    private HttpClient client;
    private ReplicationManager replicationManager;
    private HttpServerImpl server;

    private ExecutorService clientExecutor;

    private DB dao;

    public ServiceImpl(ServiceConfig config) {
        this(config, Hash::murmur3);
    }

    public ServiceImpl(ServiceConfig config, Hasher hasher) {
        this.config = config;
        this.algorithm = new RendezvousHashing(config.clusterUrls(), hasher);
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = createAcceptorConfig(port);
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    private static AcceptorConfig createAcceptorConfig(int port) {
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        return acceptor;
    }

    private void createDao() throws IOException {
        this.dao = Iq80DBFactory.factory.open(config.workingDir().toFile(), new Options());
    }

    @Override
    public CompletableFuture<?> start() throws IOException {
        if (dao == null) {
            createDao();
        }

        server = new HttpServerImpl(createConfigFromPort(config.selfPort()));
        server.addRequestHandlers(this);
        server.start();
        clientExecutor = new ThreadPoolExecutor(
                CORE_POLL_SIZE,
                CORE_POLL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(QUEUE_CAPACITY)
        );
        this.client = HttpClient.newBuilder().executor(clientExecutor).build();
        this.replicationManager = new ReplicationManager(
                algorithm,
                config.selfUrl(),
                new RequestExecutor(dao),
                new CircuitBreakerImpl(MAX_REQUEST_TRIES, config.clusterUrls()),
                client
        );
        return CompletableFuture.completedFuture(null);
    }

    @Path(PATH_ENTITY)
    public void entityHandler(@Param(PARAM_ID_NAME) String id, Request request, HttpSession session,
                              @Param(PARAM_ACK_NAME) String ackParam, @Param(PARAM_FROM_NAME) String fromParam) {
        if (!Utils.checkId(id)) {
            Utils.sendResponse(session, new Response(Response.BAD_REQUEST, Utf8.toBytes(Utils.BAD_ID)));
            return;
        }
        int ackNum;
        int from;

        if (fromParam == null || ackParam == null) {
            from = algorithm.getShards().size();
            ackNum = from / 2 + 1;
        } else {
            from = Integer.parseInt(fromParam);
            ackNum = Integer.parseInt(ackParam);
        }

        if (validateAcks(ackParam, fromParam, ackNum, from)) {
            Utils.sendResponse(session, Utils.emptyResponse(Response.BAD_REQUEST));
            return;
        }

        switch (request.getMethod()) {
            case Request.METHOD_PUT, Request.METHOD_GET, Request.METHOD_DELETE -> {
                replicationManager.handle(id, request, session, ackNum, from);
            }
            default -> {
                LOGGER.error("Method is not allowed: " + request.getMethod());
                Utils.sendResponse(session, new Response(Response.METHOD_NOT_ALLOWED,
                        Utf8.toBytes(Utils.NO_SUCH_METHOD)));
            }
        }
    }

    @Path(PATH_RANGE)
    public void rangeHandler(@Param(PARAM_START) String start, @Param(PARAM_END) String end,
                                                                Request request, HttpSession session) {
        if (!Utils.checkId(start)) {
            Utils.sendResponse(session, new Response(Response.BAD_REQUEST, Utf8.toBytes(Utils.BAD_ID)));
            return;
        }

        if (request.getMethod() == Request.METHOD_GET) {
            replicationManager.handleRange(start, end, session);
        } else {
            LOGGER.error("Method is not allowed for range request: " + request.getMethod());
            Utils.sendResponse(session, new Response(Response.METHOD_NOT_ALLOWED,
                    Utf8.toBytes(Utils.NO_SUCH_METHOD)));
        }
    }

    private boolean validateAcks(String ackParam, String fromParam, int ackNum, int from) {
        return ackNum <= 0 || from <= 0 || from > config.clusterUrls().size() || ackNum > from
                || (ackParam != null && fromParam == null) || (ackParam == null && fromParam != null);
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        if (server != null) {
            server.close();
            server = null;
        }
        replicationManager = null;
        Utils.closeExecutorPool(clientExecutor);
        if (dao != null) {
            dao.close();
        }
        dao = null;
        return CompletableFuture.completedFuture(null);
    }

    @ServiceFactory(stage = 6, week = 1, bonuses = "SingleNodeTest#respectFileFolder")
    public static class Factory implements ServiceFactory.Factory {
        @Override
        public Service create(ServiceConfig config) {
            return new ServiceImpl(config);
        }
    }
}
