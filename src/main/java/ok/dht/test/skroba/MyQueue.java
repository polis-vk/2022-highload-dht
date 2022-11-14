package ok.dht.test.skroba;

import java.util.concurrent.LinkedBlockingQueue;

public class MyQueue<T> extends LinkedBlockingQueue<T> {
    public MyQueue(int capacity) {
        super(capacity);
    }
}
