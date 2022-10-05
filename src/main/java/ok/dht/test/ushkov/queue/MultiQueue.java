package ok.dht.test.ushkov.queue;

import java.util.AbstractQueue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MultiQueue<T> extends AbstractQueue<T> implements BlockingQueue<T> {
    private final List<Queue<T>> queues;
    private final List<Lock> locks;
    private final int capacity;

    // capacity is capacity of single queue
    public MultiQueue(int nQueues, int capacity) {
        queues = Stream.generate(() -> (Queue<T>) new ArrayDeque<T>(capacity))
                .limit(nQueues)
                .collect(Collectors.toCollection(ArrayList::new));
        locks = Stream.generate(() -> (Lock) new ReentrantLock())
                .limit(nQueues)
                .collect(Collectors.toCollection(ArrayList::new));
        this.capacity = capacity;
    }


    // Not thread safe method
    @Override
    public Iterator<T> iterator() {
        List<T> list = queues.stream().flatMap(Collection::stream).toList();
        return list.iterator();
    }

    // Not thread safe method
    @Override
    public int size() {
        List<T> list = queues.stream().flatMap(Collection::stream).toList();
        return list.size();
    }

    @Override
    public void put(T value) throws InterruptedException {
        offer(value);
    }

    @Override
    public boolean offer(T value, long timeout, TimeUnit unit) throws InterruptedException {
        return offer(value);
    }

    @Override
    public T take() throws InterruptedException {
        return poll();
    }

    @Override
    public T poll(long timeout, TimeUnit unit) throws InterruptedException {
        return poll();
    }

    @Override
    public int remainingCapacity() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int drainTo(Collection<? super T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int drainTo(Collection<? super T> c, int maxElements) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean offer(T value) {
        while (true) {
            int pos = ThreadLocalRandom.current().nextInt(queues.size());
            if (locks.get(pos).tryLock()) {
                if (queues.get(pos).size() == capacity) {
                    return false;
                }
                try {
                    queues.get(pos).add(value);
                    return true;
                } finally {
                    locks.get(pos).unlock();
                }
            }
        }
    }

    @Override
    public T poll() {
        while (true) {
            int pos1 = ThreadLocalRandom.current().nextInt(queues.size());
            int pos2 = ThreadLocalRandom.current().nextInt(queues.size());
            if (pos1 != pos2) {
                if (!locks.get(pos1).tryLock()) {
                    continue;
                }
                try {
                    if (!locks.get(pos2).tryLock()) {
                        continue;
                    }
                    try {
                        if (queues.get(pos1).peek() == null) {
                            return queues.get(pos2).poll();
                        } else if (queues.get(pos2).peek() == null) {
                            return queues.get(pos1).poll();
                        } else if (queues.get(pos1).size() > queues.get(pos2).size()) {
                            return queues.get(pos1).poll();
                        } else {
                            return queues.get(pos2).poll();
                        }
                    } finally {
                        locks.get(pos2).unlock();
                    }
                } finally {
                    locks.get(pos1).unlock();
                }
            }
        }
    }

    @Override
    public T peek() {
        while (true) {
            int pos = ThreadLocalRandom.current().nextInt(queues.size());
            if (locks.get(pos).tryLock()) {
                try {
                    return queues.get(pos).peek();
                } finally {
                    locks.get(pos).unlock();
                }
            }
        }
    }
}
