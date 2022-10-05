package ok.dht.test.kiselyov.util;

import java.util.concurrent.LinkedBlockingDeque;

public class CustomLinkedBlockingDeque<E> extends LinkedBlockingDeque<E> {

    public CustomLinkedBlockingDeque(int dequeCapacity) {
        super(dequeCapacity);
    }

    @Override
    public E take() throws InterruptedException {
        return super.takeLast();
    }
}
