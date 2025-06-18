package com.danielfrak.code.keycloak.providers.rest.remote;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * A user in the old authentication system
 */
public record LegacyUser(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        @JsonProperty("enabled") boolean isEnabled,
        @JsonProperty("emailVerified") boolean isEmailVerified,
        Map<String, List<String>> attributes,
        List<String> roles,
        List<String> groups,
        List<String> requiredActions,
        List<LegacyTotp> totps,
        List<LegacyOrganization> organizations
) {
}
