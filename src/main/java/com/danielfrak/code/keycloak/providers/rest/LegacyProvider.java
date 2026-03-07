package com.danielfrak.code.keycloak.providers.rest;

import org.jboss.logging.Logger;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputUpdater;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserRegistrationProvider;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Provides legacy user migration functionality
 */
public class LegacyProvider implements UserStorageProvider,
        UserLookupProvider,
        CredentialInputUpdater,
        CredentialInputValidator,
        UserRegistrationProvider {

    private static final Logger LOG = Logger.getLogger(LegacyProvider.class);

    private final UserMigrationService userMigrationService;
    private final CredentialValidationService credentialValidationService;
    private final MigrationConfiguration config;

    public LegacyProvider(UserMigrationService userMigrationService,
                          CredentialValidationService credentialValidationService,
                          MigrationConfiguration config) {
        this.userMigrationService = userMigrationService;
        this.credentialValidationService = credentialValidationService;
        this.config = config;
    }

    @Override
    public boolean isValid(RealmModel realmModel, UserModel userModel, CredentialInput input) {
        LOG.debugf("isValid called for user %s with credential type %s", getUserName(userModel), getType(input));
        if (!supportsCredentialType(input.getType())) {
            return false;
        }

        userMigrationService.refreshUserFromLegacy(realmModel, userModel);
        return credentialValidationService.validatePassword(realmModel, userModel, input);
    }

    @Override
    public boolean supportsCredentialType(String s) {
        return credentialValidationService.supportsCredentialType(s);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realmModel, UserModel userModel, String s) {
        return false;
    }

    @Override
    public void close() {
        // Not needed
    }

    @Override
    public boolean updateCredential(RealmModel realm, UserModel user, CredentialInput input) {
        LOG.debugf("updateCredential called for user %s and type %s", getUserName(user), getType(input));
        if (user != null && config.shouldSeverFederationLink()) {
            severFederationLink(user);
        } else {
            LOG.debug("Skipping federation link removal.");
        }
        return false;
    }

    private void severFederationLink(UserModel user) {
        LOG.info("Severing federation link for " + user.getUsername());
        String link = user.getFederationLink();
        if (link != null && !link.isBlank()) {
            user.setFederationLink(null);
        }
    }

    private String getUserName(UserModel userModel) {
        return Optional.ofNullable(userModel)
                .map(UserModel::getUsername)
                .orElse(null);
    }

    private String getType(CredentialInput input) {
        return Optional.ofNullable(input)
                .map(CredentialInput::getType)
                .orElse(null);
    }

    @Override
    public void disableCredentialType(RealmModel realm, UserModel user, String credentialType) {
        // Not needed
    }

    @Override
    public Stream<String> getDisableableCredentialTypesStream(RealmModel realmModel, UserModel userModel) {
        return Stream.empty();
    }

    @Override
    public UserModel getUserById(RealmModel realmModel, String s) {
        throw new UnsupportedOperationException("User lookup by id not implemented");
    }

    @Override
    public UserModel getUserByUsername(RealmModel realmModel, String username) {
        LOG.debugf("getUserByUsername called for username %s", username);
        return userMigrationService.getAndUpdateUserByUsername(realmModel, username);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realmModel, String email) {
        LOG.debugf("getUserByEmail called for email %s", email);
        return userMigrationService.getAndUpdateUserByEmail(realmModel, email);
    }

    @Override
    public UserModel addUser(RealmModel realmModel, String userName) {
        LOG.debugf("addUser called for username %s", userName);
        return null;
    }

    @Override
    public boolean removeUser(RealmModel realmModel, UserModel userModel) {
        LOG.debugf("removeUser called for user %s", getUserName(userModel));
        return true;
    }
}
