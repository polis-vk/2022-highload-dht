package ok.dht.test.siniachenko.range;

import ok.dht.test.siniachenko.Utils;
import one.nio.util.Utf8;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

public class InRangeEntityIterator implements Iterator<Map.Entry<byte[], byte[]>> {

    private final Iterator<Map.Entry<byte[], byte[]>> srcIterator;
    private final String firstKey;
    private final String lastKey;
    private Map.Entry<byte[], byte[]> tempElement;

    public InRangeEntityIterator(Iterator<Map.Entry<byte[], byte[]>> srcIterator, String firstKey, String lastKey) {
        this.srcIterator = srcIterator;
        this.firstKey = firstKey;
        this.lastKey = lastKey;
    }

    private void filterTillNextInRange() {
        while (tempElement == null && srcIterator.hasNext()) {
            Map.Entry<byte[], byte[]> element = srcIterator.next();
            if (inRange(element) && !Utils.readFlagDeletedFromBytes(element.getValue())) {
                tempElement = element;
            }
        }
    }

    private boolean inRange(Map.Entry<byte[], byte[]> element) {
        String key = Utf8.toString(element.getKey());
        return firstKey.compareTo(key) <= 0 && (lastKey == null || key.compareTo(lastKey) < 0);
    }

    @Override
    public boolean hasNext() {
        filterTillNextInRange();
        return tempElement != null;
    }

    @Override
    public Map.Entry<byte[], byte[]> next() {
        filterTillNextInRange();
        if (tempElement == null) {
            throw new NoSuchElementException();
        }
        Map.Entry<byte[], byte[]> result = tempElement;
        tempElement = null;
        return result;
    }
}
