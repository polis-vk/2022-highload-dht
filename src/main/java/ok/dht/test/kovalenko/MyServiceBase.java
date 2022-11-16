package ok.dht.test.kovalenko;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.kovalenko.dao.LSMDao;
import ok.dht.test.kovalenko.dao.aliases.TypedBaseTimedEntry;
import ok.dht.test.kovalenko.dao.aliases.TypedIterator;
import ok.dht.test.kovalenko.dao.aliases.TypedTimedEntry;
import ok.dht.test.kovalenko.dao.base.ByteBufferDaoFactoryB;
import ok.dht.test.kovalenko.dao.utils.DaoUtils;
import ok.dht.test.kovalenko.utils.CompletableFutureSubscriber;
import ok.dht.test.kovalenko.utils.CompletableFutureUtils;
import ok.dht.test.kovalenko.utils.HttpUtils;
import ok.dht.test.kovalenko.utils.MyHttpResponse;
import ok.dht.test.kovalenko.utils.MyHttpSession;
import ok.dht.test.pashchenko.MyServer;
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
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class MyServiceBase implements Service {

    private static final Logger log = LoggerFactory.getLogger(MyServiceBase.class);
    // One LoadBalancer per Service, one Service per Server
    private final LoadBalancer loadBalancer = new LoadBalancer();
    private final ServiceConfig config;
    private LSMDao dao;
    private HttpServer server;
    private final CompletableFutureSubscriber completableFutureSubscriber = new CompletableFutureSubscriber();

    public MyServiceBase(ServiceConfig config) throws IOException {
        this.config = config;
    }

    @Override
    public CompletableFuture<?> start() {
        try {
            log.debug("Service {} is starting", selfUrl());
            dao = new LSMDao(config);
            server = new MyServerBase(createConfigFromPort(selfPort()));
            server.addRequestHandlers(this);
            loadBalancer.add(this);
            HttpUtils.initPoolKeeper();
            server.start();
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
    public void handleEntity(Request request, HttpSession session)
            throws IOException, ExecutionException, InterruptedException {
        MyHttpSession myHttpSession = (MyHttpSession) session;
        HttpUtils.IdValidation idValidation = checkForId(request, myHttpSession);
        HttpUtils.ReplicasValidation replicasValidation = checkForReplicas(request, myHttpSession);

        if (!idValidation.valid() || !replicasValidation.valid()) {
            log.error("Invalid parameters: id = {}, replicas = {}", idValidation.id(), replicasValidation.replicas());
            MyServerBase.sendEmptyResponseForCode(Response.BAD_REQUEST, myHttpSession, log);
            return;
        }

        myHttpSession.setRequestId(idValidation.id());
        HttpUtils.Replicas replicas = HttpUtils.recreateReplicas(replicasValidation.replicas(), clusterUrls().size());
        myHttpSession.setReplicas(replicas);

        if (request.getHeader(HttpUtils.REPLICA_HEADER) == null) {
            loadBalancer.balance(this, request, myHttpSession);
            return;
        }

        CompletableFuture<MyHttpResponse> cf = handleEntity(request, myHttpSession);
        CompletableFutureUtils.Subscription subscription =
                new CompletableFutureUtils.Subscription(cf, myHttpSession, loadBalancer, selfUrl());
        completableFutureSubscriber.subscribe(subscription);
    }

    @Path("/v0/entities")
    public void handleEntities(Request request, HttpSession session) throws IOException {
        MyHttpSession myHttpSession = (MyHttpSession) session;
        HttpUtils.RangeValidation rangeValidation = checkForRange(request, myHttpSession);

        if (!rangeValidation.valid()) {
            log.error("Invalid parameter: range = {}", rangeValidation.range());
            MyServerBase.sendEmptyResponseForCode(Response.BAD_REQUEST, myHttpSession, log);
            return;
        }

        myHttpSession.setRange(rangeValidation.range());
        Iterator<TypedTimedEntry> mergeIterator = handleRangeGet(request, myHttpSession);
        myHttpSession.setMergeIterator(mergeIterator);

        Response response = new MyHttpResponse.ChunkedResponse(Response.OK);
        response.addHeader("Transfer-Encoding: chunked");
        HttpUtils.NetRequest netRequest = () -> myHttpSession.sendResponse(response);
        HttpUtils.safeHttpRequest(session, log, netRequest);
    }

    // For inner calls
    public CompletableFuture<MyHttpResponse> handleEntity(Request request, MyHttpSession session)
            throws IOException {
        return switch (request.getMethod()) {
            case Request.METHOD_GET -> handleGet(request, session);
            case Request.METHOD_PUT -> handlePut(request, session);
            case Request.METHOD_DELETE -> handleDelete(request, session);
            default -> throw new IllegalArgumentException("Illegal request method: " + request.getMethod());
        };
    }

    public CompletableFuture<MyHttpResponse> handleGet(Request request, MyHttpSession session) throws IOException {
        CompletableFuture<Void> start = new CompletableFuture<>();
        CompletableFuture<MyHttpResponse> end = start
                .thenCompose(nullable -> {
                    try {
                        ByteBuffer key = DaoUtils.DAO_FACTORY.fromString(session.getRequestId());
                        TypedTimedEntry found = dao.get(key);
                        return found == null
                                ? emptyResponseFor(Response.NOT_FOUND)
                                : found.isTombstone()
                                ? emptyResponseFor(Response.NOT_FOUND, found.timestamp())
                                : completedFutureFor(new MyHttpResponse(
                                        Response.OK,
                                        DaoUtils.DAO_FACTORY.toBytes(found.value()),
                                        found.timestamp())
                                );
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .exceptionally(this::whenExceptionally);

        start.completeAsync(() -> null, HttpUtils.getService());
        return end;
    }

    public Iterator<TypedTimedEntry> handleRangeGet(Request request, MyHttpSession session) throws IOException {
        HttpUtils.Range range = session.getRange();
        ByteBuffer from = DaoUtils.DAO_FACTORY.fromString(range.start());
        ByteBuffer to = DaoUtils.DAO_FACTORY.fromString(range.end());
        return dao.get(from, to);
    }

    public CompletableFuture<MyHttpResponse> handlePut(Request request, MyHttpSession session) throws IOException {
        CompletableFuture<Void> start = new CompletableFuture<>();
        CompletableFuture<MyHttpResponse> end = start
                .thenCompose(nullable -> {
                    TypedTimedEntry entry = entryFor(
                            DaoUtils.DAO_FACTORY.fromString(session.getRequestId()),
                            ByteBuffer.wrap(request.getBody())
                    );
                    this.dao.upsert(entry);
                    return emptyResponseFor(Response.CREATED);
                })
                .exceptionally(this::whenExceptionally);

        start.completeAsync(() -> null, HttpUtils.getService());
        return end;
    }

    public CompletableFuture<MyHttpResponse> handleDelete(Request request, MyHttpSession session) throws IOException {
        CompletableFuture<Void> start = new CompletableFuture<>();
        CompletableFuture<MyHttpResponse> end = start
                .thenCompose(nullable -> {
                    TypedTimedEntry entry = entryFor(
                            DaoUtils.DAO_FACTORY.fromString(session.getRequestId()),
                            null
                    );
                    this.dao.upsert(entry);
                    return emptyResponseFor(Response.ACCEPTED);
                })
                .exceptionally(this::whenExceptionally);

        start.completeAsync(() -> null, HttpUtils.getService());
        return end;
    }

    public static CompletableFuture<MyHttpResponse> emptyResponseFor(String statusCode, long timestamp) {
        return completedFutureFor(new MyHttpResponse(statusCode, timestamp));
    }

    public static CompletableFuture<MyHttpResponse> emptyResponseFor(String statusCode) {
        return emptyResponseFor(statusCode, 0);
    }

    private MyHttpResponse whenExceptionally(Throwable t) {
        return new MyHttpResponse(Response.SERVICE_UNAVAILABLE, t);
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

    private HttpUtils.IdValidation checkForId(Request request, MyHttpSession session) {
        String id = request.getParameter("id=");
        return HttpUtils.validateId(id);
    }

    private HttpUtils.ReplicasValidation checkForReplicas(Request request, MyHttpSession session) {
        String ack = request.getParameter("ack=");
        String from = request.getParameter("from=");
        return HttpUtils.validateReplicas(ack, from);
    }

    private HttpUtils.RangeValidation checkForRange(Request request, MyHttpSession session) {
        String start = request.getParameter("start=");
        String end = request.getParameter("end=");
        return HttpUtils.validateRange(start, end);
    }

    private static CompletableFuture<MyHttpResponse> completedFutureFor(MyHttpResponse r) {
        return CompletableFuture.completedFuture(r);
    }

    public interface Handler {
        CompletableFuture<?> handle(Request request, MyHttpSession session)
                throws IOException, ExecutionException,
                InterruptedException, IllegalAccessException, InvocationTargetException;
    }
}
