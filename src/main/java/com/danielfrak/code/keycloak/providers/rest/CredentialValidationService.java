package com.danielfrak.code.keycloak.providers.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUserService;
import org.jboss.logging.Logger;
import org.keycloak.credential.CredentialInput;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.policy.PasswordPolicyManagerProvider;
import org.keycloak.policy.PolicyError;

import java.util.Set;

public class CredentialValidationService {

    private static final Logger LOG = Logger.getLogger(CredentialValidationService.class);
    private static final Set<String> SUPPORTED_CREDENTIAL_TYPES = Set.of(PasswordCredentialModel.TYPE);

    private final KeycloakSession session;
    private final LegacyUserService legacyUserService;
    private final MigrationConfiguration config;

    public CredentialValidationService(KeycloakSession session, LegacyUserService legacyUserService,
                                       MigrationConfiguration config) {
        this.session = session;
        this.legacyUserService = legacyUserService;
        this.config = config;
    }

    public boolean supportsCredentialType(String type) {
        return SUPPORTED_CREDENTIAL_TYPES.contains(type);
    }

    public boolean validatePassword(RealmModel realm, UserModel user, CredentialInput input) {
        LOG.debugf("validatePassword called for user %s with credential type %s", user.getUsername(), input.getType());

        String userIdentifier = getUserIdentifier(user);

        if (!legacyUserService.isPasswordValid(userIdentifier, input.getChallengeResponse())) {
            return false;
        }

        if (passwordDoesNotBreakPolicy(realm, user, input.getChallengeResponse())) {
            user.credentialManager().updateCredential(input);
        } else {
            addUpdatePasswordAction(user, userIdentifier);
        }

        return true;
    }

    private String getUserIdentifier(UserModel userModel) {
        return config.shouldUseUserIdForCredentialVerification() ? userModel.getId() : userModel.getUsername();
    }

    private boolean passwordDoesNotBreakPolicy(RealmModel realmModel, UserModel userModel, String password) {
        PasswordPolicyManagerProvider passwordPolicyManagerProvider = session.getProvider(
                PasswordPolicyManagerProvider.class);
        PolicyError error = passwordPolicyManagerProvider
                .validate(realmModel, userModel, password);

        return error == null;
    }

    private void addUpdatePasswordAction(UserModel userModel, String userIdentifier) {
        if (updatePasswordActionMissing(userModel)) {
            LOG.infof("Could not use legacy password for user %s due to password policy." +
                            " Adding UPDATE_PASSWORD action.",
                    userIdentifier);
            userModel.addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
        }
    }

    private boolean updatePasswordActionMissing(UserModel userModel) {
        return userModel.getRequiredActionsStream()
                .noneMatch(s -> s.contains(UserModel.RequiredAction.UPDATE_PASSWORD.name()));
    }
}
