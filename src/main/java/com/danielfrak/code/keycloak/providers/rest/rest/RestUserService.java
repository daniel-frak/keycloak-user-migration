package com.danielfrak.code.keycloak.providers.rest.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUserService;
import com.danielfrak.code.keycloak.providers.rest.exceptions.RestUserProviderException;
import com.danielfrak.code.keycloak.providers.rest.rest.http.HttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.keycloak.common.util.Encode;
import org.keycloak.component.ComponentModel;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;

public class RestUserService implements LegacyUserService {

    private final String uri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RestUserService(ComponentModel model, HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.uri = model.getConfig().getFirst(URI_PROPERTY);
        this.objectMapper = objectMapper;

        configureBasicAuth(model, httpClient);
        configureBearerTokenAuth(model, httpClient);
    }

    private void configureBasicAuth(ComponentModel model, HttpClient httpClient) {
        var basicAuthConfig = model.getConfig().getFirst(API_HTTP_BASIC_ENABLED_PROPERTY);
        var basicAuthEnabled = Boolean.parseBoolean(basicAuthConfig);
        if (basicAuthEnabled) {
            String basicAuthUser = model.getConfig().getFirst(API_HTTP_BASIC_USERNAME_PROPERTY);
            String basicAuthPassword = model.getConfig().getFirst(API_HTTP_BASIC_PASSWORD_PROPERTY);
            httpClient.enableBasicAuth(basicAuthUser, basicAuthPassword);
        }
    }

    private void configureBearerTokenAuth(ComponentModel model, HttpClient httpClient) {
        var tokenAuthEnabled = Boolean.parseBoolean(model.getConfig().getFirst(API_TOKEN_ENABLED_PROPERTY));
        if (tokenAuthEnabled) {
            String token = model.getConfig().getFirst(API_TOKEN_PROPERTY);
            httpClient.enableBearerTokenAuth(token);
        }
    }

    @Override
    public Optional<LegacyUser> findByEmail(String email) {
        return findLegacyUser(email)
                .filter(u -> equalsCaseInsensitive(email, u.getEmail()));
    }

    private boolean equalsCaseInsensitive(String a, String b) {
        if(a == null || b == null) {
            return false;
        }

        return a.toUpperCase(Locale.ROOT).equals(b.toUpperCase(Locale.ROOT));
    }

    @Override
    public Optional<LegacyUser> findByUsername(String username) {
        return findLegacyUser(username)
                .filter(u -> equalsCaseInsensitive(username, u.getUsername()));
    }

    private Optional<LegacyUser> findLegacyUser(String usernameOrEmail) {
        if (usernameOrEmail != null) {
            usernameOrEmail = Encode.urlEncode(usernameOrEmail);
        }
        var getUsernameUri = String.format("%s/%s", this.uri, usernameOrEmail);
        try {
            var response = this.httpClient.get(getUsernameUri);
            if (response.getCode() != HttpStatus.SC_OK) {
                return Optional.empty();
            }
            var legacyUser = objectMapper.readValue(response.getBody(), LegacyUser.class);
            return Optional.ofNullable(legacyUser);
        } catch (RuntimeException|IOException e) {
            throw new RestUserProviderException(e);
        }
    }

    @Override
    public boolean isPasswordValid(String username, String password) {
        if (username != null) {
            username = Encode.urlEncode(username);
        }
        var passwordValidationUri = String.format("%s/%s", this.uri, username);
        var dto = new UserPasswordDto(password);
        try {
            var json = objectMapper.writeValueAsString(dto);
            var response = httpClient.post(passwordValidationUri, json);
            return response.getCode() == HttpStatus.SC_OK;
        } catch (IOException e) {
            throw new RestUserProviderException(e);
        }
    }
}
