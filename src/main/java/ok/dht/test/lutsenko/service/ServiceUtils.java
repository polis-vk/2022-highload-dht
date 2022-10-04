package ok.dht.test.lutsenko.service;

import one.nio.http.HttpSession;
import one.nio.http.Response;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class ServiceUtils {

    private ServiceUtils() {
    }

    public static void uncheckedSendResponse(HttpSession session, Response response) {
        try {
            session.sendResponse(response);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
