package com.danielfrak.code.keycloak.providers.rest.rest.http;

import com.danielfrak.code.keycloak.providers.rest.exceptions.RestUserProviderException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.net.URI;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HttpClientTest {

    private HttpClient httpClient;
    private MockWebServer mockWebServer;
    private URI uri;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        httpClient = new HttpClient(HttpClientBuilder.create().setRedirectStrategy(new LaxRedirectStrategy()));
        uri = URI.create(String.format("http://%s:%s/", mockWebServer.getHostName(), mockWebServer.getPort()));
    }

    @AfterEach
    void afterEach() throws IOException {
        mockWebServer.shutdown();
    }

    private void enqueueFailedResponse() {
        var mockResponse = new MockResponse()
                .setBody("anyBody")
                .setResponseCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        mockWebServer.enqueue(mockResponse);
    }

    private void enqueueSuccessfulResponse(String body) {
        var mockResponse = new MockResponse()
                .setBody(body)
                .setResponseCode(HttpStatus.SC_OK);
        mockWebServer.enqueue(mockResponse);
    }

    private HttpClientBuilder throwingHttpClientBuilder() throws IOException {
        var httpClientBuilder = mock(HttpClientBuilder.class);
        var closeableHttpClient = mock(CloseableHttpClient.class);
        when(httpClientBuilder.build())
                .thenReturn(closeableHttpClient);
        when(closeableHttpClient.execute(any()))
                .thenThrow(new IOException());

        return httpClientBuilder;
    }

    @Nested
    class Get {

        @Test
        void shouldThrowGivenResponseThrows() {
            var mockResponse = new MockResponse()
                    .setBody("testBody")
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY);
            mockWebServer.enqueue(mockResponse);

            assertThatThrownBy(() -> httpClient.get(uri))
                    .isInstanceOf(RestUserProviderException.class);
        }

        @Test
        void shouldThrowGivenRequestThrows() throws IOException {
            HttpClientBuilder httpClientBuilder = throwingHttpClientBuilder();
            httpClient = new HttpClient(httpClientBuilder);

            assertThatThrownBy(() -> httpClient.get(uri))
                    .isInstanceOf(RestUserProviderException.class);
        }

        @Test
        void shouldReturnResponseGivenHttpCodeNot200() throws InterruptedException {
            enqueueFailedResponse();

            HttpResponse response = httpClient.get(uri);

            RecordedRequest recordedRequest = Objects.requireNonNull(
                    mockWebServer.takeRequest(5, TimeUnit.SECONDS));
            assertThat(response.body).isNull();
            assertThat(recordedRequest.getMethod()).isEqualTo(HttpGet.METHOD_NAME);
            assertThat(recordedRequest.getPath()).isEqualTo("/");
            assertThat(recordedRequest.getRequestUrl())
                    .extracting(Object::toString)
                    .isEqualTo(uri.toString());
        }

        @ParameterizedTest
        @CsvSource(value = {
                "null, null",
                "null, somePassword",
                "'', somePassword",
                "' ', somePassword",
                "someUser, null",
                "someuser, ''",
                "someuser, ' '"
        }, nullValues = "null")
        void shouldNotEnableBasicAuthGivenCredentialsIncorrect(String basicAuthUser, String basicAuthPassword)
                throws InterruptedException {
            var expectedBody = "anyBody";
            enqueueSuccessfulResponse(expectedBody);

            httpClient.enableBasicAuth(basicAuthUser, basicAuthPassword);
            HttpResponse response = httpClient.get(uri);

            RecordedRequest recordedRequest = Objects.requireNonNull(
                    mockWebServer.takeRequest(5, TimeUnit.SECONDS));
            assertThat(response.body).isEqualTo(expectedBody);
            assertThat(recordedRequest.getMethod()).isEqualTo(HttpGet.METHOD_NAME);
            assertThat(recordedRequest.getPath()).isEqualTo("/");
            assertThat(recordedRequest.getRequestUrl())
                    .extracting(Object::toString)
                    .isEqualTo(uri.toString());
            assertThat(recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION)).isNull();
        }

        @Test
        void shouldGetWithBasicAuth() throws InterruptedException {
            var expectedBody = "anyBody";
            enqueueSuccessfulResponse(expectedBody);
            var username = "username";
            var password = "password";

            httpClient.enableBasicAuth(username, password);
            HttpResponse response = httpClient.get(uri);

            RecordedRequest recordedRequest = Objects.requireNonNull(mockWebServer.takeRequest(5, TimeUnit.SECONDS));
            assertThat(response.body).isEqualTo(expectedBody);
            assertThat(recordedRequest.getMethod()).isEqualTo(HttpGet.METHOD_NAME);
            assertThat(recordedRequest.getPath()).isEqualTo("/");
            assertThat(recordedRequest.getRequestUrl())
                    .extracting(Object::toString)
                    .isEqualTo(uri.toString());
            String authorizationHeader = recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION);
            assertThat(authorizationHeader)
                    .isNotNull()
                    .startsWith("Basic");
            var expectedToken = new String(Base64.encodeBase64(String.format("%s:%s", username, password)
                    .getBytes(StandardCharsets.ISO_8859_1)));
            assertThat(authorizationHeader).endsWith(expectedToken);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " "})
        void shouldNotEnableBearerTokenAuthGivenTokenIncorrect(String bearerToken)
                throws InterruptedException {
            var expectedBody = "anyBody";
            enqueueSuccessfulResponse(expectedBody);

            httpClient.enableBearerTokenAuth(bearerToken);
            HttpResponse response = httpClient.get(uri);

            RecordedRequest recordedRequest = Objects.requireNonNull(mockWebServer.takeRequest(5, TimeUnit.SECONDS));
            assertThat(response.body).isEqualTo(expectedBody);
            assertThat(recordedRequest.getMethod()).isEqualTo(HttpGet.METHOD_NAME);
            assertThat(recordedRequest.getPath()).isEqualTo("/");
            assertThat(recordedRequest.getRequestUrl())
                    .extracting(Object::toString)
                    .isEqualTo(uri.toString());
            assertThat(recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION)).isNull();
        }

        @Test
        void shouldGetWithBearerTokenAuth() throws InterruptedException {
            var expectedBody = "anyBody";
            enqueueSuccessfulResponse(expectedBody);
            var token = "token";
            httpClient.enableBearerTokenAuth(token);
            HttpResponse response = httpClient.get(uri);

            RecordedRequest recordedRequest = Objects.requireNonNull(mockWebServer.takeRequest(5, TimeUnit.SECONDS));
            assertThat(response.body).isEqualTo(expectedBody);
            assertThat(recordedRequest.getMethod()).isEqualTo(HttpGet.METHOD_NAME);
            assertThat(recordedRequest.getPath()).isEqualTo("/");
            assertThat(recordedRequest.getRequestUrl())
                    .extracting(Object::toString)
                    .isEqualTo(uri.toString());
            String authorization = recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION);
            assertThat(authorization)
                    .isNotNull()
                    .startsWith("Bearer")
                    .endsWith(token);
        }

        @Test
        void shouldSendAGetRequest() throws InterruptedException {
            var expectedBody = "anyBody";
            enqueueSuccessfulResponse(expectedBody);

            HttpResponse response = httpClient.get(uri);

            RecordedRequest recordedRequest = Objects.requireNonNull(
                    mockWebServer.takeRequest(5, TimeUnit.SECONDS));
            assertThat(response.body).isEqualTo(expectedBody);
            assertThat(recordedRequest.getMethod()).isEqualTo(HttpGet.METHOD_NAME);
            assertThat(recordedRequest.getPath()).isEqualTo("/");
            assertThat(recordedRequest.getRequestUrl())
                    .extracting(Object::toString)
                    .isEqualTo(uri.toString());
            assertThat(recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION)).isNull();
        }

        @Test
        void shouldHandleRedirectsGivenLaxRedirectStrategyIsUsed() throws InterruptedException {
            var expectedBody = "anyBody";
            var redirectedUri = uri + "redirected";

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_MOVED_TEMPORARILY)
                    .setHeader("Location", redirectedUri));
            enqueueSuccessfulResponse(expectedBody);

            HttpResponse response = httpClient.get(uri);

            mockWebServer.takeRequest(5, TimeUnit.SECONDS);
            RecordedRequest recordedRequest = Objects.requireNonNull(
                    mockWebServer.takeRequest(5, TimeUnit.SECONDS));
            assertThat(recordedRequest.getMethod()).isEqualTo(HttpGet.METHOD_NAME);
            assertThat(recordedRequest.getPath()).isEqualTo("/redirected");
            assertThat(recordedRequest.getRequestUrl())
                    .extracting(Object::toString)
                    .isEqualTo(redirectedUri);
            assertThat(recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION)).isNull();

            assertThat(response.body).isEqualTo(expectedBody);
            assertThat(response.getCode()).isEqualTo(HttpStatus.SC_OK);
        }

        @Test
        void shouldSupportCustomResponseEncoding() throws InterruptedException {
            var expectedBody = "anyBody";
            var mockResponse = new MockResponse()
                    .setBody(expectedBody)
                    .addHeader("Content-Type", "ISO-8859-1")
                    .setResponseCode(HttpStatus.SC_OK);
            mockWebServer.enqueue(mockResponse);

            HttpResponse response = httpClient.get(uri);

            RecordedRequest recordedRequest = Objects.requireNonNull(
                    mockWebServer.takeRequest(5, TimeUnit.SECONDS));
            assertThat(response.body).isEqualTo(expectedBody);
            assertThat(recordedRequest.getMethod()).isEqualTo(HttpGet.METHOD_NAME);
            assertThat(recordedRequest.getPath()).isEqualTo("/");
            assertThat(recordedRequest.getRequestUrl())
                    .extracting(Object::toString)
                    .isEqualTo(uri.toString());
            assertThat(recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION)).isNull();
        }
    }

    @Nested
    class Post {

        @Test
        void shouldThrowGivenResponseThrows() {
            var mockResponse = new MockResponse()
                    .setBody("testBody")
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY);
            mockWebServer.enqueue(mockResponse);

            assertThatThrownBy(() -> httpClient.post(uri, "{}"))
                    .isInstanceOf(HttpRequestException.class);
        }

        @Test
        void shouldThrowGivenRequestThrows() throws IOException {
            var httpClientBuilder = throwingHttpClientBuilder();
            httpClient = new HttpClient(httpClientBuilder);

            assertThatThrownBy(() -> httpClient.post(uri, "{}"))
                    .isInstanceOf(RestUserProviderException.class);
        }

        @Test
        void shouldSendAPostRequest() throws InterruptedException {
            var expectedBody = "anyBody";
            enqueueSuccessfulResponse(expectedBody);

            HttpResponse response = httpClient.post(uri, expectedBody);

            RecordedRequest recordedRequest = Objects.requireNonNull(
                    mockWebServer.takeRequest(5, TimeUnit.SECONDS));
            assertThat(response.body).isEqualTo(expectedBody);
            assertThat(recordedRequest.getMethod()).isEqualTo(HttpPost.METHOD_NAME);
            assertThat(recordedRequest.getPath()).isEqualTo("/");
            assertThat(recordedRequest.getRequestUrl())
                    .extracting(Object::toString)
                    .isEqualTo(uri.toString());
        }

        @Test
        void shouldHandleRedirectsGivenLaxRedirectStrategyIsUsed() throws InterruptedException {
            var expectedBody = "anyBody";
            var redirectedUri = uri + "redirected";

            mockWebServer.enqueue(new MockResponse().setResponseCode(HttpStatus.SC_TEMPORARY_REDIRECT)
                    .setHeader("Location", redirectedUri));
            enqueueSuccessfulResponse(expectedBody);

            HttpResponse response = httpClient.post(uri, "{}");

            mockWebServer.takeRequest(5, TimeUnit.SECONDS);
            RecordedRequest recordedRequest = Objects.requireNonNull(
                    mockWebServer.takeRequest(5, TimeUnit.SECONDS));
            assertThat(recordedRequest.getMethod()).isEqualTo(HttpPost.METHOD_NAME);
            assertThat(recordedRequest.getPath()).isEqualTo("/redirected");
            assertThat(recordedRequest.getRequestUrl())
                    .extracting(Object::toString)
                    .isEqualTo(redirectedUri);
            assertThat(recordedRequest.getHeader(HttpHeaders.AUTHORIZATION)).isNull();

            assertThat(response.body).isEqualTo(expectedBody);
            assertThat(response.getCode()).isEqualTo(HttpStatus.SC_OK);
        }

        @Test
        void shouldSupportCustomResponseEncoding() throws InterruptedException {
            var expectedBody = "anyBody";
            var mockResponse = new MockResponse()
                    .setBody(expectedBody)
                    .addHeader("Content-Type", "ISO-8859-1")
                    .setResponseCode(HttpStatus.SC_OK);
            mockWebServer.enqueue(mockResponse);

            HttpResponse response = httpClient.post(uri, expectedBody);

            RecordedRequest recordedRequest = Objects.requireNonNull(
                    mockWebServer.takeRequest(5, TimeUnit.SECONDS));
            assertThat(response.body).isEqualTo(expectedBody);
            assertThat(recordedRequest.getMethod()).isEqualTo(HttpPost.METHOD_NAME);
            assertThat(recordedRequest.getPath()).isEqualTo("/");
            assertThat(recordedRequest.getRequestUrl())
                    .extracting(Object::toString)
                    .isEqualTo(uri.toString());
        }

        @Test
        void shouldReturnResponseGivenHttpCodeNot200() throws InterruptedException {
            enqueueFailedResponse();

            HttpResponse response = httpClient.post(uri, "{}");

            RecordedRequest recordedRequest = Objects.requireNonNull(
                    mockWebServer.takeRequest(5, TimeUnit.SECONDS));
            assertThat(response.body).isNull();
            assertThat(recordedRequest.getMethod()).isEqualTo(HttpPost.METHOD_NAME);
            assertThat(recordedRequest.getPath()).isEqualTo("/");
            assertThat(recordedRequest.getRequestUrl())
                    .extracting(Object::toString)
                    .isEqualTo(uri.toString());
        }

        @Test
        void shouldBeSentWithBasicAuthGivenBasicAuthIsEnabled() throws InterruptedException {
            var expectedBody = "anyBody";
            enqueueSuccessfulResponse(expectedBody);
            var username = "username";
            var password = "password";
            httpClient.enableBasicAuth(username, password);

            HttpResponse response = httpClient.post(uri, expectedBody);

            var token = new String(Base64.encodeBase64(String.format("%s:%s", username, password)
                    .getBytes(StandardCharsets.ISO_8859_1)));
            RecordedRequest recordedRequest = Objects.requireNonNull(mockWebServer.takeRequest(5, TimeUnit.SECONDS));
            var authorizationHeader = recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION);
            assertThat(response.body).isEqualTo(expectedBody);
            assertThat(recordedRequest.getMethod()).isEqualTo(HttpPost.METHOD_NAME);
            assertThat(recordedRequest.getPath()).isEqualTo("/");
            assertThat(recordedRequest.getRequestUrl())
                    .extracting(Object::toString)
                    .isEqualTo(uri.toString());
            assertThat(authorizationHeader)
                    .isNotNull()
                    .startsWith("Basic")
                    .endsWith(token);
        }

        @Test
        void shouldBeSentWithBearerAuthGivenBasicAuthIsEnabled() throws InterruptedException {
            var expectedBody = "anyBody";
            var token = "token";
            enqueueSuccessfulResponse(expectedBody);
            httpClient.enableBearerTokenAuth(token);

            HttpResponse response = httpClient.post(uri, expectedBody);

            RecordedRequest recordedRequest = Objects.requireNonNull(
                    mockWebServer.takeRequest(5, TimeUnit.SECONDS));
            assertThat(response.body).isEqualTo(expectedBody);
            assertThat(recordedRequest.getMethod()).isEqualTo(HttpPost.METHOD_NAME);
            assertThat(recordedRequest.getPath()).isEqualTo("/");
            assertThat(recordedRequest.getRequestUrl())
                    .extracting(Object::toString)
                    .isEqualTo(uri.toString());
            String authorization = recordedRequest.getHeaders().get(HttpHeaders.AUTHORIZATION);
            assertThat(authorization)
                    .isNotNull()
                    .startsWith("Bearer")
                    .endsWith(token);
        }
    }

    @Nested
    class Configuration {

        @Test
        void shouldSetConnectAndReadTimeouts() {
            var httpClientBuilder = mock(HttpClientBuilder.class);
            httpClient = new HttpClient(httpClientBuilder);

            httpClient.setConnectTimeout(3000);
            httpClient.setReadTimeout(5000);

            var requestConfig = ArgumentCaptor.forClass(RequestConfig.class);
            verify(httpClientBuilder, atLeastOnce()).setDefaultRequestConfig(requestConfig.capture());
            assertThat(requestConfig.getValue().getConnectTimeout())
                    .isEqualTo(3000);
            assertThat(requestConfig.getValue().getSocketTimeout())
                    .isEqualTo(5000);
        }

        @Test
        void shouldThrowGivenReadTimeoutIsExceeded() {
            httpClient.setReadTimeout(10);
            mockWebServer.enqueue(new MockResponse().setHeadersDelay(5, TimeUnit.SECONDS));

            assertThatThrownBy(() -> httpClient.get(uri))
                    .isInstanceOf(RestUserProviderException.class)
                    .hasCauseInstanceOf(SocketTimeoutException.class);
        }
    }
}
