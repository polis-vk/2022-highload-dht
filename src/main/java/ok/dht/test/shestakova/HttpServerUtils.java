package ok.dht.test.shestakova;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.ServiceConfig;
import one.nio.util.Hash;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class HttpServerUtils {

    static final HttpServerUtils INSTANCE = new HttpServerUtils();

    List<String> getNodesSortedByRendezvousHashing(String key, CircuitBreakerImpl circuitBreaker,
                                                   ServiceConfig serviceConfig, int from) {
        Map<Integer, String> nodesHashes = new TreeMap<>();

        for (String nodeUrl : serviceConfig.clusterUrls()) {
            if (circuitBreaker.isNodeIll(nodeUrl)) {
                continue;
            }
            nodesHashes.put(Hash.murmur3(nodeUrl + key), nodeUrl);
        }
        return nodesHashes.values().stream()
                .limit(from)
                .toList();
    }

    byte[] getBody(ByteBuffer bodyBB) {
        byte[] body;
        bodyBB.position(Long.BYTES);
        int valueLength = bodyBB.getInt();
        if (valueLength == -1) {
            body = null;
        } else {
            body = new byte[valueLength];
            bodyBB.get(body, 0, valueLength);
        }
        return body;
    }

    MemorySegment fromString(String data) {
        return data == null ? null : MemorySegment.ofArray(data.getBytes(StandardCharsets.UTF_8));
    }
}
