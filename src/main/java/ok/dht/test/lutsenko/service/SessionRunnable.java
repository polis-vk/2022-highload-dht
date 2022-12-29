package ok.dht.test.lutsenko.service;

public class SessionRunnable implements Runnable {

    public final ExtendedSession session;
    public final Runnable runnable;

    public SessionRunnable(ExtendedSession session, Runnable runnable) {
        this.session = session;
        this.runnable = runnable;
    }

    @Override
    public void run() {
        runnable.run();
    }
}
