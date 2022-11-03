package ok.dht.test.siniachenko.service;

import one.nio.http.Request;
import one.nio.http.Response;

import java.util.concurrent.CompletableFuture;

public interface AsyncEntityService {
    CompletableFuture<Response> handleGet(Request request, String id);

    CompletableFuture<Response> handlePut(Request request, String id);

    CompletableFuture<Response> handleDelete(Request request, String id);
}
