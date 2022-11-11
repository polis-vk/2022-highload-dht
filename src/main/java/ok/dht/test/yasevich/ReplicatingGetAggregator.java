package ok.dht.test.yasevich;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ReplicatingGetAggregator extends ReplicatingResponseCounter {
    private final Lock lock = new ReentrantLock();
    private TimeStampingDao.TimeStampedValue latestValue;

    public ReplicatingGetAggregator(int ack, int from) {
        super(ack, from);
    }

    public TimeStampingDao.TimeStampedValue updateValueIfNeeded(TimeStampingDao.TimeStampedValue value) {
        lock.lock();
        try {
            if (value == null) {
                return latestValue;
            }
            if (latestValue == null || value.time > latestValue.time) {
                latestValue = value;
            }
            return latestValue;
        } finally {
            lock.unlock();
        }
    }

}
