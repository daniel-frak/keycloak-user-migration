package com.danielfrak.code.keycloak.providers.rest.remote;

public record LegacyTotp(
        String secret,
        String name,
        int digits,
        int period,
        String algorithm,
        String encoding
) {
}
