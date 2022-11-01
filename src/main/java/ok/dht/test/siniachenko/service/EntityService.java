package ok.dht.test.siniachenko.service;

import one.nio.http.Request;
import one.nio.http.Response;

public interface EntityService {
    Response handleGet(Request request, String id);

    Response handlePut(Request request, String id);

    Response handleDelete(Request request, String id);
}
