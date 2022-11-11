package ok.dht.test.kovalenko;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.kovalenko.dao.LSMDao;
import ok.dht.test.kovalenko.dao.aliases.TypedBaseTimedEntry;
import ok.dht.test.kovalenko.dao.aliases.TypedTimedEntry;
import ok.dht.test.kovalenko.dao.base.ByteBufferDaoFactoryB;
import ok.dht.test.kovalenko.dao.utils.PoolKeeper;
import ok.dht.test.kovalenko.utils.CompletableFutureSubscriber;
import ok.dht.test.kovalenko.utils.HttpUtils;
import ok.dht.test.kovalenko.utils.MyHttpResponse;
import ok.dht.test.kovalenko.utils.MyHttpSession;
import ok.dht.test.kovalenko.utils.ReplicasUtils;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Path;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.server.AcceptorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MyServiceBase implements Service {

    private static final Logger log = LoggerFactory.getLogger(MyServiceBase.class);
    private static final ByteBufferDaoFactoryB daoFactory = new ByteBufferDaoFactoryB();
    // One LoadBalancer per Service, one Service per Server
    private final LoadBalancer loadBalancer = new LoadBalancer();
    private final ServiceConfig config;
    private LSMDao dao;
    private HttpServer server;
    private final PoolKeeper poolKeeper;
    private final CompletableFutureSubscriber completableFutureSubscriber = new CompletableFutureSubscriber();

    public MyServiceBase(ServiceConfig config) throws IOException {
        this.config = config;
        this.poolKeeper = new PoolKeeper(
                new ThreadPoolExecutor(1, Integer.MAX_VALUE,
                        60, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(Integer.MAX_VALUE),
                        new ThreadPoolExecutor.AbortPolicy()),
                3*60);
    }

    @Override
    public CompletableFuture<?> start() {
        try {
            log.debug("Service {} is starting", selfUrl());
            this.dao = new LSMDao(this.config);
            this.server = new MyServerBase(createConfigFromPort(selfPort()));
            this.server.addRequestHandlers(this);
            loadBalancer.add(this);
            this.server.start();
            log.debug("Service {} has started", selfUrl());
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<?> stop() {
        try {
            log.debug("Service {} is stopping", selfUrl());
            server.stop();
            completableFutureSubscriber.close();
            loadBalancer.remove(this);
            dao.close();
            log.debug("Service {} has stopped", selfUrl());
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    // For outer calls
    @Path("/v0/entity")
    public void handle(Request request, HttpSession session)
            throws IOException, ExecutionException, InterruptedException {
        MyHttpSession myHttpSession = (MyHttpSession) session;
        ReplicasUtils.Replicas replicas = ReplicasUtils.recreate(myHttpSession.getReplicas(), clusterUrls().size());
        myHttpSession.setReplicas(replicas);
        if (request.getHeader(HttpUtils.REPLICA_HEADER) != null) {
            CompletableFuture<MyHttpResponse> cf = handle(request, myHttpSession);
            completableFutureSubscriber.subscribe(cf, myHttpSession, loadBalancer, selfUrl());
        } else {
            loadBalancer.balance(this, request, myHttpSession);
        }
    }

    // For inner calls, returns fictive CompletableFuture for compliance with the MyServiceBase.Handler contract
    public CompletableFuture<MyHttpResponse> handle(Request request, MyHttpSession session)
            throws IOException {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return switch (request.getMethod()) {
                    case Request.METHOD_GET -> handleGet(request, session);
                    case Request.METHOD_PUT -> handlePut(request, session);
                    case Request.METHOD_DELETE -> handleDelete(request, session);
                    default -> throw new IllegalArgumentException("Illegal request method: " + request.getMethod());
                };
            } catch (Exception e) {
                return new MyHttpResponse(Response.SERVICE_UNAVAILABLE, e);
            }
        }, poolKeeper.getService());
    }

    public MyHttpResponse handleGet(Request request, MyHttpSession session) throws IOException {
        ByteBuffer key = daoFactory.fromString(session.getRequestId());
        TypedTimedEntry found = this.dao.get(key);
        return found == null
                ? emptyResponseFor(Response.NOT_FOUND)
                : found.isTombstone()
                ? emptyResponseFor(Response.NOT_FOUND, found.timestamp())
                : new MyHttpResponse(Response.OK, daoFactory.toBytes(found.value()), found.timestamp());
    }

    public MyHttpResponse handlePut(Request request, MyHttpSession session) throws IOException {
        ByteBuffer key = daoFactory.fromString(session.getRequestId());
        ByteBuffer value = ByteBuffer.wrap(request.getBody());
        this.dao.upsert(entryFor(key, value));
        return emptyResponseFor(Response.CREATED);
    }

    public MyHttpResponse handleDelete(Request request, MyHttpSession session) throws IOException {
        ByteBuffer key = daoFactory.fromString(session.getRequestId());
        this.dao.upsert(entryFor(key, null));
        return emptyResponseFor(Response.ACCEPTED);
    }

    public static MyHttpResponse emptyResponseFor(String statusCode, long timestamp) {
        return new MyHttpResponse(statusCode, timestamp);
    }

    public static MyHttpResponse emptyResponseFor(String statusCode) {
        return emptyResponseFor(statusCode, 0);
    }

    private static TypedTimedEntry entryFor(ByteBuffer key, ByteBuffer value) {
        return new TypedBaseTimedEntry(System.currentTimeMillis(), key, value);
    }

    public String selfUrl() {
        return this.config.selfUrl();
    }

    public int selfPort() {
        return this.config.selfPort();
    }

    public List<String> clusterUrls() {
        return this.config.clusterUrls();
    }

    public LSMDao getDao() {
        return this.dao;
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    private static CompletableFuture<MyHttpResponse> completableFutureFor(MyHttpResponse r) {
        return CompletableFuture.completedFuture(r);
    }

    public interface Handler {
        CompletableFuture<?> handle(Request request, MyHttpSession session)
                throws IOException, ExecutionException,
                InterruptedException, IllegalAccessException, InvocationTargetException;
    }
}
