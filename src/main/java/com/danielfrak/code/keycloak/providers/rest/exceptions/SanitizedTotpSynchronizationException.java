package com.danielfrak.code.keycloak.providers.rest.exceptions;

public class SanitizedTotpSynchronizationException extends RuntimeException {

    // Preserves the failure location without exposing legacy credential data in messages or nested causes.
    public SanitizedTotpSynchronizationException(RuntimeException originalException) {
        super(originalException.getClass().getName());
        super.setStackTrace(originalException.getStackTrace());
    }
}
