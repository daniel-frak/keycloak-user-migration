package com.danielfrak.code.keycloak.providers.rest.rest.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpResponseTest {

    @Test
    void shouldCreateWithCode() {
        var response = new HttpResponse(200);
        assertEquals(200, response.getCode());
        assertNull(response.getBody());
    }

    @Test
    void shouldCreateWithCodeAndBody() {
        var response = new HttpResponse(200, "someBody");
        assertEquals(200, response.getCode());
        assertEquals("someBody", response.getBody());
    }
}