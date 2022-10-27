package ok.dht.test.labazov.hash;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class Node {
    public static final int MAX_TASKS_ALLOWED = 128;
    public static final int MAX_WORKERS_ALLOWED = 3;

    public final String url;
    public final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    public final AtomicInteger tasksCount = new AtomicInteger(0);

    public Node(String url) {
        this.url = url;
    }
}