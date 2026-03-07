package com.danielfrak.code.keycloak.providers.rest.remote.usermodel;

import com.danielfrak.code.keycloak.providers.rest.MigrationConfiguration;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyOrganization;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyOrganizationDomain;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import org.jboss.logging.Logger;
import org.keycloak.models.*;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.organization.OrganizationProvider;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UserModelFactory {

    private static final Logger LOG = Logger.getLogger(UserModelFactory.class);

    private final KeycloakSession session;
    private final MigrationConfiguration config;
    private final RoleMigrationService roleMigrationService;
    private final GroupMigrationService groupMigrationService;

    public UserModelFactory(KeycloakSession session,
                            MigrationConfiguration config,
                            RoleMigrationService roleMigrationService,
                            GroupMigrationService groupMigrationService) {
        this.session = session;
        this.config = config;
        this.roleMigrationService = roleMigrationService;
        this.groupMigrationService = groupMigrationService;
    }

    public UserModel create(LegacyUser legacyUser, RealmModel realm) {
        LOG.infof("Creating user model for: %s", legacyUser.username());

        UserModel userModel = addUser(legacyUser, realm);
        validateUsernamesEqual(legacyUser, userModel);
        migrateBasicAttributes(legacyUser, userModel);
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

    public void updateUserAttributes(LegacyUser legacyUser, UserModel userModel) {
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

    private void migrateBasicAttributes(LegacyUser legacyUser, UserModel userModel) {
        userModel.setFederationLink(config.getModelId());
        updateUserAttributes(legacyUser, userModel);
    }

    private void migrateRolesOnFirstLogin(LegacyUser legacyUser, RealmModel realm, UserModel userModel) {
        if (!config.getRoleSyncMode().shouldImportOnFirstLogin()) {
            return;
        }
        roleMigrationService.getOrMigrateRoleModels(legacyUser, realm)
                .filter(roleMigrationService::isNotIgnoredRole)
                .forEach(userModel::grantRole);
    }

    private void migrateGroupsOnFirstLogin(LegacyUser legacyUser, RealmModel realm, UserModel userModel) {
        if (!config.getGroupSyncMode().shouldImportOnFirstLogin()) {
            return;
        }
        groupMigrationService.getOrMigrateGroupModels(legacyUser, realm)
                .filter(groupMigrationService::isNotIgnoredGroup)
                .forEach(userModel::joinGroup);
    }

    public void synchronizeRoles(LegacyUser legacyUser, RealmModel realm, UserModel userModel) {
        Set<RoleModel> desiredRoles = roleMigrationService.getOrMigrateRoleModels(legacyUser, realm)
                .filter(roleMigrationService::isNotIgnoredRole)
                .collect(Collectors.toSet());
        Set<RoleModel> currentRoles = userModel.getRoleMappingsStream().collect(Collectors.toSet());

        Reconcile.from(currentRoles)
                .towards(desiredRoles)
                .byKey(this::roleKey)
                .twoWay()
                .removeAllowedIf(roleMigrationService::isNotIgnoredRole)
                .removeWith(userModel::deleteRoleMapping)
                .addWith(userModel::grantRole)
                .apply();
    }

    public void synchronizeRolesAddOnly(LegacyUser legacyUser, RealmModel realm, UserModel userModel) {
        Set<RoleModel> desiredRoles = roleMigrationService.getOrMigrateRoleModels(legacyUser, realm)
                .filter(roleMigrationService::isNotIgnoredRole)
                .collect(Collectors.toSet());
        Set<RoleModel> currentRoles = userModel.getRoleMappingsStream().collect(Collectors.toSet());
        Reconcile.from(currentRoles)
                .towards(desiredRoles)
                .byKey(this::roleKey)
                .oneWay()
                .addWith(userModel::grantRole)
                .apply();
    }

    public void synchronizeGroups(LegacyUser legacyUser, RealmModel realm, UserModel userModel) {
        Set<GroupModel> desiredGroups = groupMigrationService.getOrMigrateGroupModels(legacyUser, realm)
                .filter(groupMigrationService::isNotIgnoredGroup)
                .collect(Collectors.toSet());
        Set<GroupModel> currentGroups = userModel.getGroupsStream().collect(Collectors.toSet());

        Reconcile.from(currentGroups)
                .towards(desiredGroups)
                .byKey(this::groupKey)
                .twoWay()
                .removeAllowedIf(groupMigrationService::isNotIgnoredGroup)
                .removeWith(userModel::leaveGroup)
                .addWith(userModel::joinGroup)
                .apply();
    }

    public void synchronizeGroupsAddOnly(LegacyUser legacyUser, RealmModel realm, UserModel userModel) {
        Set<GroupModel> desiredGroups = groupMigrationService.getOrMigrateGroupModels(legacyUser, realm)
                .filter(groupMigrationService::isNotIgnoredGroup)
                .collect(Collectors.toSet());
        Set<GroupModel> currentGroups = userModel.getGroupsStream().collect(Collectors.toSet());
        Reconcile.from(currentGroups)
                .towards(desiredGroups)
                .byKey(this::groupKey)
                .oneWay()
                .addWith(userModel::joinGroup)
                .apply();
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
        if (!realmModel.isOrganizationsEnabled()) {
            LOG.infof("Organization feature is not active with realm %s", realmModel.getName());
            return;
        }

        List<LegacyOrganization> userOrganization = legacyUser.organizations();
        if (userOrganization == null || userOrganization.isEmpty()) {
            return;
        }

        OrganizationProvider provider = session.getProvider(OrganizationProvider.class);
        userOrganization.forEach(legacyOrg ->
                provider.addManagedMember(getOrCreateOrganization(legacyOrg, provider), userModel)
        );
    }

    private String roleKey(RoleModel roleModel) {
        if (roleModel.getId() != null && !roleModel.getId().isBlank()) {
            return roleModel.getId();
        }
        return "name:" + roleModel.getName();
    }

    private boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }

    private String groupKey(GroupModel groupModel) {
        if (groupModel.getId() != null && !groupModel.getId().isBlank()) {
            return groupModel.getId();
        }
        String name = groupModel.getName();
        return "name:" + (name != null ? name : "");
    }

    public boolean isDuplicateUserId(LegacyUser legacyUser, RealmModel realm) {
        if (isEmpty(legacyUser.id())) {
            return false;
        }

        return session.users().getUserById(realm, legacyUser.id()) != null;
    }

    private OrganizationModel getOrCreateOrganization(
            LegacyOrganization legacyOrganization, OrganizationProvider provider) {
        OrganizationModel byAlias = provider.getByAlias(legacyOrganization.orgAlias());
        if (byAlias != null) {
            return byAlias;
        }

        OrganizationModel organizationModel = provider.create(
                legacyOrganization.orgName(), legacyOrganization.orgAlias()
        );
        List<LegacyOrganizationDomain> domains = legacyOrganization.domains();
        if(domains == null || domains.isEmpty()) {
            return organizationModel;
        }
        Set<OrganizationDomainModel> domainModelSet = domains
                .stream()
                .map(d -> new OrganizationDomainModel(d.domainName(), d.isVerified()))
                .collect(Collectors.toSet());
        organizationModel.setDomains(domainModelSet);

        return organizationModel;
    }
}
