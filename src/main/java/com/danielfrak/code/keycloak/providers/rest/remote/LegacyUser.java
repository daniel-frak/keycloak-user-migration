package com.danielfrak.code.keycloak.providers.rest.remote;

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
        boolean isEnabled,
        boolean isEmailVerified,
        Map<String, List<String>> attributes,
        List<String> roles,
        List<String> groups,
        List<String> requiredActions,
        List<LegacyTotp> totps
) {
}
