package ok.dht.test.kovalenko.shards;

import ok.dht.ServiceConfig;
import ok.dht.test.kovalenko.MyServiceBase;

import java.io.IOException;

public class MyService2 extends MyServiceBase {

    private static final int ORDINAL = 2;

    public MyService2(ServiceConfig config) throws IOException {
        super(config);
    }

    public static void main(String[] args) {
        MyServiceBase.main(ORDINAL);
    }
}
