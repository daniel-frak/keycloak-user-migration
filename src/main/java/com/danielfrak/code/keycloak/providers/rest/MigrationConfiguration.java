package com.danielfrak.code.keycloak.providers.rest;

import org.keycloak.component.ComponentModel;

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

    public UserSyncMode getGroupSyncMode() {
        var configValue = model.getConfig().getFirst(UPDATE_USER_GROUPS_ON_LOGIN);
        return UserSyncMode.fromConfig(configValue, UserSyncMode.SYNC_FIRST_LOGIN);
    }

    public UserSyncMode getRoleSyncMode() {
        var configValue = model.getConfig().getFirst(UPDATE_USER_ROLES_ON_LOGIN);
        return UserSyncMode.fromConfig(configValue, UserSyncMode.SYNC_FIRST_LOGIN);
    }

    public boolean shouldSeverFederationLink() {
        var configValue = model.getConfig().getFirst(SEVER_FEDERATION_LINK);
        return configValue == null || Boolean.parseBoolean(configValue);
    }
}
