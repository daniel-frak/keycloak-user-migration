package com.danielfrak.code.keycloak.providers.rest.rest.http;

import org.apache.http.client.methods.HttpGet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestExceptionTest {

    @Test
    void shouldGetCause() {
        var cause = new RuntimeException();

        var result = new HttpRequestException(null, cause);

        assertThat(result.getCause()).isEqualTo(cause);
    }

    @Test
    void shouldGetMessage() {
        var request = new HttpGet("someUri");
        var result = new HttpRequestException(request, null);

        assertThat(result.getMessage())
                .isEqualTo("An error occurred while making a HTTP request: GET someUri HTTP/1.1");
    }
}
