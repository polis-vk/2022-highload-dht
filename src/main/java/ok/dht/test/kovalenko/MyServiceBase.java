package ok.dht.test.kovalenko;

import ok.dht.Service;
import ok.dht.ServiceConfig;
import ok.dht.test.kovalenko.dao.LSMDao;
import ok.dht.test.kovalenko.dao.aliases.TypedBaseEntry;
import ok.dht.test.kovalenko.dao.aliases.TypedBaseTimedEntry;
import ok.dht.test.kovalenko.dao.aliases.TypedTimedEntry;
import ok.dht.test.kovalenko.dao.base.ByteBufferDaoFactoryB;
import ok.dht.test.kovalenko.utils.HttpUtils;
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
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class MyServiceBase implements Service {

    private static final ByteBufferDaoFactoryB daoFactory = new ByteBufferDaoFactoryB();
    private static final Logger log = LoggerFactory.getLogger(MyServiceBase.class);
    private static final Method clientGet;
    private static final Method clientPut;
    private static final Method clientDelete;
    private static final Method serviceHandleGet;
    private static final Method serviceHandlePut;
    private static final Method serviceHandleDelete;
    // One LoadBalancer per Service, one Service per Server
    private final LoadBalancer loadBalancer = new LoadBalancer();
    private final ServiceConfig config;
    private LSMDao dao;
    private HttpServer server;

    static {
        try {
            Class<Client> clientClass = Client.class;
            clientGet = clientClass.getMethod("get", String.class, byte[].class, MyHttpSession.class);
            clientPut = clientClass.getMethod("put", String.class, byte[].class, MyHttpSession.class);
            clientDelete = clientClass.getMethod("delete", String.class, byte[].class, MyHttpSession.class);

            Class<MyServiceBase> serviceClass = MyServiceBase.class;
            serviceHandleGet = serviceClass.getMethod("handleGet", String.class, Request.class, MyHttpSession.class);
            serviceHandlePut = serviceClass.getMethod("handlePut", String.class, Request.class, MyHttpSession.class);
            serviceHandleDelete = serviceClass.getMethod("handleDelete", String.class, Request.class, MyHttpSession.class);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

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

    public static Response emptyResponseFor(String code) {
        return new Response(code, Response.EMPTY);
    }

    @Override
    public CompletableFuture<?> start() {
        try {
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
            this.server.stop();
            this.dao.close();
            loadBalancer.remove(this);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Path("/v0/entity")
    public void handle(Request request, HttpSession session)
            throws IOException, ExecutionException, InterruptedException {
        MyHttpSession myHttpSession = (MyHttpSession) session;
        ReplicasUtils.Replicas replicas = ReplicasUtils.recreate(myHttpSession.getReplicas(), clusterUrls().size());
        myHttpSession.setReplicas(replicas);
        loadBalancer.balance(this, myHttpSession.getRequestId(), request, myHttpSession);
    }

    public Response handle(String id, Request request, MyHttpSession session)
            throws IOException, InvocationTargetException, IllegalAccessException {
        int ack = session.getReplicas().ack();

        return switch(request.getMethod()) {
            case Request.METHOD_GET
                    -> handleReplicas(serviceHandleGet, clientGet, HttpURLConnection.HTTP_OK, ack, id, request, session);
            case Request.METHOD_PUT
                    -> handleReplicas(serviceHandlePut, clientPut, HttpURLConnection.HTTP_CREATED, ack, id, request, session);
            case Request.METHOD_DELETE
                    -> handleReplicas(serviceHandleDelete, clientDelete, HttpURLConnection.HTTP_ACCEPTED, ack, id, request, session);
            default
                    -> throw new IllegalArgumentException("Illegal request method");
        };
    }

    private Response handleReplicas(Method selfHandler, Method clientHandler, int expectingResponseStatusCode, int ack,
                                    String id, Request request, MyHttpSession session)
            throws InvocationTargetException, IllegalAccessException {
        Response masterNodeResponse = (Response) selfHandler.invoke(this, id, request, session);
        if (masterNodeResponse.getStatus() != expectingResponseStatusCode) {
            return masterNodeResponse; // transfer error up the call hierarchy
        }
        int nApproves = 0;
        for (int i = 0; i < clusterUrls().size() && nApproves != ack; ++i) { // FIXME replicas order
            String nodeUrl = clusterUrls().get(i);
            if (!nodeUrl.equals(selfUrl())) {
                HttpResponse<byte[]> replicaResponse = (HttpResponse<byte[]>) clientHandler.invoke(HttpUtils.CLIENT, id, request, session);
                if (replicaResponse.statusCode() == expectingResponseStatusCode) {
                    ++nApproves;
                } else {
                    loadBalancer.makeNodeIll(nodeUrl);
                }
            }
        }
        return nApproves == ack
                ? emptyResponseFor(HttpUtils.toOneNio(expectingResponseStatusCode))
                : emptyResponseFor(HttpUtils.NOT_ENOUGH_REPLICAS);
    }

    public Response handleGet(String id, Request request, MyHttpSession session) throws IOException {
        ByteBuffer key = daoFactory.fromString(id);
        TypedBaseEntry found = TypedBaseTimedEntry.withoutTime(this.dao.get(key));
        return found == null
                ? emptyResponseFor(Response.NOT_FOUND)
                : Response.ok(daoFactory.toBytes(found.value()));
    }

    public Response handlePut(String id, Request request, MyHttpSession session) throws IOException {
        ByteBuffer key = daoFactory.fromString(id);
        ByteBuffer value = ByteBuffer.wrap(request.getBody());
        this.dao.upsert(entryFor(key, value));
        return emptyResponseFor(Response.CREATED);
    }

    public Response handleDelete(String id, Request request, MyHttpSession session) throws IOException {
        ByteBuffer key = daoFactory.fromString(id);
        this.dao.upsert(entryFor(key, null));
        return emptyResponseFor(Response.ACCEPTED);
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

    public interface Handler {
        Object handle(String id, Request request, MyHttpSession session)
                throws IOException, ExecutionException, InterruptedException, IllegalAccessException, InvocationTargetException;
    }
}
