package com.danielfrak.code.keycloak.providers.rest.remote;

import com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties;
import com.danielfrak.code.keycloak.providers.rest.UserSyncMode;
import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.*;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.organization.OrganizationProvider;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
        LOG.infof("Creating user model for: %s", legacyUser.username());

        UserModel userModel = addUser(legacyUser, realm);
        validateUsernamesEqual(legacyUser, userModel);
        migrateBasicAttributes(legacyUser, userModel);
        migrateAdditionalAttributes(legacyUser, userModel);
        migrateRolesOnFirstLogin(legacyUser, realm, userModel);
        migrateGroupsOnFirstLogin(legacyUser, realm, userModel);
        migrateRequiredActions(legacyUser, userModel);
        migrateTotp(legacyUser, userModel);
        migrateOrganizations(legacyUser, userModel, realm);

        return userModel;
    }

    private UserModel addUser(LegacyUser legacyUser, RealmModel realm) {
        UserModel userModel;
        if (isEmpty(legacyUser.id())) {
            userModel = addUserWithoutLegacyId(legacyUser, realm);
        } else {
            userModel = addUserWithLegacyId(legacyUser, realm);
        }
        return userModel;
    }

    private UserModel addUserWithoutLegacyId(LegacyUser legacyUser, RealmModel realm) {
        UserModel userModel;
        userModel = session.users().addUser(realm, legacyUser.username());
        return userModel;
    }

    private UserModel addUserWithLegacyId(LegacyUser legacyUser, RealmModel realm) {
        UserModel userModel;
        boolean addDefaultRoles = true;
        boolean dontAddDefaultRequiredActions = false;
        userModel = session.users().addUser(
                realm,
                legacyUser.id(),
                legacyUser.username(),
                addDefaultRoles,
                dontAddDefaultRequiredActions
        );
        return userModel;
    }

    private void validateUsernamesEqual(LegacyUser legacyUser, UserModel userModel) {
        if (!userModel.getUsername().equals(legacyUser.username())) {
            throw new IllegalStateException(String.format("Local and remote users differ: [%s != %s]",
                    userModel.getUsername(),
                    legacyUser.username()));
        }
    }

    private void migrateBasicAttributes(LegacyUser legacyUser, UserModel userModel) {
        userModel.setFederationLink(model.getId());
        userModel.setEnabled(legacyUser.isEnabled());
        userModel.setEmail(legacyUser.email());
        userModel.setEmailVerified(legacyUser.isEmailVerified());
        userModel.setFirstName(legacyUser.firstName());
        userModel.setLastName(legacyUser.lastName());
    }

    private void migrateAdditionalAttributes(LegacyUser legacyUser, UserModel userModel) {
        if (legacyUser.attributes() != null) {
            legacyUser.attributes()
                    .forEach(userModel::setAttribute);
        }
    }

    private void migrateRolesOnFirstLogin(LegacyUser legacyUser, RealmModel realm, UserModel userModel) {
        if (!roleSyncMode().shouldImportOnFirstLogin()) {
            return;
        }
        getRoleModels(legacyUser, realm)
                .filter(this::isNotIgnoredRole)
                .forEach(userModel::grantRole);
    }

    private void migrateGroupsOnFirstLogin(LegacyUser legacyUser, RealmModel realm, UserModel userModel) {
        if (!groupSyncMode().shouldImportOnFirstLogin()) {
            return;
        }
        getGroupModels(legacyUser, realm)
                .filter(this::isNotIgnoredGroup)
                .forEach(userModel::joinGroup);
    }

    public void synchronizeRoles(LegacyUser legacyUser, RealmModel realm, UserModel userModel) {
        Set<RoleModel> desiredRoles = getRoleModels(legacyUser, realm)
                .filter(this::isNotIgnoredRole)
                .collect(Collectors.toSet());
        Set<RoleModel> currentRoles = userModel.getRoleMappingsStream().collect(Collectors.toSet());

        reconcile(
                currentRoles,
                desiredRoles,
                this::roleKey,
                this::isNotIgnoredRole,
                userModel::deleteRoleMapping,
                userModel::grantRole
        );
    }

    public void synchronizeRolesAddOnly(LegacyUser legacyUser, RealmModel realm, UserModel userModel) {
        Set<RoleModel> desiredRoles = getRoleModels(legacyUser, realm)
                .filter(this::isNotIgnoredRole)
                .collect(Collectors.toSet());
        Set<RoleModel> currentRoles = userModel.getRoleMappingsStream().collect(Collectors.toSet());
        addMissing(currentRoles, desiredRoles, this::roleKey, userModel::grantRole);
    }

    public void synchronizeGroups(LegacyUser legacyUser, RealmModel realm, UserModel userModel) {
        Set<GroupModel> desiredGroups = getGroupModels(legacyUser, realm)
                .filter(this::isNotIgnoredGroup)
                .collect(Collectors.toSet());
        Set<GroupModel> currentGroups = userModel.getGroupsStream().collect(Collectors.toSet());

        reconcile(
                currentGroups,
                desiredGroups,
                this::groupKey,
                this::isNotIgnoredGroup,
                userModel::leaveGroup,
                userModel::joinGroup
        );
    }

    public void synchronizeGroupsAddOnly(LegacyUser legacyUser, RealmModel realm, UserModel userModel) {
        Set<GroupModel> desiredGroups = getGroupModels(legacyUser, realm)
                .filter(this::isNotIgnoredGroup)
                .collect(Collectors.toSet());
        Set<GroupModel> currentGroups = userModel.getGroupsStream().collect(Collectors.toSet());
        addMissing(currentGroups, desiredGroups, this::groupKey, userModel::joinGroup);
    }

    private void migrateRequiredActions(LegacyUser legacyUser, UserModel userModel) {
        if (legacyUser.requiredActions() != null) {
            legacyUser.requiredActions()
                    .forEach(userModel::addRequiredAction);
        }
    }

    private void migrateTotp(LegacyUser legacyUser, UserModel userModel) {
        if (legacyUser.totps() != null) {
            legacyUser.totps().forEach(totp -> {
                var otpModel = OTPCredentialModel.createTOTP(
                        totp.secret(),
                        totp.digits(),
                        totp.period(),
                        totp.algorithm(),
                        totp.encoding());
                otpModel.setUserLabel(totp.name());
                userModel.credentialManager().createStoredCredential(otpModel);
            });
        }
    }

    private void migrateOrganizations(LegacyUser legacyUser, UserModel userModel, RealmModel realmModel) {
        if(!realmModel.isOrganizationsEnabled()) {
            LOG.infof("Organization feature is not active with realm %s", realmModel.getName());
            return;
        }

        OrganizationProvider provider = session.getProvider(OrganizationProvider.class);
        List<LegacyOrganization> userOrganization = legacyUser.organizations();
        if(userOrganization == null || userOrganization.isEmpty()) {
            return;
        }

        userOrganization.forEach(legacyOrg ->
                provider.addManagedMember(getOrCreateOrganization(legacyOrg, provider), userModel)
        );
    }

    private Stream<RoleModel> getRoleModels(LegacyUser legacyUser, RealmModel realm) {
        if (legacyUser.roles() == null) {
            return Stream.empty();
        }
        return legacyUser.roles().stream()
                .map(legacyRoleName -> getMappedRoleModel(realm, legacyRoleName))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    /**
     * @return A {@link RoleModel} for this role in the realm.
     * Created if not found in the realm or in any of the realm's clients.
     * Migrated only if present in the map or config enables this.
     * @see ConfigurationProperties#MIGRATE_UNMAPPED_ROLES_PROPERTY
     */
    private Optional<RoleModel> getMappedRoleModel(RealmModel realm, String roleName) {
        return getMappedRoleName(roleName)
                .filter(mappedRoleName -> !isEmpty(mappedRoleName))
                .flatMap(mappedRoleName -> getRoleModel(realm, mappedRoleName));
    }

    private Optional<RoleModel> getRoleModel(RealmModel realm, String roleName) {
        return Optional.ofNullable(realm.getRole(roleName))
                .or(() -> getFirstFoundClientRoleModel(realm, roleName))
                .or(() -> addRoleToRealm(realm, roleName));
    }

    private Optional<RoleModel> getFirstFoundClientRoleModel(RealmModel realm, String roleName) {
        return realm.getClientsStream()
                .map(clientModel -> clientModel.getRole(roleName))
                .filter(Objects::nonNull)
                .findFirst();
    }

    private Optional<RoleModel> addRoleToRealm(RealmModel realm, String roleName) {
        LOG.debug(String.format("Added role %s to realm %s", roleName, realm.getName()));
        return Optional.ofNullable(realm.addRole(roleName));
    }

    private String roleKey(RoleModel roleModel) {
        if (roleModel.getId() != null && !roleModel.getId().isBlank()) {
            return roleModel.getId();
        }
        return "name:" + roleModel.getName();
    }

    private Optional<String> getMappedRoleName(String roleName) {
        if (roleMap.containsKey(roleName)) {
            return Optional.ofNullable(roleMap.get(roleName));
        } else if (isConfigDisabled(MIGRATE_UNMAPPED_ROLES_PROPERTY)) {
            return Optional.empty();
        }
        return Optional.ofNullable(roleName);
    }

    private boolean isConfigDisabled(String config) {
        return !Boolean.parseBoolean(model.getConfig().getFirst(config));
    }

    private boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }

    private Stream<GroupModel> getGroupModels(LegacyUser legacyUser, RealmModel realm) {
        if (legacyUser.groups() == null) {
            return Stream.empty();
        }

        return legacyUser.groups().stream()
                .map(group -> getMappedGroupModel(realm, group))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private Optional<GroupModel> getMappedGroupModel(RealmModel realm, String groupName) {
        return getMappedGroupName(groupName)
                .filter(mappedGroupName -> !isEmpty(mappedGroupName))
                .map(mappedGroupName -> getGroupModel(realm, mappedGroupName));
    }

    private Optional<String> getMappedGroupName(String groupName) {
        if (groupMap.containsKey(groupName)) {
            return Optional.ofNullable(groupMap.get(groupName));
        } else if (isConfigDisabled(MIGRATE_UNMAPPED_GROUPS_PROPERTY)) {
            return Optional.empty();
        }
        return Optional.ofNullable(groupName);
    }

    private GroupModel getGroupModel(RealmModel realm, String mappedGroupName) {
        return realm.getGroupsStream()
                .filter(g -> mappedGroupName.equalsIgnoreCase(g.getName())).findFirst()
                .map(this::getExistingGroup)
                .orElseGet(() -> createGroup(realm, mappedGroupName));
    }

    private GroupModel getExistingGroup(GroupModel g) {
        LOG.infof("Found existing group %s with id %s", g.getName(), g.getId());
        return g;
    }

    private GroupModel createGroup(RealmModel realm, String mappedGroupName) {
        GroupModel newGroup = realm.createGroup(mappedGroupName);
        LOG.infof("Created group %s with id %s", newGroup.getName(), newGroup.getId());
        return newGroup;
    }

    private String groupKey(GroupModel groupModel) {
        if (groupModel.getId() != null && !groupModel.getId().isBlank()) {
            return groupModel.getId();
        }
        return "name:" + groupModel.getName();
    }

    private UserSyncMode roleSyncMode() {
        String value = model.getConfig().getFirst(UPDATE_USER_ROLES_ON_LOGIN);
        return UserSyncMode.fromConfig(value, UserSyncMode.SYNC_FIRST_LOGIN);
    }

    private UserSyncMode groupSyncMode() {
        String value = model.getConfig().getFirst(UPDATE_USER_GROUPS_ON_LOGIN);
        return UserSyncMode.fromConfig(value, UserSyncMode.SYNC_FIRST_LOGIN);
    }

    private boolean isNotIgnoredRole(RoleModel roleModel) {
        String roleName = roleModel.getName();
        if (roleName == null || roleName.isBlank()) {
            return true;
        }
        return ignoredRolePatterns().stream().noneMatch(pattern -> wildcardMatch(pattern, roleName));
    }

    private boolean isNotIgnoredGroup(GroupModel groupModel) {
        String groupName = groupModel.getName();
        if (groupName == null || groupName.isBlank()) {
            return true;
        }
        return ignoredGroupPatterns().stream().noneMatch(pattern -> wildcardMatch(pattern, groupName));
    }

    private List<String> ignoredRolePatterns() {
        List<String> configured = model.getConfig().getList(IGNORED_SYNC_ROLES_PROPERTY);
        if (configured == null || configured.isEmpty()) {
            return ConfigurationProperties.DEFAULT_IGNORED_SYNC_ROLES;
        }
        return configured;
    }

    private List<String> ignoredGroupPatterns() {
        List<String> configured = model.getConfig().getList(IGNORED_SYNC_GROUPS_PROPERTY);
        if (configured == null) {
            return List.of();
        }
        return configured;
    }

    private boolean wildcardMatch(String pattern, String value) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        String regex = Pattern.quote(pattern.trim()).replace("*", "\\E.*\\Q");
        return value.matches("^" + regex + "$");
    }

    public boolean isDuplicateUserId(LegacyUser legacyUser, RealmModel realm) {
        if (isEmpty(legacyUser.id())) {
            return false;
        }

        return session.users().getUserById(realm, legacyUser.id()) != null;
    }

    private OrganizationModel getOrCreateOrganization(LegacyOrganization legacyOrganization, OrganizationProvider provider) {
        OrganizationModel byAlias = provider.getByAlias(legacyOrganization.orgAlias());
        if(byAlias != null) {
            return byAlias;
        }

        return provider.create(legacyOrganization.orgName(), legacyOrganization.orgAlias());
    }

    private <T> void reconcile(
            Set<T> currentItems,
            Set<T> desiredItems,
            Function<T, String> keyMapper,
            Predicate<T> removeAllowed,
            Consumer<T> removeAction,
            Consumer<T> addAction
    ) {
        Map<String, T> currentByKey = currentItems.stream()
                .collect(Collectors.toMap(keyMapper, Function.identity(), (a, b) -> a));
        Map<String, T> desiredByKey = desiredItems.stream()
                .collect(Collectors.toMap(keyMapper, Function.identity(), (a, b) -> a));

        currentByKey.keySet().stream()
                .filter(key -> !desiredByKey.containsKey(key))
                .map(currentByKey::get)
                .filter(removeAllowed)
                .forEach(removeAction);

        desiredByKey.keySet().stream()
                .filter(key -> !currentByKey.containsKey(key))
                .map(desiredByKey::get)
                .forEach(addAction);
    }

    private <T> void addMissing(
            Set<T> currentItems,
            Set<T> desiredItems,
            Function<T, String> keyMapper,
            Consumer<T> addAction
    ) {
        Map<String, T> currentByKey = currentItems.stream()
                .collect(Collectors.toMap(keyMapper, Function.identity(), (a, b) -> a));
        Map<String, T> desiredByKey = desiredItems.stream()
                .collect(Collectors.toMap(keyMapper, Function.identity(), (a, b) -> a));

        desiredByKey.keySet().stream()
                .filter(key -> !currentByKey.containsKey(key))
                .map(desiredByKey::get)
                .forEach(addAction);
    }

}
