package ok.dht.test.skroba;

public final class Pair<F, S> {
    private final F first;
    private final S second;
    
    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }
    
    public S getSecond() {
        return second;
    }
    
    public F getFirst() {
        return first;
    }
}
