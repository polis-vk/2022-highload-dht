package ok.dht.test.kuleshov.sharding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TransferReceiverService extends TransferService {
    private final Logger log = LoggerFactory.getLogger(TransferReceiverService.class);
    private final Map<Shard, Set<HashRange>> map = new HashMap<>();

    public void receiveTransfer(Map<Shard, Set<HashRange>> hashRangeMap) {
        isTransferring = true;
        map.putAll(hashRangeMap);

        for (Map.Entry<Shard, Set<HashRange>> entry : hashRangeMap.entrySet()) {
            hashRanges.addAll(entry.getValue());
        }
    }

    public void receiveEnd(Shard shard) {
        if (map.get(shard) == null) {
            return;
        }

        hashRanges.removeAll(map.get(shard));
        map.remove(shard);
        if (map.isEmpty()) {
            circle.clear();
            isTransferring = false;
            log.info("all transfer receive ended shard: " + shard.getUrl());
        }

        log.info("transfer receive ended shard: " + shard.getUrl());
    }

    @Override
    public void clear() {
        super.clear();
        map.clear();
    }
}
