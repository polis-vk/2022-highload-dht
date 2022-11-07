package ok.dht.test.kovalenko.utils;

import java.util.Comparator;

public final class ResponseComparator implements Comparator<MyOneNioResponse> {

    public static final ResponseComparator INSTANSE = new ResponseComparator();

    private ResponseComparator() {
    }

    @Override
    public int compare(MyOneNioResponse r1, MyOneNioResponse r2) {
        return Long.compare(r2.getEntryTimestamp(), r1.getEntryTimestamp()); // reverse comparing
    }
}
