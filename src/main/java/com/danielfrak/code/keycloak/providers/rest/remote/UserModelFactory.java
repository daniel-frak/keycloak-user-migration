package com.danielfrak.code.keycloak.providers.rest.remote;

import com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties;
import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.*;
import org.keycloak.models.credential.OTPCredentialModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;

public class UserModelFactory {

    private static final Logger LOG = Logger.getLogger(UserModelFactory.class);

    private final KeycloakSession session;
    private final ComponentModel model;

    /**
     * String format:
     * legacyRole:newRole
     */
    private final Map<String, String> roleMap;
    /**
     * String format:
     * legacyGroup:newGroup
     */
    private final Map<String, String> groupMap;

    public UserModelFactory(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
        this.roleMap = legacyMap(model, ROLE_MAP_PROPERTY);
        this.groupMap = legacyMap(model, GROUP_MAP_PROPERTY);
    }

    /**
     * Returns a map of legacy props to new one
     */
    private Map<String, String> legacyMap(ComponentModel model, String property) {
        Map<String, String> newRoleMap = new HashMap<>();
        List<String> pairs = model.getConfig().getList(property);
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            newRoleMap.put(keyValue[0], keyValue[1]);
        }
        return newRoleMap;
    }

    public UserModel create(LegacyUser legacyUser, RealmModel realm) {
        LOG.infof("Creating user model for: %s", legacyUser.getUsername());

        UserModel userModel;
        if (isEmpty(legacyUser.getId())) {
            userModel = session.users().addUser(realm, legacyUser.getUsername());
        } else {
            userModel = session.users().addUser(
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

        getGroupModels(legacyUser, realm)
                .forEach(userModel::joinGroup);

        if (legacyUser.getRequiredActions() != null) {
            legacyUser.getRequiredActions()
                .forEach(userModel::addRequiredAction);
        }

        if (legacyUser.getTotps() != null) {
            legacyUser.getTotps().forEach(totp -> {
                var otpModel = OTPCredentialModel.createTOTP(
                    totp.getSecret(), 
                    totp.getDigits(), 
                    totp.getPeriod(), 
                    totp.getAlgorithm(), 
                    totp.getEncoding());
                otpModel.setUserLabel(totp.getName());
                userModel.credentialManager().createStoredCredential(otpModel);
            });
        }

        return userModel;
    }

    public boolean isDuplicateUserId(LegacyUser legacyUser, RealmModel realm) {
        if (isEmpty(legacyUser.getId())) {
            return false;
        }

        return session.users().getUserById(realm, legacyUser.getId()) != null;
    }

    private void validateUsernamesEqual(LegacyUser legacyUser, UserModel userModel) {
        if (!userModel.getUsername().equalsIgnoreCase(legacyUser.getUsername())) {
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

    /**
     * @return A {@link RoleModel} for this role in the realm.
     * Created if not found in the realm or in any of the realm's clients.
     * Migrated only if present in the map or config enables this.
     * @see ConfigurationProperties#MIGRATE_UNMAPPED_ROLES_PROPERTY
     */
    private Optional<RoleModel> getRoleModel(RealmModel realm, String role) {
        if (roleMap.containsKey(role)) {
            role = roleMap.get(role);
        } else if (isConfigDisabled(MIGRATE_UNMAPPED_ROLES_PROPERTY)) {
            return Optional.empty();
        }
        if (isEmpty(role)) {
            return Optional.empty();
        }
        String finalRoleName = role;
        return Optional.ofNullable(realm.getRole(role))
                .or(() -> realm.getClientsStream()
                        .map(clientModel -> clientModel.getRole(finalRoleName))
                        .filter(Objects::nonNull)
                        .findFirst())
                .or(() -> {
                    LOG.debug(String.format("Added role %s to realm %s", finalRoleName, realm.getName()));
                    return Optional.ofNullable(realm.addRole(finalRoleName));
                });
    }

    private boolean isConfigDisabled(String config) {
        return !Boolean.parseBoolean(model.getConfig().getFirst(config));
    }

    private boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }

    private Stream<GroupModel> getGroupModels(LegacyUser legacyUser, RealmModel realm) {
        if (legacyUser.getGroups() == null) {
            return Stream.empty();
        }

        return legacyUser.getGroups().stream()
                .map(group -> getGroupModel(realm, group))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private Optional<GroupModel> getGroupModel(RealmModel realm, String groupId) {
        if (groupMap.containsKey(groupId)) {
            groupId = groupMap.get(groupId);
        } else if (isConfigDisabled(MIGRATE_UNMAPPED_GROUPS_PROPERTY)) {
            return Optional.empty();
        }
        if (isEmpty(groupId)) {
            return Optional.empty();
        }

        final String effectiveGroupId = groupId;
        Optional<GroupModel> group = realm.getGroupsStream()
                .filter(g ->
                        effectiveGroupId.equalsIgnoreCase(g.getId()) ||
                        effectiveGroupId.equalsIgnoreCase(g.getName())
                ).findFirst();

        GroupModel realmGroup = group
                .map(g -> {
                    LOG.infof("Found existing group %s with id %s", g.getName(), g.getId());
                    return g;
                })
                .orElseGet(() -> {
                    GroupModel newGroup = realm.createGroup(effectiveGroupId);
                    LOG.infof("Created group %s with id %s", newGroup.getName(), newGroup.getId());
                    return newGroup;
                });

        return Optional.of(realmGroup);
    }
}
