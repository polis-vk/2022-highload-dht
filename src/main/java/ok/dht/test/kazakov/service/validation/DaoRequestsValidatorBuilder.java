package ok.dht.test.kazakov.service.validation;

public final class DaoRequestsValidatorBuilder {
    private final ThreadLocal<Validator> validator = ThreadLocal.withInitial(Validator::new);

    public Validator validate() {
        return validator.get().setValid();
    }

    public static final class Validator extends AbstractValidator<Validator> {

        @Override
        protected Validator getSelf() {
            return this;
        }

        public Validator validateId(final String id) {
            if (isInvalid()) {
                return this;
            }

            if (id == null) {
                return setInvalid("Id should not be absent");
            }

            if (id.isEmpty()) {
                return setInvalid("Id should not be empty");
            }

            return this;
        }

        public Validator validateValue(final byte[] value) {
            if (isInvalid()) {
                return this;
            }

            if (value == null) {
                return setInvalid("Value should not be absent");
            }

            return this;
        }
    }
}
