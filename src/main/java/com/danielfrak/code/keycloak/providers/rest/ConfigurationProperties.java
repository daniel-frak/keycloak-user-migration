package com.danielfrak.code.keycloak.providers.rest;

import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

import static com.danielfrak.code.keycloak.providers.rest.UserSyncMode.*;
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
    public static final String UPDATE_USER_ON_LOGIN = "UPDATE_USER_ON_LOGIN";
    public static final String UPDATE_USER_GROUPS_ON_LOGIN = "UPDATE_USER_GROUPS_ON_LOGIN";
    public static final String UPDATE_USER_ROLES_ON_LOGIN = "UPDATE_USER_ROLES_ON_LOGIN";
    public static final String IGNORED_SYNC_GROUPS_PROPERTY = "IGNORED_SYNC_GROUPS";
    public static final String IGNORED_SYNC_ROLES_PROPERTY = "IGNORED_SYNC_ROLES";
    public static final String SEVER_FEDERATION_LINK = "SEVER_FEDERATION_LINK";
    public static final List<String> DEFAULT_IGNORED_SYNC_ROLES = List.of(
            "default-roles-*",
            "realm-management",
            "offline_access",
            "uma_authorization"
    );

    private static final List<ProviderConfigProperty> PROPERTIES = List.of(
            new ProviderConfigProperty(URI_PROPERTY,
                    "Rest client URI (required)",
                    "URI of the legacy system endpoints",
                    STRING_TYPE, null),
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
                    "Use the id of the user instead of the username as the path" +
                    "parameter when making a credential verification request",
                    BOOLEAN_TYPE, false),
            new ProviderConfigProperty(UPDATE_USER_ON_LOGIN,
                    "Refresh user attributes on login",
                    "Re-fetch legacy attributes on each login. Requires the user to remain federated—if the federation link is severed (setting below enabled), this option has no effect. Users whose links were already severed must be re-linked or re-imported for this to work.",
                    BOOLEAN_TYPE, false),
            syncModeProperty(UPDATE_USER_GROUPS_ON_LOGIN,
                    "User groups sync mode",
                    "Controls how groups are imported/synced from legacy identity provider on login. " +
                    "SYNC_FIRST_LOGIN: import only on first login. " +
                    "SYNC_EVERY_LOGIN: add missing and remove stale groups on each login. " +
                    "SYNC_EVERY_LOGIN_ONLY_ADD: add missing groups only on each login. " +
                    "NO_SYNC: do not import or sync groups."),
            syncModeProperty(UPDATE_USER_ROLES_ON_LOGIN,
                    "User roles sync mode",
                    "Controls how roles are imported/synced from legacy identity provider on login. " +
                    "SYNC_FIRST_LOGIN: import only on first login. " +
                    "SYNC_EVERY_LOGIN: add missing and remove stale roles on each login. " +
                    "SYNC_EVERY_LOGIN_ONLY_ADD: add missing roles only on each login. " +
                    "NO_SYNC: do not import or sync roles."),
            new ProviderConfigProperty(IGNORED_SYNC_GROUPS_PROPERTY,
                    "Ignored groups during sync",
                    "Groups ignored by sync operations. Supports wildcard '*' (example: app-*). " +
                    "Use this to skip importing specific legacy groups and to protect existing Keycloak groups " +
                    "that are not managed by legacy from being removed during synchronization.",
                    MULTIVALUED_STRING_TYPE,
                    null),
            new ProviderConfigProperty(IGNORED_SYNC_ROLES_PROPERTY,
                    "Ignored roles during sync",
                    "Roles ignored by sync operations. Supports wildcard '*' (example: default-roles-*). " +
                    "Use this to skip importing specific legacy roles and to protect existing Keycloak roles " +
                    "that are not managed by legacy from being removed during synchronization.",
                    MULTIVALUED_STRING_TYPE,
                    DEFAULT_IGNORED_SYNC_ROLES),
            new ProviderConfigProperty(SEVER_FEDERATION_LINK,
                    "Sever federation link after migration",
                    "When enabled, the provider removes the federation link so future logins use local credentials only.",
                    BOOLEAN_TYPE, true),
            new ProviderConfigProperty(ROLE_MAP_PROPERTY,
                    "Legacy role conversion",
                    "Role conversion in the format 'legacyRole:newRole'",
                    MULTIVALUED_STRING_TYPE, null),
            new ProviderConfigProperty(MIGRATE_UNMAPPED_ROLES_PROPERTY,
                    "Migrate unmapped roles",
                    "Whether or not to migrate roles not found in the field above",
                    BOOLEAN_TYPE, true),
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

    private static ProviderConfigProperty syncModeProperty(String name, String label, String helpText) {
        ProviderConfigProperty property = new ProviderConfigProperty(
                name,
                label,
                helpText,
                LIST_TYPE,
                SYNC_FIRST_LOGIN.name()
        );
        property.setOptions(List.of(
                SYNC_FIRST_LOGIN.name(),
                SYNC_EVERY_LOGIN.name(),
                SYNC_EVERY_LOGIN_ONLY_ADD.name(),
                NO_SYNC.name()
        ));
        return property;
    }

    public static List<ProviderConfigProperty> getConfigProperties() {
        return PROPERTIES;
    }
}
