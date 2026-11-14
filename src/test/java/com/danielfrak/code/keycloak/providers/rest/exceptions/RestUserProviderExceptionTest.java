package com.danielfrak.code.keycloak.providers.rest.exceptions;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RestUserProviderExceptionTest {

    @Test
    void shouldGetCause() {
        var cause = new RuntimeException();

        var result = new RestUserProviderException(cause);

        assertThat(result.getCause()).isEqualTo(cause);
    }

    @Test
    void shouldGetMessageAndCause() {
        var message = "someMessage";
        var cause = new RuntimeException();

        var result = new RestUserProviderException(message, cause);

        assertThat(result.getMessage()).isEqualTo(message);
        assertThat(result.getCause()).isEqualTo(cause);
    }
}
