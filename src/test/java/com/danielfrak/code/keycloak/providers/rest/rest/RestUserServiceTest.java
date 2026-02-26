package com.danielfrak.code.keycloak.providers.rest.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyOrganization;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyTotp;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import com.danielfrak.code.keycloak.providers.rest.rest.http.HttpClient;
import com.danielfrak.code.keycloak.providers.rest.rest.http.HttpResponse;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.keycloak.common.util.Encode;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void shouldCallEnableApiToken() {
        var token = "anyToken";
        enableApiToken(token);

        new RestUserService(model, httpClient, new ObjectMapper());

        verify(httpClient).enableBearerTokenAuth(token);
    }

    private void enableApiToken(String token) {
        config.putSingle(API_TOKEN_ENABLED_PROPERTY, Boolean.TRUE.toString());
        config.putSingle(API_TOKEN_PROPERTY, token);
    }

    @Test
    void shouldCallEnableEnableBasicAuth() {
        var username = "someUsername";
        var password = "anyPassword";
        enableBasicAuth(username, password);

        new RestUserService(model, httpClient, new ObjectMapper());

        verify(httpClient).enableBasicAuth(username, password);
    }

    private void enableBasicAuth(String httpBasicAuthUsername, String httpBasicAuthPassword) {
        config.putSingle(API_HTTP_BASIC_ENABLED_PROPERTY, Boolean.TRUE.toString());
        config.putSingle(API_HTTP_BASIC_USERNAME_PROPERTY, httpBasicAuthUsername);
        config.putSingle(API_HTTP_BASIC_PASSWORD_PROPERTY, httpBasicAuthPassword);
    }

    @Test
    void findByEmailShouldReturnEmptyWhenRuntimeExceptionOccurs() {
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());
        var cause = new RuntimeException();

        when(httpClient.get(any()))
                .thenThrow(cause);

        var result = restUserService.findByEmail("someEmail");

        assertThat(result).isEmpty();
    }

    @Test
    void findByEmailShouldReturnEmptyWhenIOExceptionOccurs() {
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());

        when(httpClient.get(any()))
                .thenReturn(new HttpResponse(200, "malformedJson"));

        var result = restUserService.findByEmail("someEmail");

        assertThat(result).isEmpty();
    }

    @Test
    void findByEmailShouldReturnAUserWhenUserIsFoundAndEmailMatches() throws IOException {
        var expectedUser = createALegacyUser("someUsername", "email@example.com");
        var response = new HttpResponse(HttpStatus.SC_OK, objectMapper.writeValueAsString(expectedUser));
        var path = String.format(URI_PATH_FORMAT, URI, Encode.urlEncode(expectedUser.email()));
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());

        when(httpClient.get(path)).thenReturn(response);

        var result = restUserService.findByEmail(expectedUser.email());

        assertThat(result).get().isEqualTo(expectedUser);
    }

    @NotNull
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
                List.of(new LegacyTotp("someSecret", "someName", 1, 2,
                        "someAlgorithm", "someEncoding")),
                List.of(new LegacyOrganization("org-1", "org-1", List.of()))
        );
    }

    @Test
    void findByEmailShouldReturnAUserWhenUserIsFoundAndEmailMatchesCaseInsensitive() throws IOException {
        var expectedUser = createALegacyUser("someUsername", "email@example.com");
        var response = new HttpResponse(HttpStatus.SC_OK, objectMapper.writeValueAsString(expectedUser));
        var path = String.format(URI_PATH_FORMAT, URI, Encode.urlEncode("EMAIL@EXAMPLE.COM"));
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());

        when(httpClient.get(path)).thenReturn(response);

        var result = restUserService.findByEmail("EMAIL@EXAMPLE.COM");

        assertThat(result).get().isEqualTo(expectedUser);
    }

    @Test
    void findByEmailShouldReturnAnEmptyOptionalWhenUserIsNotFound() {
        var expectedUser = createALegacyUser("someUsername", "email@example.com");
        var path = String.format(URI_PATH_FORMAT, URI, Encode.urlEncode(expectedUser.email()));
        var response = new HttpResponse(HttpStatus.SC_NOT_FOUND);
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());
        when(httpClient.get(path)).thenReturn(response);

        var result = restUserService.findByUsername(expectedUser.email());

        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "someEmail, differentEmail",
            "null, someEmail",
            "someEmail, null"
    }, nullValues = "null")
    void findByUsernameShouldReturnAnEmptyOptionalWhenEmailDoesNotMatch(
            String requestedEmail, String returnedEmail) throws JsonProcessingException {
        var expectedUser = createALegacyUser("someUsername", returnedEmail);
        var path = String.format(URI_PATH_FORMAT, URI, requestedEmail);
        var response = new HttpResponse(HttpStatus.SC_OK, objectMapper.writeValueAsString(expectedUser));
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());

        when(httpClient.get(path)).thenReturn(response);

        var result = restUserService.findByEmail(requestedEmail);

        assertThat(result).isEmpty();
    }

    @Test
    void findByUsernameShouldReturnEmptyWhenRuntimeExceptionOccurs() {
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());
        var cause = new RuntimeException();

        when(httpClient.get(any()))
                .thenThrow(cause);

        var result = restUserService.findByUsername("someUsername");

        assertThat(result).isEmpty();
    }

    @Test
    void findByUsernameShouldReturnEmptyWhenIOExceptionOccurs() {
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());

        when(httpClient.get(any()))
                .thenReturn(new HttpResponse(200, "malformedJson"));

        var result = restUserService.findByUsername("someUsername");

        assertThat(result).isEmpty();
    }

    @Test
    void findByUsernameShouldReturnAUserWhenUserIsFoundAndUsernameMatches() throws IOException {
        var expectedUser = createALegacyUser("someUsername", "email@example.com");
        var path = String.format(URI_PATH_FORMAT, URI, expectedUser.username());
        var response = new HttpResponse(HttpStatus.SC_OK, objectMapper.writeValueAsString(expectedUser));
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());
        when(httpClient.get(path)).thenReturn(response);

        Optional<LegacyUser> result = restUserService.findByUsername(expectedUser.username());

        assertThat(result).get().isEqualTo(expectedUser);
    }

    @Test
    void findByUsernameShouldReturnAUserWhenUserIsFoundAndUsernameMatchesCaseInsensitive() throws IOException {
        var expectedUser = createALegacyUser("someUsername", "email@example.com");
        var path = String.format(URI_PATH_FORMAT, URI, "SOMEUSERNAME");
        var response = new HttpResponse(HttpStatus.SC_OK, objectMapper.writeValueAsString(expectedUser));
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());
        when(httpClient.get(path)).thenReturn(response);

        var result = restUserService.findByUsername("SOMEUSERNAME");

        assertThat(result).get().isEqualTo(expectedUser);
    }

    @Test
    void findByUsernameShouldReturnAUserWhenUserIsFoundAndUsernameContainsSpace() throws IOException {
        var expectedUser = createALegacyUser("some Username", "email@example.com");
        var path = String.format(URI_PATH_FORMAT, URI, "SOME+USERNAME");
        var response = new HttpResponse(HttpStatus.SC_OK, objectMapper.writeValueAsString(expectedUser));
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());
        when(httpClient.get(path)).thenReturn(response);

        var result = restUserService.findByUsername("SOME USERNAME");

        assertThat(result).get().isEqualTo(expectedUser);
    }

    @Test
    void findByUsernameShouldReturnAnEmptyOptionalWhenUserIsNotFound() {
        var expectedUser = createALegacyUser("someUsername", "email@example.com");
        var path = String.format(URI_PATH_FORMAT, URI, expectedUser.username());
        var response = new HttpResponse(HttpStatus.SC_NOT_FOUND);
        when(httpClient.get(path)).thenReturn(response);
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());

        var result = restUserService.findByUsername(expectedUser.username());

        assertThat(result).isEmpty();
    }

    @Test
    void findByUsernameShouldReturnAnEmptyOptionalWhenUsernameDoesNotMatch() throws JsonProcessingException {
        var expectedUser = createALegacyUser("differentUsername", "email@example.com");
        var path = String.format(URI_PATH_FORMAT, URI, "someUsername");
        var response = new HttpResponse(HttpStatus.SC_OK, objectMapper.writeValueAsString(expectedUser));
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());

        when(httpClient.get(path)).thenReturn(response);

        var result = restUserService.findByUsername("someUsername");

        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "someUsername, differentUsername",
            "null, someUsername",
            "someUsername, null"
    }, nullValues = {"null"})
    void findByUsernameShouldReturnAnEmptyOptionalWhenUsernameDoesNotMatch(
            String requestedUsername, String returnedUsername) throws JsonProcessingException {
        var expectedUser = createALegacyUser(returnedUsername, "email@example.com");
        var path = String.format(URI_PATH_FORMAT, URI, requestedUsername);
        var response = new HttpResponse(HttpStatus.SC_OK, objectMapper.writeValueAsString(expectedUser));
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());

        when(httpClient.get(path)).thenReturn(response);

        var result = restUserService.findByUsername(requestedUsername);

        assertThat(result).isEmpty();
    }

    @Test
    void isPasswordValidShouldReturnTrueWhenPasswordsMatches() throws IOException {
        var username = "someUsername";
        var password = "anyPassword";
        var path = String.format(URI_PATH_FORMAT, URI, username);
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());
        var response = new HttpResponse(HttpStatus.SC_OK);
        var expectedBody = objectMapper.writeValueAsString(new UserPasswordDto(password));
        when(httpClient.post(path, expectedBody)).thenReturn(response);

        var isPasswordValid = restUserService.isPasswordValid(username, password);

        assertThat(isPasswordValid).isTrue();
    }

    @Test
    void isPasswordValidShouldNotThrowNullPointerExceptionWhenPasswordIsNull() throws IOException {
        String username = null;
        var password = "anyPassword";
        var path = String.format(URI_PATH_FORMAT, URI, username);
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());
        var response = new HttpResponse(HttpStatus.SC_OK);
        var expectedBody = objectMapper.writeValueAsString(new UserPasswordDto(password));
        when(httpClient.post(path, expectedBody)).thenReturn(response);

        var isPasswordValid = restUserService.isPasswordValid(username, password);

        assertThat(isPasswordValid).isTrue();
    }

    @Test
    void isPasswordValidShouldReturnTrueWhenPasswordsMatchesAndUsernameContainsSpace() throws IOException {
        var username = "some Username";
        var password = "anyPassword";
        var path = String.format(URI_PATH_FORMAT, URI, "some+Username");
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());
        var response = new HttpResponse(HttpStatus.SC_OK);
        var expectedBody = objectMapper.writeValueAsString(new UserPasswordDto(password));
        when(httpClient.post(path, expectedBody)).thenReturn(response);

        var isPasswordValid = restUserService.isPasswordValid(username, password);

        assertThat(isPasswordValid).isTrue();
    }

    @Test
    void isPasswordValidShouldReturnFalseWhenPasswordsDoNotMatch() {
        var username = "someUsername";
        var password = "anyPassword";
        var path = String.format(URI_PATH_FORMAT, URI, username);
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());
        var response = new HttpResponse(HttpStatus.SC_NOT_FOUND);
        when(httpClient.post(eq(path), anyString())).thenReturn(response);

        var isPasswordValid = restUserService.isPasswordValid(username, password);

        assertThat(isPasswordValid).isFalse();
    }

    @Test
    void isPasswordValidShouldReturnFalseWhenIOExceptionOccurs() throws JsonProcessingException {
        var mockObjectMapper = mock(ObjectMapper.class);
        var cause = mock(JsonProcessingException.class);
        var restUserService = new RestUserService(model, httpClient, mockObjectMapper);

        when(mockObjectMapper.writeValueAsString(any()))
                .thenThrow(cause);

        var isPasswordValid = restUserService.isPasswordValid(
                "someUsername", "somePassword");
        assertThat(isPasswordValid).isFalse();
    }
}