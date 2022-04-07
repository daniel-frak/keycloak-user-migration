package com.danielfrak.code.keycloak.providers.rest.rest;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientTest {
    private static MockWebServer mockWebServer;
    private static String uri;
    private HttpClient httpClient;

    @BeforeAll
    static void beforeAll() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        uri = String.format("http://localhost:%s/",mockWebServer.getPort());
    }

    @AfterAll
    static void afterAll() throws IOException {
        mockWebServer.shutdown();
    }

    @BeforeEach
    void setUp() {
        this.httpClient = new HttpClient();
    }

    @Test
    void getWithBasicAuth() throws InterruptedException {
        var expectedBody = "anyBody";
        enqueueSuccessfulResponse(expectedBody);
        var username = "username";
        var password = "password";
        httpClient.enableBasicAuth(username, password);

        var response = httpClient.get(uri);

        var token = new String (Base64.encodeBase64(String.format("%s:%s",username, password).getBytes(StandardCharsets.ISO_8859_1)));
        var recordedRequest = mockWebServer.takeRequest();
        var authorizationHeader = recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION);
        assertEquals(expectedBody, response.body);
        assertEquals(HttpGet.METHOD_NAME, recordedRequest.getMethod());
        assertEquals("/", recordedRequest.getPath());
        assertEquals(uri, Objects.requireNonNull(recordedRequest.getRequestUrl()).toString());
        assertTrue(authorizationHeader.startsWith("Basic"));
        assertTrue(authorizationHeader.endsWith(token));
    }

    private void enqueueSuccessfulResponse(String body) {
        var mockResponse = new MockResponse()
                .setBody(body)
                .setResponseCode(HttpStatus.SC_OK);
        mockWebServer.enqueue(mockResponse);
    }

    @Test
    void getWithBearerTokenAuth() throws IOException, InterruptedException {
        var expectedBody = "anyBody";
        enqueueSuccessfulResponse(expectedBody);
        var token = "token";
        httpClient.enableBearerTokenAuth(token);
        var response = httpClient.get(uri);

        var recordedRequest = mockWebServer.takeRequest();
        assertEquals(expectedBody, response.body);
        assertEquals(HttpGet.METHOD_NAME, recordedRequest.getMethod());
        assertEquals("/", recordedRequest.getPath());
        assertEquals(uri, Objects.requireNonNull(recordedRequest.getRequestUrl()).toString());
        assertTrue(recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION).startsWith("Bearer"));
        assertTrue(recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION).endsWith(token));
    }

    @Test
    void getShouldSendAGetRequest() throws InterruptedException {
        var expectedBody = "anyBody";
        enqueueSuccessfulResponse(expectedBody);

        var response = httpClient.get(uri);

        var recordedRequest = mockWebServer.takeRequest();
        assertEquals(expectedBody, response.body);
        assertEquals(HttpGet.METHOD_NAME, recordedRequest.getMethod());
        assertEquals("/", recordedRequest.getPath());
        assertEquals(uri, Objects.requireNonNull(recordedRequest.getRequestUrl()).toString());
        assertNull(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void postShouldSendAPostRequest() throws InterruptedException {
        var expectedBody = "anyBody";
        enqueueSuccessfulResponse(expectedBody);

        var response = httpClient.post(uri, expectedBody);

        var recordedRequest = mockWebServer.takeRequest();
        assertEquals(expectedBody, response.body);
        assertEquals(HttpPost.METHOD_NAME, recordedRequest.getMethod());
        assertEquals("/", recordedRequest.getPath());
        assertEquals(uri, Objects.requireNonNull(recordedRequest.getRequestUrl()).toString());
    }

    @Test
    void postShouldBeSentWithBasicAuthWhenBasicAuthIsEnabled() throws InterruptedException {
        var expectedBody = "anyBody";
        enqueueSuccessfulResponse(expectedBody);
        var username = "username";
        var password = "password";
        httpClient.enableBasicAuth(username, password);

        var response = httpClient.post(uri, expectedBody);

        var token = new String (Base64.encodeBase64(String.format("%s:%s",username, password).getBytes(StandardCharsets.ISO_8859_1)));
        var recordedRequest = mockWebServer.takeRequest();
        var authorizationHeader = recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION);
        assertEquals(expectedBody, response.body);
        assertEquals(HttpPost.METHOD_NAME, recordedRequest.getMethod());
        assertEquals("/", recordedRequest.getPath());
        assertEquals(uri, Objects.requireNonNull(recordedRequest.getRequestUrl()).toString());
        assertTrue(authorizationHeader.startsWith("Basic"));
        assertTrue(authorizationHeader.endsWith(token));
    }

    @Test
    void postShouldBeSentWithBearerAuthWhenBasicAuthIsEnabled() throws InterruptedException {
        var expectedBody = "anyBody";
        var token = "token";
        enqueueSuccessfulResponse(expectedBody);
        httpClient.enableBearerTokenAuth(token);

        var response = httpClient.post(uri, expectedBody);

        var recordedRequest = mockWebServer.takeRequest();
        assertEquals(expectedBody, response.body);
        assertEquals(HttpPost.METHOD_NAME, recordedRequest.getMethod());
        assertEquals("/", recordedRequest.getPath());
        assertEquals(uri, Objects.requireNonNull(recordedRequest.getRequestUrl()).toString());
        assertTrue(recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION).startsWith("Bearer"));
        assertTrue(recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION).endsWith(token));
    }
}