package ok.dht.test.kazakov.service.validation;

import one.nio.util.Utf8;

public abstract class AbstractValidator<SELF extends AbstractValidator<SELF>> {
    private String errorMessage;

    protected abstract SELF getSelf();

    public boolean isInvalid() {
        return errorMessage != null;
    }

    public byte[] getErrorMessage() {
        return Utf8.toBytes(errorMessage);
    }

    public SELF setInvalid(final String errorMessage) {
        this.errorMessage = errorMessage;
        return getSelf();
    }

    public SELF setValid() {
        this.errorMessage = null;
        return getSelf();
    }
}
