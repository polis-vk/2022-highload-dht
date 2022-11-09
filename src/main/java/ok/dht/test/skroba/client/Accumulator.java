package ok.dht.test.skroba.client;

import ok.dht.test.skroba.db.base.Entity;
import ok.dht.test.skroba.shard.Manager;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class Accumulator {
    private final Manager manager;
    private final HttpClient client;
    
    private final Instant timestamp = Instant.now();
    private final AtomicInteger ok = new AtomicInteger(0);
    private final AtomicInteger handled = new AtomicInteger(0);
    private final AtomicReference<Entity> result = new AtomicReference<>();
    private final Map<Integer, CompletableFuture<HttpResponse<byte[]>>> futures = new ConcurrentHashMap<>();
    
    public Accumulator(final Manager manager, final HttpClient client) {
        this.manager = manager;
        this.client = client;
    }
    
    
}
