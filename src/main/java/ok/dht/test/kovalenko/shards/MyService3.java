package ok.dht.test.kovalenko.shards;

import ok.dht.ServiceConfig;
import ok.dht.test.kovalenko.MyServiceBase;
import ok.dht.test.kovalenko.utils.Main;

import java.io.IOException;

public final class MyService3 extends MyServiceBase {

    private static final int ORDINAL = 3;

    public MyService3(ServiceConfig config) throws IOException {
        super(config);
    }

    public static void main(String[] args) {
        Main.main(ORDINAL);
    }
}
