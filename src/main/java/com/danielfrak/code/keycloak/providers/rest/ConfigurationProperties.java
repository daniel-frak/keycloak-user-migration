package com.danielfrak.code.keycloak.providers.rest;

import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

import static org.keycloak.provider.ProviderConfigProperty.*;

public final class ConfigurationProperties {

    public static final String PROVIDER_NAME = "User migration using a REST client";
    public static final String URI_PROPERTY = "URI";
    public static final String API_TOKEN_PROPERTY = "API_TOKEN";
    public static final String API_TOKEN_ENABLED_PROPERTY = "API_TOKEN_ENABLED";
    public static final String API_HTTP_BASIC_ENABLED_PROPERTY = "API_HTTP_BASIC_ENABLED";
    public static final String API_HTTP_BASIC_USERNAME_PROPERTY = "API_HTTP_BASIC_USERNAME";
    public static final String API_HTTP_BASIC_PASSWORD_PROPERTY = "API_HTTP_BASIC_PASSWORD";
    public static final String USE_USER_ID_FOR_CREDENTIAL_VERIFICATION = "USE_USER_ID_FOR_CREDENTIAL_VERIFICATION";
    public static final String ROLE_MAP_PROPERTY = "ROLE_MAP";
    public static final String GROUP_MAP_PROPERTY = "GROUP_MAP";
    public static final String MIGRATE_UNMAPPED_ROLES_PROPERTY = "MIGRATE_UNMAPPED_ROLES";
    public static final String MIGRATE_UNMAPPED_GROUPS_PROPERTY = "MIGRATE_UNMAPPED_GROUPS";
    public static final String VALID_FOR_CLIENT_PROPERTY = "VALID_FOR_CLIENT";
    public static final String RESTRICT_ROLES_TO_CLIENT = "RESTRICT_ROLES_TO_CLIENT";

    private static final List<ProviderConfigProperty> PROPERTIES = List.of(
            new ProviderConfigProperty(URI_PROPERTY,
                    "Rest client URI (required)",
                    "URI of the legacy system endpoints",
                    STRING_TYPE, null),
            new ProviderConfigProperty(VALID_FOR_CLIENT_PROPERTY,
                    "Valid for Client ID",
                    """
                            The migration can be restricted to only migrate users logging in \
                            using a specific client. Only roles defined on the client will be used. \
                            New roles will be created on the client instead of in Realm Roles.
                            Enter a Client ID, or leave blank if valid for all clients.
                            """,
                    STRING_TYPE,
                    null),
            new ProviderConfigProperty(API_TOKEN_ENABLED_PROPERTY,
                    "Rest client Bearer token auth enabled",
                    "Enables Bearer token authentication for legacy user service",
                    BOOLEAN_TYPE, false),
            new ProviderConfigProperty(API_TOKEN_PROPERTY,
                    "Rest client Bearer token",
                    "Bearer token",
                    PASSWORD, null),
            new ProviderConfigProperty(API_HTTP_BASIC_ENABLED_PROPERTY,
                    "Rest client basic auth enabled",
                    "Enables HTTP basic auth for legacy user service",
                    BOOLEAN_TYPE, false),
            new ProviderConfigProperty(API_HTTP_BASIC_USERNAME_PROPERTY,
                    "Rest client basic auth username",
                    "HTTP basic auth username for legacy user service",
                    STRING_TYPE, null),
            new ProviderConfigProperty(API_HTTP_BASIC_PASSWORD_PROPERTY,
                    "Rest client basic auth password",
                    "HTTP basic auth password for legacy user service",
                    PASSWORD, null),
            new ProviderConfigProperty(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION,
                    "Use user id for credential verification",
                    "Use the id of the user instead of the username as the path " +
                    "parameter when making a credential verification request",
                    BOOLEAN_TYPE, false),
            new ProviderConfigProperty(ROLE_MAP_PROPERTY,
                    "Legacy role conversion",
                    "Role conversion in the format 'legacyRole:newRole'",
                    MULTIVALUED_STRING_TYPE, null),
            new ProviderConfigProperty(MIGRATE_UNMAPPED_ROLES_PROPERTY,
                    "Migrate unmapped roles",
                    "Whether or not to migrate roles not found in the field above",
                    BOOLEAN_TYPE, true),
            new ProviderConfigProperty(RESTRICT_ROLES_TO_CLIENT,
                    "Restrict role actions to client",
                    """
                        If 'Valid Client ID' is set, this will restrict the use roles to those \
                        defined on that client.
                        New roles will be created on the client instead of in the realm.
                    """,
                    BOOLEAN_TYPE, false),
            new ProviderConfigProperty(GROUP_MAP_PROPERTY,
                    "Legacy group conversion",
                    "Group conversion in the format 'legacyGroup:newGroup'",
                    MULTIVALUED_STRING_TYPE, null),
            new ProviderConfigProperty(MIGRATE_UNMAPPED_GROUPS_PROPERTY,
                    "Migrate unmapped groups",
                    "Whether or not to migrate groups not found in the field above",
                    BOOLEAN_TYPE, true)
    );

    private ConfigurationProperties() {
    }

    public static List<ProviderConfigProperty> getConfigProperties() {
        return PROPERTIES;
    }
}
