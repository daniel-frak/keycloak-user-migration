package com.danielfrak.code.keycloak.providers.rest.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpStatus;
import org.keycloak.component.ComponentModel;

import java.io.IOException;
import java.util.Optional;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;

public class RestUserService implements LegacyUserService {

    private final String uri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RestUserService(ComponentModel model, HttpClient httpClient) {
        this.httpClient = httpClient;
        this.uri = model.getConfig().getFirst(URI_PROPERTY);

        var tokenAuthEnabled = Boolean.parseBoolean(model.getConfig().getFirst(API_TOKEN_ENABLED_PROPERTY));
        if (tokenAuthEnabled) {
            String token = model.getConfig().getFirst(API_TOKEN_PROPERTY);
            httpClient.enableBearerTokenAuth(token);
        }
        var basicAuthConfig = model.getConfig().getFirst(API_HTTP_BASIC_ENABLED_PROPERTY);
        var basicAuthEnabled = Boolean.parseBoolean(basicAuthConfig);
        if (basicAuthEnabled) {
            String basicAuthUser = model.getConfig().getFirst(API_HTTP_BASIC_USERNAME_PROPERTY);
            String basicAuthPassword = model.getConfig().getFirst(API_HTTP_BASIC_PASSWORD_PROPERTY);
            httpClient.enableBasicAuth(basicAuthUser, basicAuthPassword);
        }
    }

    @Override
    public Optional<LegacyUser> findByEmail(String email) {
        return findByUsername(email);
    }

    @Override
    public Optional<LegacyUser> findByUsername(String username) {
        var getUsernameUri = String.format("%s/%s", this.uri, username);
        try {
            var response = this.httpClient.get(getUsernameUri);
            if(response.code != HttpStatus.SC_OK) {
                return Optional.empty();
            }
            var legacyUser = objectMapper.readValue(response.body, LegacyUser.class);
            return Optional.ofNullable(legacyUser);
        } catch (IOException e) {
            throw new RestUserProviderException(e);
        }
    }

    @Override
    public boolean isPasswordValid(String username, String password) {
        var passwordValidationUri = String.format("%s/%s", this.uri, username);
        var dto = new UserPasswordDto(password);
        try {
            var json = objectMapper.writeValueAsString(dto);
            var response = httpClient.post(passwordValidationUri, json);
            return response.code == HttpStatus.SC_OK;
        } catch (IOException e) {
            throw new RestUserProviderException(e);
        }
    }
}
