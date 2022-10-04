package ok.dht.test.lutsenko.service;

import one.nio.http.HttpSession;

public class SessionRunnable implements Runnable {

    public final HttpSession session;
    public final Runnable runnable;

    public SessionRunnable(HttpSession session, Runnable runnable) {
        this.session = session;
        this.runnable = runnable;
    }

    @Override
    public void run() {
        runnable.run();
    }
}
