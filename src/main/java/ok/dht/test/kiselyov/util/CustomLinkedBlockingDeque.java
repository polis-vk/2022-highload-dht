package ok.dht.test.kiselyov.util;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

public class CustomLinkedBlockingDeque<E> extends LinkedBlockingDeque<E> {

    private final AtomicBoolean isHead = new AtomicBoolean(true);

    public CustomLinkedBlockingDeque(int dequeCapacity) {
        super(dequeCapacity);
    }

    @Override
    public E take() throws InterruptedException {
        return super.takeLast();
        /*if (isHead.get()) {
            isHead.set(!isHead.get());
            return super.takeFirst();
        } else {
            isHead.set(!isHead.get());
            return super.takeLast();
        }*/
    }
}
