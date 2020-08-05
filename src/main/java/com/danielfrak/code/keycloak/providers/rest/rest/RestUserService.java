package com.danielfrak.code.keycloak.providers.rest.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUserService;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.keycloak.component.ComponentModel;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import java.util.Optional;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.API_TOKEN_PROPERTY;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.URI_PROPERTY;

public class RestUserService implements LegacyUserService {

    private final RestUserClient client;

    public RestUserService(ComponentModel model) {
        String uri = model.getConfig().getFirst(URI_PROPERTY);
        String token = model.getConfig().getFirst(API_TOKEN_PROPERTY);
        this.client = buildClient(uri, token);
    }

    private RestUserClient buildClient(String uri, String token) {
        var restEasyClient = ClientBuilder.newClient();
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
