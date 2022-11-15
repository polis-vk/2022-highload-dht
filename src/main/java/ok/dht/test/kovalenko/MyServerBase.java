package ok.dht.test.kovalenko;

import ok.dht.test.kovalenko.utils.HttpUtils;
import ok.dht.test.kovalenko.utils.MyHttpResponse;
import ok.dht.test.kovalenko.utils.MyHttpSession;
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

public class MyServerBase extends HttpServer {

    private static final Set<Integer /*HTTP-method id*/> availableMethods
            = Set.of(Request.METHOD_GET, Request.METHOD_PUT, Request.METHOD_DELETE);
    private final Logger log = LoggerFactory.getLogger(MyServerBase.class);

    public MyServerBase(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }

    @Override
    public void handleDefault(Request request, HttpSession session) throws IOException {
        sendEmptyResponseForCode(session, Response.BAD_REQUEST);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!MyServerBase.availableMethods.contains(request.getMethod())) {
            sendEmptyResponseForCode(session, Response.METHOD_NOT_ALLOWED);
            return;
        }

        MyHttpSession myHttpSession = (MyHttpSession) session;
        boolean hasId = checkForId(request, myHttpSession);
        boolean hasReplicas = checkForReplicas(request, myHttpSession);
        boolean hasRange = checkForRange(request, myHttpSession);
        if (!hasReplicas || (hasId && hasRange) || (!hasId && !hasRange)) {
            sendEmptyResponseForCode(session, Response.BAD_REQUEST);
            return;
        }

        HttpUtils.NetRequest netRequest = () -> super.handleRequest(request, myHttpSession);
        HttpUtils.safeHttpRequest(myHttpSession, log, netRequest);
    }

    @Override
    public synchronized void stop() {
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

    private boolean checkForId(Request request, MyHttpSession session) {
        String id = request.getParameter("id=");
        HttpUtils.IdValidation idValidation = HttpUtils.validateId(id);
        if (!idValidation.valid()) {
            log.error("Error when validating 'id' parameter: {}", id);
            return false;
        } else {
            session.setRequestId(id);
            return true;
        }
    }

    private boolean checkForReplicas(Request request, MyHttpSession session) {
        String ack = request.getParameter("ack=");
        String from = request.getParameter("from=");
        HttpUtils.ReplicasValidation replicasValidation = HttpUtils.validateReplicas(ack, from);
        if (!replicasValidation.valid()) {
            log.error("Error when validating 'replicas' parameter: {}", replicasValidation.replicas());
            return false;
        } else {
            session.setReplicas(replicasValidation.replicas());
            return true;
        }
    }

    private boolean checkForRange(Request request, MyHttpSession session) {
        String start = request.getParameter("start=");
        String end = request.getParameter("end=");
        HttpUtils.RangeValidation rangeValidation = HttpUtils.validateRange(start, end);
        if (!rangeValidation.valid()) {
            log.error("Error when validating 'range' parameter: {}", rangeValidation.range());
            return false;
        } else {
            session.setRange(rangeValidation.range());
            return true;
        }
    }

    private void sendEmptyResponseForCode(HttpSession session, String statusCode) {
        MyHttpResponse response = new MyHttpResponse(statusCode);
        HttpUtils.NetRequest netRequest = () -> session.sendResponse(response);
        HttpUtils.safeHttpRequest(session, log, netRequest);
    }

}
