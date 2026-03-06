package com.danielfrak.code.keycloak.providers.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;

import java.util.Optional;

public class LocalUserLookup {

    private static final Logger LOG = Logger.getLogger(LocalUserLookup.class);
    private static final String EXISTING_USER_LOOKUP_ATTR = LocalUserLookup.class.getName() + ".existingUserLookup";

    private final KeycloakSession session;

    public LocalUserLookup(KeycloakSession session) {
        this.session = session;
    }

    public boolean isLookupInProgress() {
        Boolean inProgress = session.getAttribute(EXISTING_USER_LOOKUP_ATTR, Boolean.class);
        return Boolean.TRUE.equals(inProgress);
    }

    public Optional<UserModel> findExistingUser(RealmModel realm, LegacyUser legacyUser) {
        session.setAttribute(EXISTING_USER_LOOKUP_ATTR, Boolean.TRUE);
        try {
            UserProvider userProvider = session.users();

            return getUserByUsername(realm, legacyUser, userProvider)
                    .or(() -> findByIdAndUsername(realm, legacyUser, userProvider))
                    .or(() -> {
                        LOG.debugf("No existing user %s located in local storage.", legacyUser.username());
                        return Optional.empty();
                    });
        } finally {
            session.removeAttribute(EXISTING_USER_LOOKUP_ATTR);
        }
    }

    private Optional<UserModel> getUserByUsername(RealmModel realm, LegacyUser legacyUser, UserProvider userProvider) {
        UserModel userModel = userProvider.getUserByUsername(realm, legacyUser.username());
        if (userModel != null) {
            LOG.debugf("Located existing user %s by username in local storage.", legacyUser.username());
        }

        return Optional.ofNullable(userModel);
    }

    private Optional<UserModel> findByIdAndUsername(RealmModel realm, LegacyUser legacyUser,
                                                    UserProvider userProvider) {
        String legacyId = legacyUser.id();
        if (legacyId == null || legacyId.isBlank()) {
            LOG.debugf("Legacy user %s does not expose an id. Skipping lookup by id.", legacyUser.username());
            return Optional.empty();
        }

        return Optional.ofNullable(userProvider.getUserById(realm, legacyId))
                .filter(user -> legacyUser.username().equals(user.getUsername()))
                .map(user -> {
                    LOG.debugf("Located existing user %s by legacy id %s.", legacyUser.username(), legacyId);
                    return user;
                });
    }
}
