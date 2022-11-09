package ok.dht.test.kovalenko;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.kovalenko.dao.LSMDao;
import ok.dht.test.kovalenko.dao.aliases.TypedBaseTimedEntry;
import ok.dht.test.kovalenko.dao.aliases.TypedTimedEntry;
import ok.dht.test.kovalenko.dao.base.ByteBufferDaoFactoryB;
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
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class MyServiceBase implements Service {

    private static final Logger log = LoggerFactory.getLogger(MyServiceBase.class);
    private static final ByteBufferDaoFactoryB daoFactory = new ByteBufferDaoFactoryB();
    // One LoadBalancer per Service, one Service per Server
    private final LoadBalancer loadBalancer = new LoadBalancer();
    private final ServiceConfig config;
    private LSMDao dao;
    private HttpServer server;

    public MyServiceBase(ServiceConfig config) throws IOException {
        this.config = config;
    }

    private static HttpServerConfig createConfigFromPort(int port) {
        HttpServerConfig httpConfig = new HttpServerConfig();
        AcceptorConfig acceptor = new AcceptorConfig();
        acceptor.port = port;
        acceptor.reusePort = true;
        httpConfig.acceptors = new AcceptorConfig[]{acceptor};
        return httpConfig;
    }

    public static MyHttpResponse emptyResponseFor(String statusCode, long timestamp) {
        return new MyHttpResponse(statusCode, timestamp);
    }

    public static MyHttpResponse emptyResponseFor(String statusCode) {
        return new MyHttpResponse(statusCode, 0);
    }

    private static TypedTimedEntry entryFor(ByteBuffer key, ByteBuffer value) {
        return new TypedBaseTimedEntry(System.currentTimeMillis(), key, value);
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
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<?> stop() {
        try {
            log.debug("Service {} is stopping", selfUrl());
            this.server.stop();
            this.dao.close();
            loadBalancer.remove(this);
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
            MyHttpResponse response = handle(request, myHttpSession);
            session.sendResponse(response);
        } else {
            loadBalancer.balance(this, request, myHttpSession);
        }
    }

    // For inner calls
    public MyHttpResponse handle(Request request, MyHttpSession session)
            throws IOException {
        return switch (request.getMethod()) {
            case Request.METHOD_GET -> handleGet(request, session);
            case Request.METHOD_PUT -> handlePut(request, session);
            case Request.METHOD_DELETE -> handleDelete(request, session);
            default ->
                    throw new IllegalArgumentException("Illegal request method: " + request.getMethod());
        };
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

    public interface Handler {
        MyHttpResponse handle(Request request, MyHttpSession session)
                throws IOException, ExecutionException,
                InterruptedException, IllegalAccessException, InvocationTargetException;
    }
}

