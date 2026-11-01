package com.civiclens.ai;

/** A classified failure from the AI provider or from validating its output. Messages never contain secrets. */
public class ProviderException extends Exception {

    public enum FailureType {
        NOT_CONFIGURED(false),
        AUTH_FAILED(false),
        MODEL_NOT_FOUND(false),
        BAD_REQUEST(false),
        BLOCKED(false),
        RATE_LIMITED(true),
        TIMEOUT(true),
        NETWORK(true),
        PROVIDER_ERROR(true),
        EMPTY_RESPONSE(true),
        MALFORMED_OUTPUT(true),
        INVALID_OUTPUT(true);

        private final boolean retryable;

        FailureType(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean retryable() {
            return retryable;
        }
    }

    private final FailureType type;

    public ProviderException(FailureType type, String message) {
        super(message);
        this.type = type;
    }

    public ProviderException(FailureType type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public FailureType type() {
        return type;
    }

    public boolean retryable() {
        return type.retryable();
    }
}
