package ok.dht.test.skroba.dao.base;

import java.time.Instant;

public record TimeEntry<Data>(Data key, Data value, Instant time)
        implements Entry<Data>,
        Comparable<TimeEntry<Data>>
{
    @Override
    public String toString() {
        return "{" + key + ":" + value + "}";
    }
    
    @Override
    public int compareTo(TimeEntry o) {
        return time.compareTo(o.time);
    }
}
