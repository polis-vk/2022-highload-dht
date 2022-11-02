package ok.dht.test.kovalenko;

import ok.dht.test.kovalenko.dao.utils.PoolKeeper;
import ok.dht.test.kovalenko.utils.HttpUtils;
import ok.dht.test.kovalenko.utils.ReplicasUtils;
import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.net.Socket;
import one.nio.server.RejectedSessionException;
import one.nio.server.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MyServerBase extends HttpServer {
    private static final Set<Integer /*HTTP-method id*/> availableMethods
            = Set.of(Request.METHOD_GET, Request.METHOD_PUT, Request.METHOD_DELETE);
    private static final int N_WORKERS = 2 * (Runtime.getRuntime().availableProcessors() + 1);
    private static final int QUEUE_CAPACITY = 10 * N_WORKERS;
    private final Logger log = LoggerFactory.getLogger(MyServerBase.class);
    private final PoolKeeper workers;

    public MyServerBase(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
        this.workers = new PoolKeeper(
                new ThreadPoolExecutor(1, N_WORKERS,
                        60, TimeUnit.SECONDS,
                        new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                        new ThreadPoolExecutor.AbortPolicy()),
                3 * 60);
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        Response response = MyServiceBase.emptyResponseFor(Response.BAD_REQUEST);
        session.sendResponse(response);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!MyServerBase.availableMethods.contains(request.getMethod())) {
            Response response = MyServiceBase.emptyResponseFor(Response.METHOD_NOT_ALLOWED);
            session.sendResponse(response);
            return;
        }

        String id = request.getParameter("id=");
        if (id == null || id.isEmpty()) {
            Response response = MyServiceBase.emptyResponseFor(Response.BAD_REQUEST);
            session.sendResponse(response);
            return;
        }

        String replicas = request.getParameter("replicas=");
        ReplicasUtils.ReplicasValidation replicasValidation = ReplicasUtils.validate(replicas);
        if (!replicasValidation.valid()) {
            Response response = MyServiceBase.emptyResponseFor(Response.BAD_REQUEST);
            session.sendResponse(response);
            return;
        }

        MyHttpSession myHttpSession = (MyHttpSession) session;
        myHttpSession.setRequestId(id);
        myHttpSession.setReplicas(replicasValidation.replicas());
        HttpUtils.NetRequest netRequest = () -> handle(request, myHttpSession);
        HttpUtils.safeHttpRequest(myHttpSession, log, netRequest);
    }

    @Override
    public synchronized void stop() {
        workers.close();
        for (SelectorThread selectorThread : selectors) {
            if (selectorThread.selector.isOpen()) {
                for (Session session : selectorThread.selector) {
                    session.close();
                }
            }
        }
        super.stop();
    }

    @Override
    public HttpSession createSession(Socket socket) throws RejectedSessionException {
        return new MyHttpSession(socket, this);
    }

    private void handle(Request request, MyHttpSession myHttpSession) {
        HttpUtils.NetRequest netRequest = () -> super.handleRequest(request, myHttpSession);
        workers.submit(() -> HttpUtils.safeHttpRequest(myHttpSession, log, netRequest));
    }

}
