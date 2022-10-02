package ok.dht.test.kondraev;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class Service implements ok.dht.Service {
    @Override
    public CompletableFuture<?> start() throws IOException {
        return null;
    }

    @Override
    public CompletableFuture<?> stop() throws IOException {
        return null;
    }
}
