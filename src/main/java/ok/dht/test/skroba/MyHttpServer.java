package ok.dht.test.skroba;

import one.nio.http.HttpServer;
import one.nio.http.HttpServerConfig;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.Session;
import one.nio.server.SelectorThread;

import java.io.IOException;

public class MyHttpServer extends HttpServer {
    
    public MyHttpServer(HttpServerConfig config, Object... routers) throws IOException {
        super(config, routers);
    }
    
    @Override
    public void handleDefault(
            Request request,
            HttpSession session
    ) throws IOException {
        session.sendResponse(MyServiceUtils.getEmptyResponse(Response.BAD_REQUEST));
    }
    
    @Override
    public synchronized void stop() {
        for (SelectorThread thread : selectors) {
            thread.selector.forEach(Session::close);
        }
        
        super.stop();
    }
}
