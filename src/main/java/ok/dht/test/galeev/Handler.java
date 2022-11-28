package ok.dht.test.galeev;

import ok.dht.test.galeev.dao.entry.Entry;
import ok.dht.test.shestakova.exceptions.MethodNotAllowedException;
import one.nio.http.Request;
import one.nio.http.Response;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public interface Handler<T> {
    String NOT_ENOUGH_REPLICAS = "504 Not Enough Replicas";

    CompletableFuture<Optional<T>> action(Node node, String key);

    Response responseOk();

    Response responseError();

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    void onSuccess(Optional<?> input);

    @SuppressWarnings("EmptyMethod")
    void onError();

    void finishResponse();

    static Handler<?> getHandler(Request request) {
        switch (request.getMethod()) {
            case Request.METHOD_GET -> {
                return new GetHandler(request);
            }
            case Request.METHOD_PUT -> {
                return new PutHandler(request);
            }
            case Request.METHOD_DELETE -> {
                return new DeleteHandler(request);
            }
            default -> throw new MethodNotAllowedException();
        }
    }

    class GetHandler implements Handler<Entry<Timestamp, byte[]>> {
        private final AtomicReference<Entry<Timestamp, byte[]>> newestEntry = new AtomicReference<>();
        private volatile boolean hasFinished;

        public GetHandler(@SuppressWarnings("unused") Request ignored) {
        }

        @Override
        public CompletableFuture<Optional<Entry<Timestamp, byte[]>>> action(Node node, String key) {
            if (hasFinished) {
                return CompletableFuture.completedFuture(null);
            }
            return node.get(key).thenApply(entry -> {
                if (entry == null) {
                    return Optional.empty();
                } else {
                    return Optional.of(entry);
                }
            });
        }

        @Override
        public Response responseOk() {
            Entry<Timestamp, byte[]> entry = newestEntry.get();

            if (entry.key() == null || entry.value() == null) {
                return new Response(Response.NOT_FOUND, Response.EMPTY);
            } else {
                return new Response(Response.OK, entry.value());
            }
        }

        @Override
        public Response responseError() {
            return new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY);
        }

        @Override
        public void onSuccess(Optional<?> input) {
            Entry<Timestamp, byte[]> entry = (Entry<Timestamp, byte[]>) input.get();
            updateNewestEntry(newestEntry, entry);
        }

        @Override
        public void onError() {

        }

        @Override
        public void finishResponse() {
            hasFinished = true;
        }

        private static void updateNewestEntry(
                AtomicReference<Entry<Timestamp, byte[]>> newestEntry,
                Entry<Timestamp, byte[]> entry) {
            // When NotFound entry is (null,null)
            if (entry.key() == null) {
                // If response was Not Found -> set entry only if there was nothing else.
                newestEntry.compareAndSet(null, entry);
            } else {
                boolean needToUpdate;
                Entry<Timestamp, byte[]> currentNewestEntry;
                do {
                    needToUpdate = false;
                    currentNewestEntry = newestEntry.get();
                    if (currentNewestEntry == null || currentNewestEntry.key() == null
                            || isFirstMoreActual(entry, currentNewestEntry)) {
                        // If there is absolutely no entry
                        // Or if there is NotFound entry (anything is better than this two)
                        // Or if we have more actual entry
                        needToUpdate = true;
                    }
                } while (needToUpdate && !newestEntry.compareAndSet(currentNewestEntry, entry));
            }
        }

        private static boolean isFirstMoreActual(Entry<Timestamp, byte[]> first,
                                                 Entry<Timestamp, byte[]> second) {
            return first.key().after(second.key())
                    || (first.key().equals(second.key()) && first.isTombstone());
        }
    }

    class PutHandler implements Handler<Boolean> {
        private final Request request;
        private final Timestamp currentTime;

        public PutHandler(Request request) {
            currentTime = new Timestamp(System.currentTimeMillis());
            this.request = request;
        }

        @Override
        public CompletableFuture<Optional<Boolean>> action(Node node, String key) {
            return node.put(key, currentTime, request.getBody()).thenApply((isOk) -> {
                if (isOk) {
                    return Optional.of(true);
                } else {
                    return Optional.empty();
                }
            });
        }

        @Override
        public Response responseOk() {
            return new Response(Response.CREATED, Response.EMPTY);
        }

        @Override
        public Response responseError() {
            return new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY);
        }

        @Override
        public void onSuccess(Optional<?> input) {
        }

        @Override
        public void onError() {

        }

        @Override
        public void finishResponse() {
        }
    }

    class DeleteHandler implements Handler<Boolean> {

        private final Timestamp currentTime;

        public DeleteHandler(@SuppressWarnings("unused") Request ignored) {
            currentTime = new Timestamp(System.currentTimeMillis());
        }

        @Override
        public CompletableFuture<Optional<Boolean>> action(Node node, String key) {
            return node.delete(key, currentTime).thenApply((isOk) -> {
                if (isOk) {
                    return Optional.of(true);
                } else {
                    return Optional.empty();
                }
            });
        }

        @Override
        public Response responseOk() {
            return new Response(Response.ACCEPTED, Response.EMPTY);
        }

        @Override
        public Response responseError() {
            return new Response(NOT_ENOUGH_REPLICAS, Response.EMPTY);
        }

        @Override
        public void onSuccess(Optional<?> input) {
        }

        @Override
        public void onError() {
        }

        @Override
        public void finishResponse() {
        }
    }
}
