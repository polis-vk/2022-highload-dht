package ok.dht.test.kazakov.service.validation;

import one.nio.util.Utf8;

public abstract class AbstractValidator<SelfT extends AbstractValidator<SelfT>> {
    private String errorMessage;

    protected abstract SelfT getSelf();

    public boolean isInvalid() {
        return errorMessage != null;
    }

    public byte[] getErrorMessage() {
        return Utf8.toBytes(errorMessage);
    }

    public SelfT setInvalid(final String errorMessage) {
        this.errorMessage = errorMessage;
        return getSelf();
    }

    public SelfT setValid() {
        this.errorMessage = null;
        return getSelf();
    }
}
