package com.danielfrak.code.keycloak.providers.rest.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUserService;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.keycloak.component.ComponentModel;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import java.util.Optional;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.API_TOKEN_PROPERTY;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.URI_PROPERTY;

public class RestUserService implements LegacyUserService {

    private final RestUserClient client;

    public RestUserService(ComponentModel model, Client restEasyClient) {
        String uri = model.getConfig().getFirst(URI_PROPERTY);
        String token = model.getConfig().getFirst(API_TOKEN_PROPERTY);
        this.client = buildClient(restEasyClient, uri, token);
    }

    private RestUserClient buildClient(Client restEasyClient, String uri, String token) {
        if (token != null && !token.isEmpty()) {
            restEasyClient.register(new BearerTokenRequestFilter(token));
        }

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
    public boolean isPasswordValid(String username, String password) {
        final Response response = client.validatePassword(username, new UserPasswordDto(password));
        return response.getStatus() == 200;
    }
}
