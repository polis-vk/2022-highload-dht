package ok.dht.test.slastin.node;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class Node {
    private final int maxTasks;
    private final int maxWorkers;

    private final Queue<Runnable> tasks;
    private final AtomicInteger workersCount;

    public Node(NodeConfig nodeConfig) {
        maxTasks = nodeConfig.maxTasks();
        maxWorkers = nodeConfig.maxWorkers();
        tasks = new LinkedBlockingQueue<>(maxTasks);
        workersCount = new AtomicInteger(0);
    }

    public int maxTasks() {
        return maxTasks;
    }

    public int maxWorkers() {
        return maxWorkers;
    }

    public boolean offerTask(Runnable task) {
        return tasks.offer(task);
    }

    public Runnable pollTask() {
        int currentWorkers = workersCount.incrementAndGet();
        if (currentWorkers > maxWorkers) {
            workersCount.decrementAndGet();
            return null;
        }
        Runnable currentTask = tasks.poll();
        if (currentTask == null) {
            workersCount.decrementAndGet();
        }
        return currentTask;
    }

    public void finishTask() {
        workersCount.decrementAndGet();
    }
}
