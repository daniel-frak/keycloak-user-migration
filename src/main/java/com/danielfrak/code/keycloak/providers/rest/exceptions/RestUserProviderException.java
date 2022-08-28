package com.danielfrak.code.keycloak.providers.rest.exceptions;

public class RestUserProviderException extends RuntimeException {

    public RestUserProviderException(Throwable cause) {
        super(cause);
    }

    public RestUserProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}