package ok.dht.test.kuleshov.sharding;

import ok.dht.test.kuleshov.utils.CoolPair;

import java.util.Objects;

public class HashRange {
    private final int leftBorder;
    private final int rightBorder;

    public HashRange(int leftBorder, int rightBorder) {
        this.leftBorder = leftBorder;
        this.rightBorder = rightBorder;
    }

    public int getLeftBorder() {
        return leftBorder;
    }

    public int getRightBorder() {
        return rightBorder;
    }

    public CoolPair<HashRange, HashRange> split(int e) {
        return new CoolPair<>(new HashRange(this.leftBorder, e), new HashRange(e + 1, this.rightBorder));
    }

    public HashRange concat(HashRange pred) {
        return new HashRange(pred.leftBorder, this.rightBorder);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HashRange hashRange = (HashRange) o;
        return leftBorder == hashRange.leftBorder && rightBorder == hashRange.rightBorder;
    }

    @Override
    public int hashCode() {
        return Objects.hash(leftBorder, rightBorder);
    }

    @Override
    public String toString() {
        return "HashRange{" +
                "leftBorder=" + leftBorder +
                ", rightBorder=" + rightBorder +
                '}';
    }
}
