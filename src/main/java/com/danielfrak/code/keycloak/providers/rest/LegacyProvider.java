package com.danielfrak.code.keycloak.providers.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUserService;
import com.danielfrak.code.keycloak.providers.rest.remote.UserModelFactory;
import org.jboss.logging.Logger;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Provides legacy user migration functionality
 */
public class LegacyProvider implements UserStorageProvider, UserLookupProvider, CredentialInputValidator {

    private static final Logger log = Logger.getLogger(LegacyProvider.class);
    private static final Set<String> supportedCredentialTypes = Collections.singleton(PasswordCredentialModel.TYPE);
    private final KeycloakSession session;
    private final LegacyUserService legacyUserService;
    private final UserModelFactory userModelFactory;

    public LegacyProvider(KeycloakSession session, LegacyUserService legacyUserService,
                          UserModelFactory userModelFactory) {
        this.session = session;
        this.legacyUserService = legacyUserService;
        this.userModelFactory = userModelFactory;
    }

    @Override
    public UserModel getUserByUsername(String username, RealmModel realm) {
        return getUserModel(realm, username, () -> legacyUserService.findByUsername(username));
    }

    private UserModel getUserModel(RealmModel realm, String username, Supplier<Optional<LegacyUser>> user) {
        return user.get()
                .map(u -> userModelFactory.create(u, realm))
                .orElseGet(() -> {
                    log.warnf("User not found in external repository: %s", username);
                    return null;
                });
    }

    @Override
    public UserModel getUserByEmail(String email, RealmModel realm) {
        return getUserModel(realm, email, () -> legacyUserService.findByEmail(email));
    }

    @Override
    public boolean isValid(RealmModel realmModel, UserModel userModel, CredentialInput input) {
        if (!supportsCredentialType(input.getType())) {
            return false;
        }

        if (legacyUserService.validatePassword(userModel.getUsername(), input.getChallengeResponse())) {
            session.userCredentialManager().updateCredential(realmModel, userModel, input);
            userModel.setFederationLink(null);
            return true;
        }

        return false;
    }

    @Override
    public boolean supportsCredentialType(String s) {
        return supportedCredentialTypes.contains(s);
    }

    @Override
    public UserModel getUserById(String id, RealmModel realm) {
        throw new RuntimeException("User lookup by id not implemented");
    }

    @Override
    public boolean isConfiguredFor(RealmModel realmModel, UserModel userModel, String s) {
        return false;
    }

    @Override
    public void close() {
    }
}
