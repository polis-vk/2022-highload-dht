package ok.dht.test.kovalenko.utils;

import java.util.Comparator;

public final class ResponseComparator implements Comparator<MyHttpResponse> {

    public static final ResponseComparator INSTANSE = new ResponseComparator();

    private ResponseComparator() {
    }

    @Override
    public int compare(MyHttpResponse r1, MyHttpResponse r2) {
        return Long.compare(r2.getTimestamp(), r1.getTimestamp()); // reverse comparing
    }
}
