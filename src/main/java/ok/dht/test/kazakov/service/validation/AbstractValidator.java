package ok.dht.test.kazakov.service.validation;

import one.nio.util.Utf8;

public abstract class AbstractValidator<Self extends AbstractValidator<Self>> {
    private String errorMessage;

    protected abstract Self getSelf();

    public boolean isInvalid() {
        return errorMessage != null;
    }

    public byte[] getErrorMessage() {
        return Utf8.toBytes(errorMessage);
    }

    public Self setInvalid(final String errorMessage) {
        this.errorMessage = errorMessage;
        return getSelf();
    }

    public Self setValid() {
        this.errorMessage = null;
        return getSelf();
    }
}
