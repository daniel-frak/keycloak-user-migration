package com.danielfrak.code.keycloak.providers.rest.remote;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.MIGRATE_UNMAPPED_ROLES_PROPERTY;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.ROLE_MAP_PROPERTY;

public class UserModelFactory {

    private static final Logger log = Logger.getLogger(UserModelFactory.class);

    private final KeycloakSession session;
    private final ComponentModel model;

    /**
     * String format:
     * legacyRole:newRole
     */
    private final Map<String, String> roleMap;

    public UserModelFactory(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
        this.roleMap = getRoleMap(model);
    }

    /**
     * Returns a map of legacy roles to new roles
     */
    private Map<String, String> getRoleMap(ComponentModel model) {
        Map<String, String> roleMap = new HashMap<>();
        List<String> pairs = model.getConfig().getList(ROLE_MAP_PROPERTY);
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            roleMap.put(keyValue[0], keyValue[1]);
        }
        return roleMap;
    }

    public UserModel create(LegacyUser legacyUser, RealmModel realm) {
        log.infof("Creating user model for: %s", legacyUser.getUsername());

        UserModel userModel;
        if (legacyUser.getId() == null) {
            userModel = session.userLocalStorage().addUser(realm, legacyUser.getUsername());
        } else {
            userModel = session.userLocalStorage().addUser(
                realm,
                legacyUser.getId(),
                legacyUser.getUsername(),
                true,
                false
            );
        }

        validateUsernamesEqual(legacyUser, userModel);

        userModel.setFederationLink(model.getId());
        userModel.setEnabled(legacyUser.isEnabled());
        userModel.setEmail(legacyUser.getEmail());
        userModel.setEmailVerified(legacyUser.isEmailVerified());
        userModel.setFirstName(legacyUser.getFirstName());
        userModel.setLastName(legacyUser.getLastName());

        if (legacyUser.getAttributes() != null) {
            legacyUser.getAttributes()
                    .forEach(userModel::setAttribute);
        }

        getRoleModels(legacyUser, realm)
                .forEach(userModel::grantRole);

        return userModel;
    }

    private void validateUsernamesEqual(LegacyUser legacyUser, UserModel userModel) {
        if (!userModel.getUsername().equals(legacyUser.getUsername())) {
            throw new IllegalStateException(String.format("Local and remote users differ: [%s != %s]",
                    userModel.getUsername(),
                    legacyUser.getUsername()));
        }
    }

    private Stream<RoleModel> getRoleModels(LegacyUser legacyUser, RealmModel realm) {
        if (legacyUser.getRoles() == null) {
            return Stream.empty();
        }
        return legacyUser.getRoles().stream()
                .map(r -> getRoleModel(realm, r))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private Optional<RoleModel> getRoleModel(RealmModel realm, String role) {
        if (roleMap.containsKey(role)) {
            role = roleMap.get(role);
        } else if (!shouldParseUnmappedRoles()) {
            return Optional.empty();
        }
        if (role == null || role.equals("")) {
            return Optional.empty();
        }
        RoleModel roleModel = realm.getRole(role);
        return Optional.ofNullable(roleModel);
    }

    private boolean shouldParseUnmappedRoles() {
        return Boolean.parseBoolean(model.getConfig().getFirst(MIGRATE_UNMAPPED_ROLES_PROPERTY));
    }
}
