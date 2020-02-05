package com.danielfrak.code.keycloak.providers.rest;

import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

import static org.keycloak.provider.ProviderConfigProperty.*;

public class ConfigurationProperties {

    public static final String PROVIDER_NAME = "User Migration using a REST client";
    public static final String URI_PROPERTY = "URI";
    public static final String ROLE_MAP_PROPERTY = "ROLE_MAP";
    public static final String MIGRATE_UNMAPPED_ROLES_PROPERTY = "MIGRATE_UNMAPPED_ROLES";

    private static List<ProviderConfigProperty> properties = List.of(
            new ProviderConfigProperty(URI_PROPERTY,
                    "Rest client URI", "URI of the legacy system endpoints",
                    STRING_TYPE, null),
            new ProviderConfigProperty(ROLE_MAP_PROPERTY,
                    "Legacy role conversion", "Role conversion in the format 'legacyRole:newRole'",
                    MULTIVALUED_STRING_TYPE, null),
            new ProviderConfigProperty(MIGRATE_UNMAPPED_ROLES_PROPERTY,
                    "Migrate unmapped roles",
                    "Whether or not to migrate roles not found in the field above",
                    BOOLEAN_TYPE, true)
    );

    public static List<ProviderConfigProperty> getConfigProperties() {
        return properties;
    }
}
