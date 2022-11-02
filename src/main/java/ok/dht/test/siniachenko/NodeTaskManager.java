package ok.dht.test.siniachenko;

import ok.dht.test.siniachenko.service.TycoonService;
import org.apache.commons.logging.Log;
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

    private final ExecutorService executorService;
    private final Map<String, Node> urlToNode;

    public NodeTaskManager(
        ExecutorService executorService, List<String> nodeUrls,
        int queueSizePerNode, int maxExecutorsPerNode
    ) {
        this.executorService = executorService;
        this.urlToNode = new HashMap<>();
        for (String nodeUrl : nodeUrls) {
            urlToNode.put(nodeUrl, new Node(maxExecutorsPerNode, queueSizePerNode));
        }
    }

    public boolean tryAddNodeTask(String nodeUrl, Runnable task) {
        Node node = urlToNode.get(nodeUrl);
        if (node == null) {
            throw new IllegalArgumentException("node url " + nodeUrl + " was not passed to constructor in modeUrls");
        }
        if (task == null) {
            throw new IllegalArgumentException("task is null");
        }

        if (node.queuedTasks.tryAcquire()) {
            if (node.executors.tryAcquire()) {
                executorService.execute(() -> {
                    node.nodeTasksQueue.offer(task);
                    try {
                        while (true) {
                            Runnable pollTask = node.nodeTasksQueue.poll();
                            if (pollTask != null) {
                                try {
                                    try {
                                        pollTask.run();  // no exceptions are expected
                                    } finally {
                                        node.queuedTasks.release();
                                    }
                                } catch (Exception e) {
                                    LOG.error("Exception executing task for node {}", nodeUrl, e);
                                    // but in case of exception continue executing tasks
                                }
                            } else {
                                break;
                            }
                        }
                    } finally {
                        // queue task
                        // see no executors
                        // fail
                    node.executors.release();
                    }
                });
            }
            node.nodeTasksQueue.offer(task);

            return true;
        } else {
            return false;
        }

    }

    private static class Node {
        private final ConcurrentLinkedQueue<Runnable> nodeTasksQueue;
        private final Semaphore executors;
        private final Semaphore queuedTasks;

        public Node(int maxExecutors, int maxQueueSize) {
            nodeTasksQueue = new ConcurrentLinkedQueue<>();
            executors = new Semaphore(maxExecutors);
            queuedTasks = new Semaphore(maxQueueSize);
        }
    }
}
