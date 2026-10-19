package com.danielfrak.code.keycloak.providers.rest.remote.usermodel;

import com.danielfrak.code.keycloak.providers.rest.MigrationConfiguration;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyOrganization;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyOrganizationDomain;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyTotp;
import org.jboss.logging.Logger;
import org.keycloak.models.*;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.organization.OrganizationProvider;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HexFormat;
import java.util.stream.Collectors;

public class UserModelFactory {

    private static final Logger LOG = Logger.getLogger(UserModelFactory.class);
    private final KeycloakSession session;
    private final MigrationConfiguration config;
    private final RoleMigrationService roleMigrationService;
    private final GroupMigrationService groupMigrationService;
    private final TotpValidationService totpValidationService;

    public UserModelFactory(KeycloakSession session,
                            MigrationConfiguration config,
                            RoleMigrationService roleMigrationService,
                            GroupMigrationService groupMigrationService,
                            TotpValidationService totpValidationService) {
        this.session = session;
        this.config = config;
        this.roleMigrationService = roleMigrationService;
        this.groupMigrationService = groupMigrationService;
        this.totpValidationService = totpValidationService;
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
        String id = isEmpty(legacyUser.id()) ? null : legacyUser.id();
        boolean addDefaultRoles = true;
        return session.users().addUser(
                realm,
                id,
                legacyUser.username(),
                addDefaultRoles,
                legacyUser.shouldAddDefaultRequiredActions()
        );
    }

    private void validateUsernamesEqual(LegacyUser legacyUser, UserModel userModel) {
        if (!userModel.getUsername().equalsIgnoreCase(legacyUser.username())) {
            throw new IllegalStateException(String.format("Local and remote users differ: [%s != %s]",
                    userModel.getUsername(),
                    legacyUser.username()));
        }
    }

    public void updateUserAttributes(LegacyUser legacyUser, UserModel userModel) {
        userModel.setUsername(legacyUser.username());
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
            legacyUser.totps().forEach(totp ->
                    userModel.credentialManager().createStoredCredential(totpValidationService.toTotpCredential(totp)));
        }
    }

    public void synchronizeTotps(LegacyUser legacyUser, UserModel userModel) {
        var desired = totpValidationService.validateTotps(legacyUser.totps()).stream().collect(Collectors.toSet());
        var credentials = userModel.credentialManager();
        var current = credentials.getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE)
                .map(OTPCredentialModel::createFromCredentialModel)
                .filter(otp -> OTPCredentialModel.TOTP.equals(otp.getOTPCredentialData().getSubType()))
                .collect(Collectors.toSet());

        Reconcile.from(current)
                .towards(desired)
                .byKey(this::totpKey)
                .twoWay()
                .removeAllowedIf(ignored -> true)
                .removeWith(credential -> {
                    if (!credentials.removeStoredCredentialById(credential.getId())) {
                        throw new IllegalStateException("Could not remove stale TOTP credential.");
                    }
                })
                .addWith(credential -> {
                    if (credentials.createStoredCredential(credential) == null) {
                        throw new IllegalStateException("Could not store synchronized TOTP credential.");
                    }
                })
                .apply();
    }

    private String totpKey(OTPCredentialModel credential) {
        var data = credential.getOTPCredentialData();
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(credential.getDecodedSecret());
            var label = credential.getUserLabel();
            var labelKey = label == null ? "null" : "value"
                    + Base64.getEncoder().encodeToString(label.getBytes(StandardCharsets.UTF_8));
            return labelKey + "|" + data.getDigits() + "|"
                    + data.getPeriod() + "|" + data.getAlgorithm() + "|" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
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
