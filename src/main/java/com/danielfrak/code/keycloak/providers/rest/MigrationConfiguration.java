package com.danielfrak.code.keycloak.providers.rest;

import org.keycloak.component.ComponentModel;

import java.util.OptionalInt;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;

public class MigrationConfiguration {

    private final ComponentModel model;

    public MigrationConfiguration(ComponentModel model) {
        this.model = model;
    }

    public String getModelId() {
        return model.getId();
    }

    public boolean shouldUseUserIdForCredentialVerification() {
        return Boolean.parseBoolean(model.getConfig().getFirst(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION));
    }

    public boolean shouldUpdateUserOnLogin() {
        return Boolean.parseBoolean(model.getConfig().getFirst(UPDATE_USER_ON_LOGIN));
    }

    public boolean shouldUpdateUserTotpsOnLogin() {
        return Boolean.parseBoolean(model.getConfig().getFirst(UPDATE_USER_TOTPS_ON_LOGIN));
    }

    public UserSyncMode getGroupSyncMode() {
        var configValue = model.getConfig().getFirst(UPDATE_USER_GROUPS_ON_LOGIN);
        return UserSyncMode.fromConfig(configValue, UserSyncMode.SYNC_FIRST_LOGIN);
    }

    public UserSyncMode getRoleSyncMode() {
        var configValue = model.getConfig().getFirst(UPDATE_USER_ROLES_ON_LOGIN);
        return UserSyncMode.fromConfig(configValue, UserSyncMode.SYNC_FIRST_LOGIN);
    }

    /**
     * @return the connect timeout in milliseconds, or empty if not configured
     * @throws IllegalArgumentException if the configured value is not a positive integer
     */
    public OptionalInt getHttpConnectTimeoutMillis() {
        return getTimeoutMillis(HTTP_CONNECT_TIMEOUT_PROPERTY, HTTP_CONNECT_TIMEOUT_LABEL);
    }

    /**
     * @return the read timeout in milliseconds, or empty if not configured
     * @throws IllegalArgumentException if the configured value is not a positive integer
     */
    public OptionalInt getHttpReadTimeoutMillis() {
        return getTimeoutMillis(HTTP_READ_TIMEOUT_PROPERTY, HTTP_READ_TIMEOUT_LABEL);
    }

    private OptionalInt getTimeoutMillis(String property, String label) {
        var configValue = model.getConfig().getFirst(property);
        if (configValue == null || configValue.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            int timeout = Integer.parseInt(configValue.strip());
            if (timeout > 0) {
                return OptionalInt.of(timeout);
            }
        } catch (NumberFormatException e) {
            // Handled below
        }
        throw new IllegalArgumentException("'" + label + "' must be a positive number of milliseconds");
    }

    public boolean shouldSeverFederationLink() {
        var configValue = model.getConfig().getFirst(SEVER_FEDERATION_LINK);
        return configValue == null || Boolean.parseBoolean(configValue);
    }
}
