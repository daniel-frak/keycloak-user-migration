package com.danielfrak.code.keycloak.providers.rest.rest.http;

import com.danielfrak.code.keycloak.providers.rest.exceptions.RestUserProviderException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientTest {

    private HttpClient httpClient;
    private MockWebServer mockWebServer;
    private String uri;

    private static Stream<Arguments> invalidBasicAuthCredentials() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of(null, "somePassword"),
                Arguments.of("", "somePassword"),
                Arguments.of(" ", "somePassword"),
                Arguments.of("someUser", null),
                Arguments.of("someUser", ""),
                Arguments.of("someUser", " ")
        );
    }

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        httpClient = new HttpClient();
        uri = String.format("http://" + mockWebServer.getHostName() + ":%s/", mockWebServer.getPort());
    }

    @AfterEach
    void afterEach() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void getShouldThrowIfIOExceptionIsThrown() {
        var mockResponse = new MockResponse()
                .setBody(new Buffer().write(new byte[4096]))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY);
        mockWebServer.enqueue(mockResponse);

        assertThrows(RestUserProviderException.class, () -> httpClient.get(uri));
    }

    @Test
    void shouldGetWithNoAuth() throws InterruptedException {
        var expectedBody = "anyBody";
        enqueueSuccessfulResponse(expectedBody);

        var response = httpClient.get(uri);

        var recordedRequest = mockWebServer.takeRequest();
        assertEquals(expectedBody, response.body);
        assertEquals(HttpGet.METHOD_NAME, recordedRequest.getMethod());
        assertEquals("/", recordedRequest.getPath());
        assertEquals(uri, Objects.requireNonNull(recordedRequest.getRequestUrl()).toString());
        assertNull(recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION));
    }

    private void enqueueSuccessfulResponse(String body) {
        var mockResponse = new MockResponse()
                .setBody(body)
                .setResponseCode(HttpStatus.SC_OK);
        mockWebServer.enqueue(mockResponse);
    }

    @ParameterizedTest
    @MethodSource("invalidBasicAuthCredentials")
    void shouldNotEnableBasicAuthIfCredentialsIncorrect(String basicAuthUser, String basicAuthPassword)
            throws InterruptedException {
        var expectedBody = "anyBody";
        enqueueSuccessfulResponse(expectedBody);

        httpClient.enableBasicAuth(basicAuthUser, basicAuthPassword);
        var response = httpClient.get(uri);

        var recordedRequest = mockWebServer.takeRequest();
        assertEquals(expectedBody, response.body);
        assertEquals(HttpGet.METHOD_NAME, recordedRequest.getMethod());
        assertEquals("/", recordedRequest.getPath());
        assertEquals(uri, Objects.requireNonNull(recordedRequest.getRequestUrl()).toString());
        assertNull(recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void shouldGetWithBasicAuth() throws InterruptedException {
        var expectedBody = "anyBody";
        enqueueSuccessfulResponse(expectedBody);
        var username = "username";
        var password = "password";

        httpClient.enableBasicAuth(username, password);
        var response = httpClient.get(uri);

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals(expectedBody, response.body);
        assertEquals(HttpGet.METHOD_NAME, recordedRequest.getMethod());
        assertEquals("/", recordedRequest.getPath());
        assertEquals(uri, Objects.requireNonNull(recordedRequest.getRequestUrl()).toString());
        String authorizationHeader = recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION);
        assertNotNull(authorizationHeader);
        assertTrue(authorizationHeader.startsWith("Basic"));
        var expectedToken = new String(Base64.encodeBase64(String.format("%s:%s", username, password)
                .getBytes(StandardCharsets.ISO_8859_1)));
        assertTrue(authorizationHeader.endsWith(expectedToken));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void shouldNotEnableBearerTokenAuthIfTokenIncorrect(String bearerToken)
            throws InterruptedException {
        var expectedBody = "anyBody";
        enqueueSuccessfulResponse(expectedBody);

        httpClient.enableBearerTokenAuth(bearerToken);
        var response = httpClient.get(uri);

        var recordedRequest = mockWebServer.takeRequest();
        assertEquals(expectedBody, response.body);
        assertEquals(HttpGet.METHOD_NAME, recordedRequest.getMethod());
        assertEquals("/", recordedRequest.getPath());
        assertEquals(uri, Objects.requireNonNull(recordedRequest.getRequestUrl()).toString());
        assertNull(recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void shouldGetWithBearerTokenAuth() throws InterruptedException {
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
        String authorization = recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION);
        assertNotNull(authorization);
        assertTrue(authorization.startsWith("Bearer"));
        assertTrue(authorization.endsWith(token));
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
    void postShouldThrowIfIOExceptionIsThrown() {
        var mockResponse = new MockResponse()
                .setBody(new Buffer().write(new byte[4096]))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY);
        mockWebServer.enqueue(mockResponse);

        assertThrows(RestUserProviderException.class, () -> httpClient.post(uri, "{}"));
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

        var token = new String(Base64.encodeBase64(String.format("%s:%s", username, password).getBytes(StandardCharsets.ISO_8859_1)));
        var recordedRequest = mockWebServer.takeRequest();
        var authorizationHeader = recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION);
        assertEquals(expectedBody, response.body);
        assertEquals(HttpPost.METHOD_NAME, recordedRequest.getMethod());
        assertEquals("/", recordedRequest.getPath());
        assertEquals(uri, Objects.requireNonNull(recordedRequest.getRequestUrl()).toString());
        assertNotNull(authorizationHeader);
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
        String authorization = recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION);
        assertNotNull(authorization);
        assertTrue(authorization.startsWith("Bearer"));
        assertTrue(authorization.endsWith(token));
    }
}