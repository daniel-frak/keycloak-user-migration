package com.danielfrak.code.keycloak.providers.rest.rest;

import com.danielfrak.code.keycloak.providers.rest.rest.http.HttpRequestException;
import org.apache.http.client.methods.HttpGet;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class RestUserServiceFailureMessageTest {

    private static final String URI = "http://localhost:9090";

    @Test
    void lookupFailureMessageShouldNameProviderAndCauseWithoutUser() {
        var request = new HttpGet(URI + "/someone%40example.com");
        var exception = new HttpRequestException(request, new SocketTimeoutException("Read timed out"));

        var message = RestUserService.lookupFailureMessage("someProvider", URI, exception);

        assertThat(message).isEqualTo("Legacy user lookup failed for provider 'someProvider' (" + URI + "): " +
                "SocketTimeoutException: Read timed out - treating user as not found");
    }

    @Test
    void lookupFailureMessageShouldUseExceptionItselfWhenItHasNoCause() {
        var exception = new IOException("Unexpected end of input");

        var message = RestUserService.lookupFailureMessage("someProvider", URI, exception);

        assertThat(message).isEqualTo("Legacy user lookup failed for provider 'someProvider' (" + URI + "): " +
                "IOException: Unexpected end of input - treating user as not found");
    }

    @Test
    void failureMessageShouldNameOperationAndConsequence() {
        var exception = new HttpRequestException(new HttpGet(URI), new SocketTimeoutException("Read timed out"));

        var message = RestUserService.failureMessage(
                "password validation", "someProvider", URI, exception, "aborting password validation");

        assertThat(message).isEqualTo("Legacy password validation failed for provider 'someProvider' (" + URI + "): " +
                "SocketTimeoutException: Read timed out - aborting password validation");
    }

    @Test
    void lookupFailureMessageShouldUseOuterMessageWhenCauseHasNone() {
        var exception = new RuntimeException("Outer message", new IllegalStateException());

        var message = RestUserService.lookupFailureMessage("someProvider", URI, exception);

        assertThat(message).isEqualTo("Legacy user lookup failed for provider 'someProvider' (" + URI + "): " +
                "IllegalStateException: Outer message - treating user as not found");
    }

    @Test
    void lookupFailureMessageShouldOmitMissingMessage() {
        var exception = new RuntimeException();

        var message = RestUserService.lookupFailureMessage("someProvider", URI, exception);

        assertThat(message).isEqualTo("Legacy user lookup failed for provider 'someProvider' (" + URI + "): " +
                "RuntimeException - treating user as not found");
    }
}
