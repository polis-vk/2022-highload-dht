package ok.dht.test.kuleshov.test;

import java.io.IOException;

public final class MainTest {

    private MainTest() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Test test = new Test();
        test.twoNode();
        test.twoNodeCustomHash();
        test.twoNodeTransfer();
        test.twoNodeDelete();
    }
}
