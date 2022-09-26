package ok.dht.test.shestakova.dao;

import jdk.incubator.foreign.MemorySegment;
import ok.dht.test.shestakova.dao.baseElements.BaseEntry;

import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.atomic.AtomicLong;

class State {

    final ConcurrentNavigableMap<MemorySegment, BaseEntry<MemorySegment>> memory;
    final ConcurrentNavigableMap<MemorySegment, BaseEntry<MemorySegment>> flushingMemory;
    final AtomicLong memorySize = new AtomicLong();
    final Storage storage;

    State(ConcurrentNavigableMap<MemorySegment, BaseEntry<MemorySegment>> memory,
                  ConcurrentNavigableMap<MemorySegment, BaseEntry<MemorySegment>> flushingMemory,
                  Storage storage) {
        this.memory = memory;
        this.flushingMemory = flushingMemory;
        this.memorySize.getAndSet(memory.size());
        this.storage = storage;
    }
}
