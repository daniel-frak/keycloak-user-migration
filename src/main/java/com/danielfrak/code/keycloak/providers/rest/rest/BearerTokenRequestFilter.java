package com.danielfrak.code.keycloak.providers.rest.rest;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;

public class BearerTokenRequestFilter implements ClientRequestFilter {

    private final String token;

    public BearerTokenRequestFilter(String token) {
        this.token = token;
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        requestContext.getHeaders().add("Authorization", "Bearer " + this.token);
    }
}
