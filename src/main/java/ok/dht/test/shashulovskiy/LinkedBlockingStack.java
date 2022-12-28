package ok.dht.test.shashulovskiy;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class LinkedBlockingStack<T> extends LinkedBlockingDeque<T> {
    @Override
    public boolean offer(T t) {
        return offerFirst(t);
    }

    @Override
    public boolean offer(T t, long timeout, TimeUnit unit) throws InterruptedException {
        return offerFirst(t, timeout, unit);
    }
}
