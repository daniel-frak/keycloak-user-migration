package com.danielfrak.code.keycloak.providers.rest.rest;

import com.danielfrak.code.keycloak.providers.rest.LogCapture;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyOrganization;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyOrganizationDomain;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyTotp;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import com.danielfrak.code.keycloak.providers.rest.rest.http.HttpClient;
import com.danielfrak.code.keycloak.providers.rest.rest.http.HttpRequestException;
import com.danielfrak.code.keycloak.providers.rest.rest.http.HttpResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestUserServiceTest {

    private static final String URI_PATH_FORMAT = "%s/%s";
    private static final String URI = "http://localhost:9090";

    private ObjectMapper objectMapper;
    private MultivaluedHashMap<String, String> config;

    @Mock
    private HttpClient httpClient;

    @Mock
    private ComponentModel model;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        registerBasicConfig();
    }

    private void registerBasicConfig() {
        config = new MultivaluedHashMap<>();
        config.putSingle(URI_PROPERTY, URI);
        when(model.getConfig()).thenReturn(config);
    }

    private LegacyUser createALegacyUser(String username, String email) {
        return new LegacyUser(
                "someId",
                username,
                email,
                "Bob",
                "Smith",
                true,
                true,
                Map.of("position", List.of("rockstar-developer")),
                List.of("admin"),
                List.of("migrated_users"),
                List.of("CONFIGURE_TOTP"),
                List.of(
                        new LegacyTotp(
                                "someSecret",
                                "someName",
                                1,
                                2,
                                "someAlgorithm",
                                "someEncoding"
                        )
                ),
                List.of(
                        new LegacyOrganization(
                                "org-1",
                                "org-1",
                                List.of(
                                        new LegacyOrganizationDomain(
                                                "org-1.local",
                                                true
                                        )
                                )
                        )
                )
        );
    }

    private void httpGetRequestThrowsException(RuntimeException exception) {
        when(httpClient.get(any()))
                .thenThrow(exception);
    }

    private void httpGetRequestThrowsExceptionWithCauseButNoMessage() {
        httpGetRequestThrowsException(
                new HttpRequestException(
                        new HttpGet(URI + "/someone%40example.com"),
                        new EOFException()
                )
        );
    }

    private void httpGetRequestThrowsExceptionWithNoCause() {
        httpGetRequestThrowsException(new IllegalStateException("Connection pool shut down"));
    }

    private void httpGetRequestTimesOut() {
        httpGetRequestThrowsException(
                new HttpRequestException(
                        new HttpGet(URI),
                        new SocketTimeoutException("Read timed out")
                )
        );
    }

    private void componentModelHasName() {
        when(model.getName()).thenReturn("someProvider");
    }

    private void httpGetRequestReturns(String path, HttpResponse response) {
        URI uri = java.net.URI.create(path);
        when(httpClient.get(uri))
                .thenReturn(response);
    }

    private void httpGetRequestReturnsMalformedJson() {
        when(httpClient.get(any()))
                .thenReturn(new HttpResponse(200, "malformedJson"));
    }

    private void httpGetRequestThrows(RuntimeException cause) {
        when(httpClient.get(any()))
                .thenThrow(cause);
    }

    private ObjectMapper serializationExceptionThrowingObjectMapper() throws JsonProcessingException {
        var mockObjectMapper = mock(ObjectMapper.class);
        when(mockObjectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("Cannot serialize") {
                });
        return mockObjectMapper;
    }

    private void httpPostRequestThrows(HttpRequestException exception) {
        when(httpClient.post(any(), anyString()))
                .thenThrow(exception);
    }

    private ObjectMapper throwingObjectMapper() throws JsonProcessingException {
        var mockObjectMapper = mock(ObjectMapper.class);
        var cause = mock(JsonProcessingException.class);
        when(mockObjectMapper.writeValueAsString(any()))
                .thenThrow(cause);
        return mockObjectMapper;
    }

    private void httpPostRequestReturns(String path, HttpResponse response) {
        URI uri = java.net.URI.create(path);
        when(httpClient.post(eq(uri), any()))
                .thenReturn(response);
    }

    @Nested
    class EnableBearerTokenAuth {

        @Test
        void shouldMakeRemoteCall() {
            var token = "anyToken";
            enableApiToken(token);

            new RestUserService(model, httpClient, objectMapper);

            verify(httpClient).enableBearerTokenAuth(token);
        }

        private void enableApiToken(String token) {
            config.putSingle(API_TOKEN_ENABLED_PROPERTY, Boolean.TRUE.toString());
            config.putSingle(API_TOKEN_PROPERTY, token);
        }
    }

    @Nested
    class EnableBasicAuth {

        @Test
        void shouldMakeRemoteCall() {
            var username = "someUsername";
            var password = "anyPassword";
            enableBasicAuth(username, password);

            new RestUserService(model, httpClient, objectMapper);

            verify(httpClient).enableBasicAuth(username, password);
        }

        private void enableBasicAuth(String httpBasicAuthUsername, String httpBasicAuthPassword) {
            config.putSingle(API_HTTP_BASIC_ENABLED_PROPERTY, Boolean.TRUE.toString());
            config.putSingle(API_HTTP_BASIC_USERNAME_PROPERTY, httpBasicAuthUsername);
            config.putSingle(API_HTTP_BASIC_PASSWORD_PROPERTY, httpBasicAuthPassword);
        }
    }

    @Nested
    class HttpTimeouts {

        @Test
        void shouldSetConfiguredTimeoutsOnHttpClient() {
            config.putSingle(HTTP_CONNECT_TIMEOUT_PROPERTY, "3000");
            config.putSingle(HTTP_READ_TIMEOUT_PROPERTY, "5000");

            new RestUserService(model, httpClient, objectMapper);

            verify(httpClient).setConnectTimeout(3000);
            verify(httpClient).setReadTimeout(5000);
        }

        @Test
        void shouldNotSetTimeoutsOnHttpClientGivenNotConfigured() {
            new RestUserService(model, httpClient, objectMapper);

            verify(httpClient, never()).setConnectTimeout(anyInt());
            verify(httpClient, never()).setReadTimeout(anyInt());
        }
    }

    @Nested
    class FindByEmail {

        @Test
        void shouldReturnEmptyGivenRuntimeExceptionOccurs() {
            var restUserService = new RestUserService(model, httpClient, objectMapper);
            var cause = new RuntimeException();

            httpGetRequestThrows(cause);

            var result = restUserService.findByEmail("someEmail");

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptyGivenIoExceptionOccurs() {
            var restUserService = new RestUserService(model, httpClient, objectMapper);

            httpGetRequestReturnsMalformedJson();

            var result = restUserService.findByEmail("someEmail");

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnAUserGivenUserIsFoundAndEmailMatches() throws IOException {
            var expectedUser = createALegacyUser("someUsername", "email@example.com");
            var response = new HttpResponse(HttpStatus.SC_OK, objectMapper.writeValueAsString(expectedUser));
            var path = String.format(URI_PATH_FORMAT, URI, expectedUser.email());
            var restUserService = new RestUserService(model, httpClient, objectMapper);

            httpGetRequestReturns(path, response);

            var result = restUserService.findByEmail(expectedUser.email());

            assertThat(result).get().isEqualTo(expectedUser);
        }

        @Test
        void shouldReturnAUserGivenUserIsFoundAndEmailMatchesCaseInsensitive() throws IOException {
            var expectedUser = createALegacyUser("someUsername", "email@example.com");
            var response = new HttpResponse(HttpStatus.SC_OK, objectMapper.writeValueAsString(expectedUser));
            var path = String.format(URI_PATH_FORMAT, URI, "EMAIL@EXAMPLE.COM");
            var restUserService = new RestUserService(model, httpClient, objectMapper);

            httpGetRequestReturns(path, response);

            var result = restUserService.findByEmail("EMAIL@EXAMPLE.COM");

            assertThat(result).get().isEqualTo(expectedUser);
        }

        @Test
        void shouldReturnAnEmptyOptionalGivenUserIsNotFound() {
            var expectedUser = createALegacyUser("someUsername", "email@example.com");
            var path = String.format(URI_PATH_FORMAT, URI, expectedUser.email());
            var response = new HttpResponse(HttpStatus.SC_NOT_FOUND);
            var restUserService = new RestUserService(model, httpClient, objectMapper);
            httpGetRequestReturns(path, response);

            var result = restUserService.findByEmail(expectedUser.email());

            assertThat(result).isEmpty();
        }

        @ParameterizedTest
        @CsvSource(value = {
                "someEmail, differentEmail",
                "someEmail, null"
        }, nullValues = "null")
        void shouldReturnAnEmptyOptionalGivenEmailDoesNotMatch(
                String requestedEmail, String returnedEmail) throws JsonProcessingException {
            var expectedUser = createALegacyUser("someUsername", returnedEmail);
            var path = String.format(URI_PATH_FORMAT, URI, requestedEmail);
            var response = new HttpResponse(HttpStatus.SC_OK, objectMapper.writeValueAsString(expectedUser));
            var restUserService = new RestUserService(model, httpClient, objectMapper);

            httpGetRequestReturns(path, response);

            var result = restUserService.findByEmail(requestedEmail);

            assertThat(result).isEmpty();
        }
        
        @Test
        void shouldReturnAnEmptyOptionalGivenRequestedEmailIsNull() {
            var restUserService = new RestUserService(model, httpClient, objectMapper);

            var result = restUserService.findByEmail(null);

            assertThat(result).isEmpty();
            verifyNoInteractions(httpClient);
        }

        @Test
        void shouldLogProviderAndCauseWithoutUserGivenRequestFails() {
            componentModelHasName();
            var restUserService = new RestUserService(model, httpClient, objectMapper);
            httpGetRequestTimesOut();

            var logs = new LogCapture(RestUserService.class);
            try (logs) {
                restUserService.findByEmail("someone@example.com");
            }

            assertThat(logs.messages()).containsExactly(
                    "Legacy user lookup failed for provider 'someProvider' ("
                            + URI + "): SocketTimeoutException: Read timed out - treating user as not found");
        }

        @Test
        void shouldLogExceptionItselfGivenItHasNoCause() {
            componentModelHasName();
            var restUserService = new RestUserService(model, httpClient, objectMapper);
            httpGetRequestThrowsExceptionWithNoCause();

            var logs = new LogCapture(RestUserService.class);
            try (logs) {
                restUserService.findByEmail("someone@example.com");
            }

            assertThat(logs.messages()).containsExactly(
                    "Legacy user lookup failed for provider 'someProvider' ("
                            + URI + "): IllegalStateException: Connection pool shut down - treating user as not found");
        }

        @Test
        void shouldNotLogRequestWithUserGivenCauseHasNoMessage() {
            componentModelHasName();
            var restUserService = new RestUserService(model, httpClient, objectMapper);
            httpGetRequestThrowsExceptionWithCauseButNoMessage();

            var logs = new LogCapture(RestUserService.class);
            try (logs) {
                restUserService.findByEmail("someone@example.com");
            }

            assertThat(logs.messages()).containsExactly(
                    "Legacy user lookup failed for provider 'someProvider' ("
                            + URI + "): EOFException - treating user as not found");
        }

        @Test
        void shouldLogExceptionTypeGivenThereIsNoMessage() {
            componentModelHasName();
            var restUserService = new RestUserService(model, httpClient, objectMapper);
            httpGetRequestThrowsException(new RuntimeException());

            var logs = new LogCapture(RestUserService.class);
            try (logs) {
                restUserService.findByEmail("someone@example.com");
            }

            assertThat(logs.messages()).containsExactly(
                    "Legacy user lookup failed for provider 'someProvider' ("
                            + URI + "): RuntimeException - treating user as not found");
        }
    }

    @Nested
    class FindByUsername {

        @Test
        void shouldReturnEmptyGivenRuntimeExceptionOccurs() {
            var restUserService = new RestUserService(model, httpClient, objectMapper);
            var cause = new RuntimeException();

            httpGetRequestThrows(cause);

            var result = restUserService.findByUsername("someUsername");

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptyGivenIoExceptionOccurs() {
            var restUserService = new RestUserService(model, httpClient, objectMapper);

            httpGetRequestReturnsMalformedJson();

            var result = restUserService.findByUsername("someUsername");

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnAUserGivenUserIsFoundAndUsernameMatches() throws IOException {
            var expectedUser = createALegacyUser("someUsername", "email@example.com");
            var path = String.format(URI_PATH_FORMAT, URI, expectedUser.username());
            var response = new HttpResponse(HttpStatus.SC_OK, objectMapper.writeValueAsString(expectedUser));
            var restUserService = new RestUserService(model, httpClient, objectMapper);
            httpGetRequestReturns(path, response);

            Optional<LegacyUser> result = restUserService.findByUsername(expectedUser.username());

            assertThat(result).get().isEqualTo(expectedUser);
        }

        @Test
        void shouldReturnAUserGivenUserIsFoundAndUsernameMatchesCaseInsensitive() throws IOException {
            var expectedUser = createALegacyUser("someUsername", "email@example.com");
            var path = String.format(URI_PATH_FORMAT, URI, "SOMEUSERNAME");
            var response = new HttpResponse(HttpStatus.SC_OK, objectMapper.writeValueAsString(expectedUser));
            var restUserService = new RestUserService(model, httpClient, objectMapper);
            httpGetRequestReturns(path, response);

            var result = restUserService.findByUsername("SOMEUSERNAME");

            assertThat(result).get().isEqualTo(expectedUser);
        }

        @Test
        void shouldReturnAUserGivenUserIsFoundAndUsernameContainsSpace() throws IOException {
            var expectedUser = createALegacyUser("some Username", "email@example.com");
            var path = String.format(URI_PATH_FORMAT, URI, "SOME%20USERNAME");
            var response = new HttpResponse(HttpStatus.SC_OK, objectMapper.writeValueAsString(expectedUser));
            var restUserService = new RestUserService(model, httpClient, objectMapper);
            httpGetRequestReturns(path, response);

            var result = restUserService.findByUsername("SOME USERNAME");

            assertThat(result).get().isEqualTo(expectedUser);
        }

        @Test
        void shouldReturnAnEmptyOptionalGivenUserIsNotFound() {
            var expectedUser = createALegacyUser("someUsername", "email@example.com");
            var path = String.format(URI_PATH_FORMAT, URI, expectedUser.username());
            var response = new HttpResponse(HttpStatus.SC_NOT_FOUND);
            httpGetRequestReturns(path, response);
            var restUserService = new RestUserService(model, httpClient, objectMapper);

            var result = restUserService.findByUsername(expectedUser.username());

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnAnEmptyOptionalGivenUsernameDoesNotMatch() throws JsonProcessingException {
            var expectedUser = createALegacyUser("differentUsername", "email@example.com");
            var path = String.format(URI_PATH_FORMAT, URI, "someUsername");
            var response = new HttpResponse(HttpStatus.SC_OK, objectMapper.writeValueAsString(expectedUser));
            var restUserService = new RestUserService(model, httpClient, objectMapper);

            httpGetRequestReturns(path, response);

            var result = restUserService.findByUsername("someUsername");

            assertThat(result).isEmpty();
        }

        @ParameterizedTest
        @CsvSource(value = {
                "someUsername, differentUsername",
                "someUsername, null"
        }, nullValues = "null")
        void shouldReturnAnEmptyOptionalGivenUsernameDoesNotMatch(
                String requestedUsername, String returnedUsername) throws JsonProcessingException {
            var expectedUser = createALegacyUser(returnedUsername, "email@example.com");
            var path = String.format(URI_PATH_FORMAT, URI, requestedUsername);
            var response = new HttpResponse(HttpStatus.SC_OK, objectMapper.writeValueAsString(expectedUser));
            var restUserService = new RestUserService(model, httpClient, objectMapper);

            httpGetRequestReturns(path, response);

            var result = restUserService.findByUsername(requestedUsername);

            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnAnEmptyOptionalGivenRequestedUsernameIsNull() {
            var restUserService = new RestUserService(model, httpClient, objectMapper);

            var result = restUserService.findByUsername(null);

            assertThat(result).isEmpty();
            verifyNoInteractions(httpClient);
        }
    }

    @Nested
    class IsPasswordValid {

        @Test
        void shouldReturnTrueGivenPasswordsMatches() {
            var username = "someUsername";
            var password = "anyPassword";
            var path = String.format(URI_PATH_FORMAT, URI, username);
            var restUserService = new RestUserService(model, httpClient, objectMapper);
            var response = new HttpResponse(HttpStatus.SC_OK);
            httpPostRequestReturns(path, response);

            var isPasswordValid = restUserService.isPasswordValid(username, password);

            assertThat(isPasswordValid).isTrue();
        }

        @Test
        void shouldReturnTrueGivenPasswordsMatchesAndUsernameContainsSpace() {
            var username = "some Username";
            var password = "anyPassword";
            var path = String.format(URI_PATH_FORMAT, URI, "some+Username");
            var restUserService = new RestUserService(model, httpClient, objectMapper);
            var response = new HttpResponse(HttpStatus.SC_OK);
            httpPostRequestReturns(path, response);

            var isPasswordValid = restUserService.isPasswordValid(username, password);

            assertThat(isPasswordValid).isTrue();
        }

        @Test
        void shouldNotThrowNullPointerExceptionGivenPasswordIsNull() {
            String username = null;
            var password = "anyPassword";
            var path = String.format(URI_PATH_FORMAT, URI, username);
            var restUserService = new RestUserService(model, httpClient, objectMapper);
            var response = new HttpResponse(HttpStatus.SC_OK);
            httpPostRequestReturns(path, response);

            var isPasswordValid = restUserService.isPasswordValid(username, password);

            assertThat(isPasswordValid).isTrue();
        }

        @Test
        void shouldReturnFalseGivenPasswordsDoNotMatch() {
            var username = "someUsername";
            var password = "anyPassword";
            var path = String.format(URI_PATH_FORMAT, URI, username);
            var restUserService = new RestUserService(model, httpClient, objectMapper);
            var response = new HttpResponse(HttpStatus.SC_NOT_FOUND);
            httpPostRequestReturns(path, response);

            var isPasswordValid = restUserService.isPasswordValid(username, password);

            assertThat(isPasswordValid).isFalse();
        }

        @Test
        void shouldReturnFalseGivenIoExceptionOccurs() throws JsonProcessingException {
            var mockObjectMapper = throwingObjectMapper();
            var restUserService = new RestUserService(model, httpClient, mockObjectMapper);

            var isPasswordValid = restUserService.isPasswordValid(
                    "someUsername", "somePassword");
            assertThat(isPasswordValid).isFalse();
        }

        @Test
        void shouldRethrowGivenRequestFails() {
            var restUserService = new RestUserService(model, httpClient, objectMapper);
            var exception = new HttpRequestException(new HttpPost(URI), new SocketTimeoutException("Read timed out"));
            httpPostRequestThrows(exception);

            assertThatThrownBy(() -> restUserService.isPasswordValid("someUsername", "somePassword"))
                    .isSameAs(exception);
        }

        @Test
        void shouldLogProviderAndCauseWithoutUserBeforeRethrowing() {
            componentModelHasName();
            var restUserService = new RestUserService(model, httpClient, objectMapper);
            httpPostRequestThrows(
                    new HttpRequestException(
                            new HttpPost(URI),
                            new SocketTimeoutException("Read timed out")
                    )
            );

            var logs = new LogCapture(RestUserService.class);
            Throwable thrown;
            try (logs) {
                thrown = catchThrowable(() -> restUserService.isPasswordValid(
                        "someone@example.com", "somePassword"));
            }

            assertThat(thrown).isInstanceOf(HttpRequestException.class);
            assertThat(logs.messages()).containsExactly("Legacy password validation failed for provider "
                    + "'someProvider' (" + URI + "): SocketTimeoutException: Read timed out - "
                    + "aborting password validation");
        }

        @Test
        void shouldLogProviderAndCauseGivenRequestBodyCannotBeCreated() throws JsonProcessingException {
            componentModelHasName();
            ObjectMapper throwingObjectMapper = serializationExceptionThrowingObjectMapper();
            var restUserService = new RestUserService(model, httpClient, throwingObjectMapper);

            var logs = new LogCapture(RestUserService.class);
            try (logs) {
                restUserService.isPasswordValid("someone@example.com", "somePassword");
            }

            assertThat(logs.messages()).singleElement().asString()
                    .startsWith("Legacy password validation failed for provider 'someProvider' (" + URI + "): ")
                    .endsWith("Cannot serialize - treating password as invalid");
        }
    }
}
