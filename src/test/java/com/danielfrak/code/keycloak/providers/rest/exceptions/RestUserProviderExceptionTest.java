package com.danielfrak.code.keycloak.providers.rest.exceptions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RestUserProviderExceptionTest {

    @Test
    void shouldGetCause() {
        var cause = new RuntimeException();

        var result = new RestUserProviderException(cause);

        assertEquals(cause, result.getCause());
    }

    @Test
    void shouldGetMessageAndCause() {
        var message = "someMessage";
        var cause = new RuntimeException();

        var result = new RestUserProviderException(message, cause);

        assertEquals(message, result.getMessage());
        assertEquals(cause, result.getCause());
    }
}