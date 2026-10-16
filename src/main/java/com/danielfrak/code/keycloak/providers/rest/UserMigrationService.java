package com.danielfrak.code.keycloak.providers.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUserService;
import com.danielfrak.code.keycloak.providers.rest.remote.usermodel.UserModelFactory;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationFlowException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.Optional;
import java.util.function.Supplier;

public class UserMigrationService {

    private static final Logger LOG = Logger.getLogger(UserMigrationService.class);

    private final KeycloakSession session;
    private final LegacyUserService legacyUserService;
    private final LocalUserLookup localUserLookup;
    private final UserModelFactory userModelFactory;
    private final MigrationConfiguration config;

    public UserMigrationService(KeycloakSession session, LegacyUserService legacyUserService,
                                LocalUserLookup localUserLookup,
                                UserModelFactory userModelFactory,
                                MigrationConfiguration config) {
        this.session = session;
        this.legacyUserService = legacyUserService;
        this.localUserLookup = localUserLookup;
        this.userModelFactory = userModelFactory;
        this.config = config;
    }

    public UserModel getAndUpdateUserByUsername(RealmModel realm, String username) {
        return getAndUpdateUserModelIfNotInProgress(realm, username, () -> legacyUserService.findByUsername(username));
    }

    public UserModel getAndUpdateUserByEmail(RealmModel realm, String email) {
        return getAndUpdateUserModelIfNotInProgress(realm, email, () -> legacyUserService.findByEmail(email));
    }

    private UserModel getAndUpdateUserModelIfNotInProgress(
            RealmModel realm, String identifier, Supplier<Optional<LegacyUser>> legacyUserLookup) {
        if (localUserLookup.isLookupInProgress()) {
            LOG.debugf("Skipping legacy lookup for %s to avoid recursion", identifier);
            return null;
        }

        return getAndUpdateUserModel(realm, identifier, legacyUserLookup);
    }

    private UserModel getAndUpdateUserModel(
            RealmModel realm, String identifier, Supplier<Optional<LegacyUser>> legacyUserLookup) {
        Optional<LegacyUser> maybeLegacyUser = legacyUserLookup.get();
        if (maybeLegacyUser.isEmpty()) {
            LOG.warnf("User not found in external repository: %s", identifier);
            return null;
        }
        LegacyUser legacyUser = maybeLegacyUser.get();

        Optional<UserModel> maybeExistingUser = localUserLookup.findExistingUser(realm, legacyUser);
        if (maybeExistingUser.isEmpty()) {
            return createUserFromLegacyData(realm, legacyUser);
        }

        var existingUser = maybeExistingUser.get();
        updateExistingUser(existingUser, legacyUser);
        return existingUser;
    }

    private UserModel createUserFromLegacyData(RealmModel realm, LegacyUser legacyUser) {
        if (userModelFactory.isDuplicateUserId(legacyUser, realm)) {
            LOG.warnf("User with the same user id already exists: %s", legacyUser.id());
            return null;
        }

        LOG.debug("Creating user model for legacy user " + legacyUser.username());
        return userModelFactory.create(legacyUser, realm);
    }

    private void updateExistingUser(UserModel existingUser, LegacyUser legacyUser) {
        LOG.debugf("Found existing user %s in Keycloak. Updating from legacy payload.",
                existingUser.getUsername());
        userModelFactory.updateUserAttributes(legacyUser, existingUser);
    }

    public void refreshUserTotpsFromLegacy(RealmModel realm, UserModel userModel) {
        try {
            if (config.shouldSeverFederationLink()) {
                throw new IllegalStateException("TOTP refresh requires a retained federation link.");
            }
            var legacyUser = legacyUserService.findByUsername(userModel.getUsername())
                    .orElseThrow(() -> new IllegalStateException("Legacy user unavailable during TOTP refresh."));
            if (!legacyUser.isEnabled()) {
                throw new IllegalStateException("Legacy user disabled during TOTP refresh.");
            }
            if (legacyUser.id() != null && !legacyUser.id().isBlank()
                    && !legacyUser.id().equals(userModel.getId())) {
                throw new IllegalStateException("Legacy user id differs during TOTP refresh.");
            }
            userModelFactory.synchronizeTotps(legacyUser, userModel);
            refreshUserFromLegacy(realm, userModel, legacyUser, userModel.getUsername());
        } catch (RuntimeException ex) {
            session.getTransactionManager().setRollbackOnly();
            // Do not log the exception payload: it may contain data from the legacy credentials response.
            LOG.errorf("Failed to synchronize legacy TOTP credentials for user %s (%s). Aborting login.",
                    userModel.getId(), ex.getClass().getSimpleName());
            throw new AuthenticationFlowException("Unable to synchronize legacy TOTP credentials.",
                    AuthenticationFlowError.INTERNAL_ERROR);
        }
    }

    public void refreshUserFromLegacy(RealmModel realm, UserModel userModel) {
        if (!config.shouldUpdateUserOnLogin() &&
                !config.getGroupSyncMode().shouldSyncOnLogin() &&
                !config.getRoleSyncMode().shouldSyncOnLogin()) {
            LOG.debug("Skipping legacy refresh because all refresh-on-login options are disabled.");
            return;
        }

        String username = userModel.getUsername();
        LOG.debugf("Refreshing user data from legacy store for %s", username);
        legacyUserService.findByUsername(username)
                .ifPresentOrElse(
                        legacyUser -> refreshUserFromLegacy(realm, userModel, legacyUser, username),
                        () -> LOG.debugf("Legacy user %s not found during refresh.", username)
                );
    }

    private void refreshUserFromLegacy(RealmModel realm, UserModel userModel, LegacyUser legacyUser, String username) {
        LOG.debugf("Legacy user %s found. Applying latest data.", username);
        try {
            if (config.shouldUpdateUserOnLogin()) {
                userModelFactory.updateUserAttributes(legacyUser, userModel);
            }
            updateUserGroups(userModel, legacyUser, realm);
            updateUserRoles(userModel, legacyUser, realm);
        } catch (RuntimeException ex) {
            LOG.errorf(ex, "Failed to refresh legacy data for user %s. Continuing login.", username);
        }
    }

    private void updateUserGroups(UserModel userModel, LegacyUser legacyUser, RealmModel realm) {
        UserSyncMode syncMode = config.getGroupSyncMode();
        if (!syncMode.shouldSyncOnLogin()) {
            return;
        }

        LOG.debugf("Synchronizing groups for user %s", legacyUser.username());
        if (syncMode.shouldRemoveMissingOnLogin()) {
            userModelFactory.synchronizeGroups(legacyUser, realm, userModel);
        } else {
            userModelFactory.synchronizeGroupsAddOnly(legacyUser, realm, userModel);
        }
    }

    private void updateUserRoles(UserModel userModel, LegacyUser legacyUser, RealmModel realm) {
        UserSyncMode syncMode = config.getRoleSyncMode();
        if (!syncMode.shouldSyncOnLogin()) {
            return;
        }

        LOG.debugf("Synchronizing roles for user %s", legacyUser.username());
        if (syncMode.shouldRemoveMissingOnLogin()) {
            userModelFactory.synchronizeRoles(legacyUser, realm, userModel);
        } else {
            userModelFactory.synchronizeRolesAddOnly(legacyUser, realm, userModel);
        }
    }
}
