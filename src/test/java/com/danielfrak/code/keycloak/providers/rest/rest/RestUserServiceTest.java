package com.danielfrak.code.keycloak.providers.rest.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import com.danielfrak.code.keycloak.providers.rest.rest.http.HttpClient;
import com.danielfrak.code.keycloak.providers.rest.rest.http.HttpResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestUserServiceTest {

    private final static String URI_PATH_FORMAT = "%s/%s";
    private final static String URI = "http://localhost:9090";

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
        var username = "anyUsername";
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
    void findByEmailShouldReturnAUserWhenUserIsFound() throws IOException {
        var expectedUser = createAnyLegacyUser();
        var response = new HttpResponse(HttpStatus.SC_OK, objectMapper.writeValueAsString(expectedUser));
        var path = String.format(URI_PATH_FORMAT, URI, expectedUser.getEmail());
        when(httpClient.get(path)).thenReturn(response);
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());

        var result = restUserService.findByEmail(expectedUser.getEmail());

        assertTrue(result.isPresent());
        assertEquals(result.get(), expectedUser);
    }

    @NotNull
    private LegacyUser createAnyLegacyUser() {
        var legacyUser = new LegacyUser();
        legacyUser.setEmail("any@email.com");
        legacyUser.setUsername("anyUsername");
        legacyUser.setRoles(List.of("admin"));
        legacyUser.setGroups(List.of("migrated_users"));
        legacyUser.setRequiredActions(List.of("CONFIGURE_TOTP"));
        legacyUser.setFirstName("Bob");
        legacyUser.setLastName("Smith");
        legacyUser.setEnabled(true);
        legacyUser.setEmailVerified(true);
        legacyUser.setAttributes(Map.of("position", List.of("rockstar-developer")));
        return legacyUser;
    }

    @Test
    void findByEmailShouldReturnAnEmptyOptionalWhenUsernameIsNotFound() {
        var expectedUser = createAnyLegacyUser();
        var path = String.format(URI_PATH_FORMAT, URI, expectedUser.getEmail());
        var response = new HttpResponse(HttpStatus.SC_NOT_FOUND);
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());
        when(httpClient.get(path)).thenReturn(response);

        var result = restUserService.findByUsername(expectedUser.getEmail());

        assertTrue(result.isEmpty());
    }


    @Test
    void findByUsernameShouldReturnAUserWhenUserIsFound() throws IOException {
        var expectedUser = createAnyLegacyUser();
        var path = String.format(URI_PATH_FORMAT, URI, expectedUser.getUsername());
        var response = new HttpResponse(HttpStatus.SC_OK, objectMapper.writeValueAsString(expectedUser));
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());
        when(httpClient.get(path)).thenReturn(response);

        var result = restUserService.findByUsername(expectedUser.getUsername());

        assertTrue(result.isPresent());
        assertEquals(result.get(), expectedUser);
    }

    @Test
    void findByUsernameShouldReturnAnEmptyOptionalWhenUsernameIsNotFound() {
        var expectedUser = createAnyLegacyUser();
        var path = String.format(URI_PATH_FORMAT, URI, expectedUser.getUsername());
        var response = new HttpResponse(HttpStatus.SC_NOT_FOUND);
        when(httpClient.get(path)).thenReturn(response);
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());

        var result = restUserService.findByUsername(expectedUser.getUsername());

        assertTrue(result.isEmpty());
    }

    @Test
    void isPasswordValidShouldReturnTrueWhenPasswordsMatches() throws IOException {
        var username = "anyUsername";
        var password = "anyPassword";
        var path = String.format(URI_PATH_FORMAT, URI, username);
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());
        var response = new HttpResponse(HttpStatus.SC_OK);
        var expectedBody = objectMapper.writeValueAsString(new UserPasswordDto(password));
        when(httpClient.post(path, expectedBody)).thenReturn(response);

        var isPasswordValid = restUserService.isPasswordValid(username, password);

        assertTrue(isPasswordValid);
    }

    @Test
    void isPasswordValidShouldReturnFalseWhenPasswordsDoNotMatch() {
        var username = "anyUsername";
        var password = "anyPassword";
        var path = String.format(URI_PATH_FORMAT, URI, username);
        var restUserService = new RestUserService(model, httpClient, new ObjectMapper());
        var response = new HttpResponse(HttpStatus.SC_NOT_FOUND);
        when(httpClient.post(eq(path), anyString())).thenReturn(response);

        var isPasswordValid = restUserService.isPasswordValid(username, password);

        assertFalse(isPasswordValid);
    }
}