package com.danielfrak.code.keycloak.providers.rest.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUserService;
import org.jboss.resteasy.client.jaxrs.BasicAuthentication;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.keycloak.component.ComponentModel;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.util.Optional;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;

public class RestUserService implements LegacyUserService {

    private final RestUserClient client;

    public RestUserService(ComponentModel model, Client restEasyClient) {
        String uri = model.getConfig().getFirst(URI_PROPERTY);
        var tokenAuthEnabled = Boolean.parseBoolean(model.getConfig().getFirst(API_TOKEN_ENABLED_PROPERTY));
        if (tokenAuthEnabled) {
            String token = model.getConfig().getFirst(API_TOKEN_PROPERTY);
            registerBearerTokenRequestFilter(restEasyClient, token);
        }
        var basicAuthEnabled = Boolean.parseBoolean(model.getConfig().getFirst(API_HTTP_BASIC_ENABLED_PROPERTY));
        if (basicAuthEnabled) {
            String basicAuthUser = model.getConfig().getFirst(API_HTTP_BASIC_USERNAME_PROPERTY);
            String basicAuthPassword = model.getConfig().getFirst(API_HTTP_BASIC_PASSWORD_PROPERTY);
            registerBasicAuthFilter(restEasyClient, basicAuthUser, basicAuthPassword);
        }
        this.client = buildClient(restEasyClient, uri);
    }

    private Client registerBasicAuthFilter(Client restEasyClient, String basicAuthUser, String basicAuthPassword) {
        if (basicAuthUser != null
                && !basicAuthUser.isEmpty()
                && basicAuthPassword != null
                && !basicAuthPassword.isEmpty()) {
            restEasyClient.register(new BasicAuthentication(basicAuthUser, basicAuthPassword));
        }
        return restEasyClient;
    }

    private Client registerBearerTokenRequestFilter(Client restEasyClient, String token) {
        if (token != null && !token.isEmpty()) {
            restEasyClient.register(new BearerTokenRequestFilter(token));
        }
        return restEasyClient;
    }

    private RestUserClient buildClient(Client restEasyClient, String uri) {

        ResteasyWebTarget target = (ResteasyWebTarget) restEasyClient.target(uri);
        return target.proxy(RestUserClient.class);
    }

    @Override
    public Optional<LegacyUser> findByEmail(String email) {
        return findByUsername(email);
    }

    @Override
    public Optional<LegacyUser> findByUsername(String username) {
        final Response response = client.findByUsername(username);
        if (response.getStatus() != 200) {
            return Optional.empty();
        }
        return Optional.ofNullable(response.readEntity(LegacyUser.class));
    }

    @Override
    public boolean removeByIdentifier(String identifier) {
        final Response response = client.removeByIdentifier(identifier);
        return response.getStatus() == 200;
    }

    @Override
    public boolean isPasswordValid(String username, String password) {
        final Response response = client.validatePassword(username, new UserPasswordDto(password));
        return response.getStatus() == 200;
    }
}
