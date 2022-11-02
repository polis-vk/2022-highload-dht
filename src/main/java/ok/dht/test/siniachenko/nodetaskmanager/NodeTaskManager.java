package ok.dht.test.siniachenko.nodetaskmanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;

public class NodeTaskManager {
    private static final Logger LOG = LoggerFactory.getLogger(NodeTaskManager.class);

    final ExecutorService executorService;
    final Map<String, Node> urlToNode;
    private final int queueSizePerNode;
    private final int maxExecutorsPerNode;

    public NodeTaskManager(
        ExecutorService executorService, List<String> nodeUrls,
        int queueSizePerNode, int maxExecutorsPerNode
    ) {
        this.executorService = executorService;
        this.urlToNode = new HashMap<>();
        for (String nodeUrl : nodeUrls) {
            urlToNode.put(nodeUrl, new Node(maxExecutorsPerNode, queueSizePerNode));
        }
        this.queueSizePerNode = queueSizePerNode;
        this.maxExecutorsPerNode = maxExecutorsPerNode;
    }

    public boolean tryAddNodeTask(String nodeUrl, Runnable task) {
        Node node = urlToNode.get(nodeUrl);
        if (node == null) {
            throw new IllegalArgumentException("node url " + nodeUrl + " was not passed to constructor in modeUrls");
        }
        if (task == null) {
            throw new NullPointerException("task is null");
        }

        if (node.queuedTasks.tryAcquire()) {
            node.nodeTasksQueue.offer(task);
        } else {
            return false;
        }

        if (node.executors.tryAcquire()) {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        processTasks(node, nodeUrl);
                    } finally {
                        node.executors.release();

                        // After we polled null:
                        // other thread could offer task
                        // but get false with node.executors.tryAcquire()
                        // That's why after release we must check the situation, that there are
                        // no more executors for node, but some tasks are remaining

                        if (noNodeExecutors(node) && !noNodeTasks(node) && node.executors.tryAcquire()) {
                            executorService.execute(this);
                        }
                    }
                }
            });
        }

        return true;
    }

    private static void processTasks(Node node, String nodeUrl) {
        while (true) {
            Runnable pollTask = node.nodeTasksQueue.poll();
            if (pollTask == null) {
                break;
            } else {
                try {
                    try {
                        pollTask.run(); // no exceptions are expected
                    } finally {
                        node.queuedTasks.release();
                    }
                } catch (Exception e) {
                    LOG.error("Exception executing task for node {}", nodeUrl, e);
                    // but in case of exception continue executing tasks
                }
            }
        }
    }

    private boolean noNodeTasks(Node node) {
        return node.queuedTasks.availablePermits() == queueSizePerNode;
    }

    private boolean noNodeExecutors(Node node) {
        return node.executors.availablePermits() == maxExecutorsPerNode;
    }

    static class Node {
        final ConcurrentLinkedQueue<Runnable> nodeTasksQueue;
        final Semaphore executors;
        final Semaphore queuedTasks;

        public Node(int maxExecutors, int maxQueueSize) {
            nodeTasksQueue = new ConcurrentLinkedQueue<>();
            executors = new Semaphore(maxExecutors);
            queuedTasks = new Semaphore(maxQueueSize);
        }
    }
}
