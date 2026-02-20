package com.danielfrak.code.keycloak.providers.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUserService;
import com.danielfrak.code.keycloak.providers.rest.remote.UserModelFactory;
import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputUpdater;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.policy.PasswordPolicyManagerProvider;
import org.keycloak.policy.PolicyError;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserRegistrationProvider;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;


/**
 * Provides legacy user migration functionality
 */
@SuppressWarnings(
        // No easy way to reduce the dependencies of this class:
        "java:S1200"
)
public class LegacyProvider implements UserStorageProvider,
        UserLookupProvider,
        CredentialInputUpdater,
        CredentialInputValidator,
        UserRegistrationProvider {

    private static final Logger LOG = Logger.getLogger(LegacyProvider.class);
    private static final Set<String> supportedCredentialTypes = Set.of(PasswordCredentialModel.TYPE);
    private static final String EXISTING_USER_LOOKUP_ATTR = LegacyProvider.class.getName() + ".existingUserLookup";
    private final KeycloakSession session;
    private final LegacyUserService legacyUserService;
    private final UserModelFactory userModelFactory;
    private final ComponentModel model;

    public LegacyProvider(KeycloakSession session, LegacyUserService legacyUserService,
                          UserModelFactory userModelFactory, ComponentModel model) {
        this.session = session;
        this.legacyUserService = legacyUserService;
        this.userModelFactory = userModelFactory;
        this.model = model;
    }

    @Override
    public boolean isValid(RealmModel realmModel, UserModel userModel, CredentialInput input) {
        LOG.debugf("isValid called for user %s with credential type %s",
                userModel == null ? "null" : userModel.getUsername(), input == null ? "null" : input.getType());
        if (!supportsCredentialType(input.getType())) {
            return false;
        }

        refreshUserFromLegacy(realmModel, userModel);

        var userIdentifier = getUserIdentifier(userModel);

        if (!legacyUserService.isPasswordValid(userIdentifier, input.getChallengeResponse())) {
            return false;
        }

        if (passwordDoesNotBreakPolicy(realmModel, userModel, input.getChallengeResponse())) {
            userModel.credentialManager().updateCredential(input);
        } else {
            addUpdatePasswordAction(userModel, userIdentifier);
        }

        return true;
    }

    private String getUserIdentifier(UserModel userModel) {
        var userIdConfig = model.getConfig().getFirst(ConfigurationProperties.USE_USER_ID_FOR_CREDENTIAL_VERIFICATION);
        var useUserId = Boolean.parseBoolean(userIdConfig);
        return useUserId ? userModel.getId() : userModel.getUsername();
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

    private void refreshUserFromLegacy(RealmModel realmModel, UserModel userModel) {
        if (!shouldRefreshAttributesOnLogin() &&
            !groupSyncMode().shouldSyncOnLogin() &&
            !roleSyncMode().shouldSyncOnLogin()) {
            LOG.debug("Skipping legacy refresh because all refresh-on-login options are disabled.");
            return;
        }

        String username = userModel.getUsername();
        LOG.debugf("Refreshing user data from legacy store for %s", username);
        legacyUserService.findByUsername(username)
                .ifPresentOrElse(
                        legacyUser -> {
                            LOG.debugf("Legacy user %s found. Applying latest data.", username);
                            try {
                                if (shouldRefreshAttributesOnLogin()) {
                                    updateUserData(userModel, legacyUser);
                                }
                                updateUserGroups(userModel, legacyUser, realmModel);
                                updateUserRoles(userModel, legacyUser, realmModel);
                            } catch (RuntimeException ex) {
                                LOG.errorf(ex, "Failed to refresh legacy data for user %s. Continuing login.", username);
                            }
                        },
                        () -> LOG.debugf("Legacy user %s not found during refresh.", username)
                );
    }

    @Override
    public boolean supportsCredentialType(String s) {
        return supportedCredentialTypes.contains(s);
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
        LOG.debugf("updateCredential called for user %s and type %s",
                user == null ? "null" : user.getUsername(), input == null ? "null" : input.getType());
        if (user != null && shouldSeverFederationLink()) {
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
        return getUserModel(realmModel, username, () -> legacyUserService.findByUsername(username));
    }

    private UserModel getUserModel(RealmModel realm, String username, Supplier<Optional<LegacyUser>> user) {
        LOG.debug("Getting legacy user for username " + username);
        if (existingUserLookupInProgress()) {
            LOG.debugf("Skipping legacy lookup for username %s to avoid recursion", username);
            return null;
        }

        return user.get()
                .map(legacyUser -> findExistingUser(realm, legacyUser)
                        .map(existingUser -> {
                            LOG.debugf("Found existing user %s in Keycloak. Updating from legacy payload.", existingUser.getUsername());
                            updateUserData(existingUser, legacyUser);
                            return existingUser;
                        })
                        .orElseGet(() -> createUserFromLegacyData(realm, legacyUser)))
                .orElseGet(() -> {
                    LOG.warnf("User not found in external repository: %s", username);
                    return null;
                });
    }

    private Optional<UserModel> findExistingUser(RealmModel realm, LegacyUser legacyUser) {
        session.setAttribute(EXISTING_USER_LOOKUP_ATTR, Boolean.TRUE);
        try {
            UserProvider userProvider = session.users();
            if (userProvider == null) {
                return Optional.empty();
            }

            UserModel existingUser = userProvider.getUserByUsername(realm, legacyUser.username());
            if (existingUser != null) {
                LOG.debugf("Located existing user %s by username in local storage.", legacyUser.username());
                return Optional.of(existingUser);
            }

            String legacyId = legacyUser.id();
            if (isBlank(legacyId)) {
                LOG.debugf("Legacy user %s does not expose an id. Skipping lookup by id.", legacyUser.username());
                return Optional.empty();
            }

            UserModel existingById = userProvider.getUserById(realm, legacyId);
            if (existingById != null && legacyUser.username().equals(existingById.getUsername())) {
                LOG.debugf("Located existing user %s by legacy id %s.", legacyUser.username(), legacyId);
                return Optional.of(existingById);
            }

            LOG.debugf("No existing user %s located in local storage.", legacyUser.username());
            return Optional.empty();
        } finally {
            session.removeAttribute(EXISTING_USER_LOOKUP_ATTR);
        }
    }

    private void updateUserData(UserModel userModel, LegacyUser legacyUser) {
        LOG.debugf("Updating user model for legacy user %s", legacyUser.username());
        userModel.setEnabled(legacyUser.isEnabled());
        userModel.setEmail(legacyUser.email());
        userModel.setEmailVerified(legacyUser.isEmailVerified());
        userModel.setFirstName(legacyUser.firstName());
        userModel.setLastName(legacyUser.lastName());

        if (legacyUser.attributes() != null) {
            legacyUser.attributes()
                    .forEach(userModel::setAttribute);
        }
    }

    private void updateUserGroups(UserModel userModel, LegacyUser legacyUser, RealmModel realmModel) {
        UserSyncMode syncMode = groupSyncMode();
        if (!syncMode.shouldSyncOnLogin()) {
            return;
        }

        LOG.debugf("Synchronizing groups for user %s", legacyUser.username());
        if (syncMode.shouldRemoveMissingOnLogin()) {
            userModelFactory.synchronizeGroups(legacyUser, realmModel, userModel);
        } else {
            userModelFactory.synchronizeGroupsAddOnly(legacyUser, realmModel, userModel);
        }
    }

    private void updateUserRoles(UserModel userModel, LegacyUser legacyUser, RealmModel realmModel) {
        UserSyncMode syncMode = roleSyncMode();
        if (!syncMode.shouldSyncOnLogin()) {
            return;
        }

        LOG.debugf("Synchronizing roles for user %s", legacyUser.username());
        if (syncMode.shouldRemoveMissingOnLogin()) {
            userModelFactory.synchronizeRoles(legacyUser, realmModel, userModel);
        } else {
            userModelFactory.synchronizeRolesAddOnly(legacyUser, realmModel, userModel);
        }
    }

    private UserModel createUserFromLegacyData(RealmModel realm, LegacyUser legacyUser) {
        boolean duplicate = userModelFactory.isDuplicateUserId(legacyUser, realm);
        if (duplicate) {
            LOG.warnf("User with the same user id already exists: %s", legacyUser.id());
            return null;
        }

        LOG.debug("Creating user model for legacy user " + legacyUser.username());
        return userModelFactory.create(legacyUser, realm);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean existingUserLookupInProgress() {
        Boolean inProgress = session.getAttribute(EXISTING_USER_LOOKUP_ATTR, Boolean.class);
        return Boolean.TRUE.equals(inProgress);
    }

    private boolean shouldRefreshAttributesOnLogin() {
        var configValue = model.getConfig().getFirst(ConfigurationProperties.UPDATE_USER_ON_LOGIN);
        return Boolean.parseBoolean(configValue);
    }

    private UserSyncMode groupSyncMode() {
        var configValue = model.getConfig().getFirst(ConfigurationProperties.UPDATE_USER_GROUPS_ON_LOGIN);
        return UserSyncMode.fromConfig(configValue, UserSyncMode.SYNC_FIRST_LOGIN);
    }

    private UserSyncMode roleSyncMode() {
        var configValue = model.getConfig().getFirst(ConfigurationProperties.UPDATE_USER_ROLES_ON_LOGIN);
        return UserSyncMode.fromConfig(configValue, UserSyncMode.SYNC_FIRST_LOGIN);
    }

    private boolean shouldSeverFederationLink() {
        var configValue = model.getConfig().getFirst(ConfigurationProperties.SEVER_FEDERATION_LINK);
        return configValue == null || Boolean.parseBoolean(configValue);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realmModel, String email) {
        LOG.debugf("getUserByEmail called for email %s", email);
        return getUserModel(realmModel, email, () -> legacyUserService.findByEmail(email));
    }

    @Override
    public UserModel addUser(RealmModel realmModel, String userName) {
        LOG.debugf("addUser called for username %s", userName);
        return null;
    }

    @Override
    public boolean removeUser(RealmModel realmModel, UserModel userModel) {
        LOG.debugf("removeUser called for user %s", userModel == null ? "null" : userModel.getUsername());
        return true;
    }
}
