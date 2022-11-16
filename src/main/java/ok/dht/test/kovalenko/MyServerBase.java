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
        sendEmptyResponseForCode(Response.BAD_REQUEST, session, log);
    }

    @Override
    public void handleRequest(Request request, HttpSession session) throws IOException {
        if (!MyServerBase.availableMethods.contains(request.getMethod())) {
            sendEmptyResponseForCode(Response.METHOD_NOT_ALLOWED, session, log);
            return;
        }

        HttpUtils.NetRequest netRequest = () -> super.handleRequest(request, session);
        HttpUtils.safeHttpRequest(session, log, netRequest);
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

    public static void sendEmptyResponseForCode(String statusCode, HttpSession session, Logger log) {
        MyHttpResponse response = new MyHttpResponse(statusCode);
        HttpUtils.NetRequest netRequest = () -> session.sendResponse(response);
        HttpUtils.safeHttpRequest(session, log, netRequest);
    }

}
