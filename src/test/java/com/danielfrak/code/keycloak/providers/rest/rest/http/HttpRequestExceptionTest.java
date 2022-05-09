package com.danielfrak.code.keycloak.providers.rest.rest.http;

import org.apache.http.client.methods.HttpGet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpRequestExceptionTest {

    @Test
    void shouldGetCause() {
        var cause = new RuntimeException();

        var result = new HttpRequestException(null, cause);

        assertEquals(cause, result.getCause());
    }

    @Test
    void shouldGetMessage() {
        var request = new HttpGet("someUri");
        var result = new HttpRequestException(request, null);

        assertEquals("An error occurred while making a HTTP request: GET someUri HTTP/1.1", result.getMessage());
    }
}