package ok.dht.test.maximenko.dao;

import jdk.incubator.foreign.MemorySegment;

import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.TreeMap;

public class BorderedMergeIterator implements Iterator<Entry<MemorySegment>> {
    private static final Comparator<MemorySegment> COMPARATOR = NaturalOrderComparator.getInstance();
    private final NavigableMap<MemorySegment, Source> sources;

    public BorderedMergeIterator(List<Table> tables) throws IOException {
        this(null, null, tables);
    }

    private static class Source {
        Iterator<Entry<MemorySegment>> iterator;
        Entry<MemorySegment> element;
        final int id;

        public Source(Table table, MemorySegment from, MemorySegment to, int id) throws IOException {
            this.iterator = table.get(from, to);
            if (iterator.hasNext()) {
                element = iterator.next();
            } else {
                element = null;
            }
            this.id = id;
        }

        public Source(Iterator<Entry<MemorySegment>> iterator,  int id) {
            this.iterator = iterator;
            if (iterator.hasNext()) {
                element = iterator.next();
            } else {
                element = null;
            }
            this.id = id;
        }
    }

    public BorderedMergeIterator(MemorySegment from, MemorySegment to, List<Table> tables) throws IOException {
        sources = new TreeMap<>(COMPARATOR);
        int sourceId = 0;

        for (Table table : tables) {
            Source source = new Source(table, from, to, sourceId);
            addSource(source);
            sourceId++;
        }

        removeNextNullValues();
    }

    public BorderedMergeIterator(List<Iterator<Entry<MemorySegment>>> iterators, int size) throws IOException {
        sources = new TreeMap<>(COMPARATOR);
        int sourceId = 0;

        for (Iterator<Entry<MemorySegment>> iterator : iterators) {
            Source source = new Source(iterator, sourceId);
            addSource(source);
            sourceId++;
        }

        removeNextNullValues();
    }

    @Override
    public boolean hasNext() {
        return !sources.isEmpty();
    }

    @Override
    public Entry<MemorySegment> next() {
        if (sources.size() == 1) {
            Entry<MemorySegment> result = sources.firstEntry().getValue().element;
            if (!moveSource(sources.firstEntry().getValue())) {
                sources.remove(sources.firstEntry().getKey());
            }
            removeNextNullValues();
            return result;
        }

        Entry<MemorySegment> result;
        if (sources.size() > 1) {
            Source source = popIterator();
            if (source == null) {
                throw new NoSuchElementException();
            }
            result = source.element;
            if (moveSource(source)) {
                addSource(source);
            }
        } else {
            Source source = peekIterator();
            if (source == null) {
                throw new NoSuchElementException();
            }
            result = source.element;
            if (!moveSource(sources.firstEntry().getValue())) {
                sources.remove(source.element.key());
            }
        }
        removeNextNullValues();
        return result;
    }

    private boolean moveSource(Source source) {
        if (source.iterator.hasNext()) {
            source.element = source.iterator.next();
            return true;
        }
        return false;
    }

    private void removeNextNullValues() {
        Source source = peekIterator();
        while (source != null && source.element.value() == null) {
            popIterator();
            if (moveSource(source)) {
                addSource(source);
            }
            source = peekIterator();
        }
    }

    private Source popIterator() {
        if (sources.isEmpty()) {
            return null;
        }

        Source minSource = sources.firstEntry().getValue();
        sources.remove(sources.firstKey());
        return minSource;
    }

    private Source peekIterator() {
        if (sources.isEmpty()) {
            return null;
        }

        return sources.firstEntry().getValue();
    }

    private void addSource(Source changedSource) {
        if (changedSource.element == null) {
            return;
        }

        Source source = changedSource;
        while (true) {
            Source existedSourceWithSameKey = sources.getOrDefault(source.element.key(), null);
            if (existedSourceWithSameKey == null) {
                sources.put(source.element.key(), source);
                break;
            }

            if (existedSourceWithSameKey.id > source.id) {
                sources.put(source.element.key(), source);
                source = existedSourceWithSameKey;
            }
            if (!source.iterator.hasNext()) {
                break;
            }
            source.element = source.iterator.next();
        }
    }
}
