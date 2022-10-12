package ok.dht.test.mikhaylov.internal;

import ok.dht.test.mikhaylov.MyService;
import one.nio.http.HttpClient;
import one.nio.http.HttpException;
import one.nio.http.Request;
import one.nio.http.Response;
import one.nio.net.ConnectionString;
import one.nio.pool.PoolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static ok.dht.test.mikhaylov.MyService.makeError;

public class OneNioHttpClient extends InternalHttpClient {
    private final ThreadLocal<Map<String, HttpClient>> clients;

    private final Logger logger = LoggerFactory.getLogger(OneNioHttpClient.class);

    public OneNioHttpClient(List<String> shards) {
        super();
        clients = ThreadLocal.withInitial(() -> {
            Map<String, HttpClient> clientsMap = new HashMap<>();
            for (String shard : shards) {
                clientsMap.put(shard, new HttpClient(new ConnectionString(shard)));
            }
            return clientsMap;
        });
    }

    @Override
    public Response proxyRequest(Request request, String shard) throws ExecutionException, InterruptedException,
            TimeoutException {
        return getExecutor().submit(() -> {
            try {
                Request internalRequest = new Request(request.getMethod(),
                        request.getURI().replaceFirst(MyService.ENTITY_PATH, MyService.ENTITY_INTERNAL_PATH),
                        true);
                internalRequest.setBody(request.getBody());
                for (String header : request.getHeaders()) {
                    if (header != null) {
                        internalRequest.addHeader(header);
                    }
                }
                return clients.get().get(shard).invoke(internalRequest);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return makeError(logger, shard, e);
            } catch (PoolException | IOException | HttpException e) {
                return makeError(logger, shard, e);
            }
        }).get(1, TimeUnit.SECONDS);
    }

    @Override
    public void close() throws IOException {
        super.close();
        for (HttpClient client : clients.get().values()) {
            client.close();
        }
        clients.remove();
    }
}
