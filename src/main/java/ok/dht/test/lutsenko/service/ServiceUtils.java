package ok.dht.test.lutsenko.service;

import one.nio.http.HttpSession;
import one.nio.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ServiceUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceUtils.class);

    private ServiceUtils() {
    }

    public static void sendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (Exception e) {
            LOG.error("Service unavailable", e);
            try {
                session.sendResponse(new Response(Response.SERVICE_UNAVAILABLE, Response.EMPTY));
            } catch (Exception e1) {
                LOG.error("Failed send SERVICE_UNAVAILABLE response", e1);
                closeSession(session);
            }
        }
    }

    public static void closeSession(HttpSession session) {
        try {
            session.close();
        } catch (Exception e) {
            LOG.error("Failed close session", e);
        }
    }
}
