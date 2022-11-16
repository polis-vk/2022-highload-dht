package ok.dht.test.vihnin.utils;

import ok.dht.test.vihnin.ResponseAccumulator;
import one.nio.http.HttpSession;
import one.nio.http.Request;
import one.nio.http.Response;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Optional;

import static ok.dht.test.vihnin.ParallelHttpServer.TIME_HEADER_NAME;
import static ok.dht.test.vihnin.ParallelHttpServer.handleException;
import static ok.dht.test.vihnin.utils.ServiceUtils.emptyResponse;

public final class ServerUtils {
    private ServerUtils() {

    }

    public static HttpResponse<byte[]> handleSingleAcknowledgment(
            ResponseAccumulator responseAccumulator,
            HttpResponse<byte[]> httpResponse,
            Throwable throwable) {
        if (throwable == null) {
            if (httpResponse == null) {
                responseAccumulator.acknowledgeFailed();
            } else {
                Optional<String> time = httpResponse.headers()
                        .firstValue(TIME_HEADER_NAME);
                if (time.isPresent()) {
                    responseAccumulator.acknowledgeSucceed(
                            Long.parseLong(time.get()),
                            httpResponse.statusCode(),
                            httpResponse.body()
                    );
                } else {
                    responseAccumulator.acknowledgeFailed();
                }
            }
            return httpResponse;
        } else {
            responseAccumulator.acknowledgeMissed();
            return null;
        }
    }

    public static boolean methodAllowed(int method) {
        return method == Request.METHOD_GET
                || method == Request.METHOD_DELETE
                || method == Request.METHOD_PUT;
    }

    static void processAcknowledgment(
            int method,
            HttpSession session,
            boolean reachAckNumber,
            int freshestStatus,
            byte[] freshestData) {
        try {
            if (reachAckNumber) {
                if (method == Request.METHOD_DELETE) {
                    session.sendResponse(emptyResponse("202 Accepted"));
                } else if (method == Request.METHOD_PUT) {
                    session.sendResponse(emptyResponse("201 Created"));
                } else if (method == Request.METHOD_GET) {
                    if (freshestStatus == 200) {
                        session.sendResponse(new Response("200 OK", freshestData));
                    } else {
                        session.sendResponse(emptyResponse("404 Not Found"));
                    }
                }
            } else {
                session.sendResponse(emptyResponse("504 Not Enough Replicas"));
            }
        } catch (IOException e) {
            handleException(session, e);
        }
    }
}
