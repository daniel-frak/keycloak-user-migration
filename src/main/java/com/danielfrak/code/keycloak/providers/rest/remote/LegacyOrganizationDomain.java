package com.danielfrak.code.keycloak.providers.rest.remote;

public record LegacyOrganizationDomain (
        String domainName,
        boolean isVerified
) {
}
