package com.danielfrak.code.keycloak.providers.rest.rest.http;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HttpResponseTest {

    @Test
    void shouldCreateWithCode() {
        var response = new HttpResponse(200);
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void shouldCreateWithCodeAndBody() {
        var response = new HttpResponse(200, "someBody");
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("someBody");
    }
}