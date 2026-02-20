package com.danielfrak.code.keycloak.providers.rest.remote;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.*;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.organization.OrganizationProvider;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;
import static com.danielfrak.code.keycloak.providers.rest.remote.TestLegacyUser.*;
import static com.danielfrak.code.keycloak.providers.rest.remote.TestLegacyUser.aLegacyUserWithOneOrg;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserModelFactoryTest {

    private static final String MODEL_ID = "modelId";

    private UserModelFactory userModelFactory;

    private MultivaluedHashMap<String, String> config;

    @Mock
    private KeycloakSession session;

    @Mock
    private ComponentModel model;

    @Mock
    private UserProvider userProvider;

    @Mock
    private OrganizationProvider organizationProvider;

    @Mock
    private RealmModel realm;

    @BeforeEach
    void setUp() {
        config = new MultivaluedHashMap<>();
        when(model.getConfig())
                .thenReturn(config);
        lenient().when(model.getId())
                .thenReturn(MODEL_ID);
        lenient().when(session.users())
                .thenReturn(userProvider);
        lenient().when(session.getProvider(OrganizationProvider.class))
                .thenReturn(organizationProvider);
    }

    @Test
    void shouldCreateMinimalUser() {
        final LegacyUser legacyUser = aMinimalLegacyUser();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result).isNotNull();
    }

    private void mockSuccessfulUserModelCreationWithoutIdMigration(LegacyUser legacyUser) {
        when(userProvider.addUser(realm, legacyUser.username()))
                .thenReturn(matchingUserModel(legacyUser));
    }

    private TestUserModel matchingUserModel(LegacyUser legacyUser) {
        return new TestUserModel(legacyUser.username(), legacyUser.id());
    }

    private UserModelFactory constructUserModelFactory() {
        return new UserModelFactory(session, model);
    }

    @Test
    void shouldThrowExceptionGivenUserModelCreatedWithUsernameDifferentThanLegacy() {
        final LegacyUser legacyUser = aMinimalLegacyUser();
        mockUserModelCreatedWithWrongUsernameWithoutIdMigration(legacyUser);
        userModelFactory = constructUserModelFactory();

        assertThatThrownBy(() -> userModelFactory.create(legacyUser, realm))
                .isInstanceOf(IllegalStateException.class);
    }

    private void mockUserModelCreatedWithWrongUsernameWithoutIdMigration(LegacyUser legacyUser) {
        when(userProvider.addUser(realm, legacyUser.username()))
                .thenReturn(new TestUserModel("wrong_username"));
    }

    @Test
    void shouldMigrateBasicAttributes() {
        final LegacyUser legacyUser = aMinimalLegacyUser();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertBasicAttributesWereMigrated(result, legacyUser);
    }

    private void assertBasicAttributesWereMigrated(UserModel result, LegacyUser legacyUser) {
        assertThat(result.getUsername()).isEqualTo(legacyUser.username());
        assertThat(result.getEmail()).isEqualTo(legacyUser.email());
        assertThat(result.isEmailVerified()).isEqualTo(legacyUser.isEmailVerified());
        assertThat(result.isEnabled()).isEqualTo(legacyUser.isEnabled());
        assertThat(result.getFirstName()).isEqualTo(legacyUser.firstName());
        assertThat(result.getLastName()).isEqualTo(legacyUser.lastName());
    }

    @Test
    void shouldMigrateLegacyUserId() {
        final LegacyUser legacyUser = aLegacyUserWithId();
        mockSuccessfulUserModelCreationWithIdMigration(legacyUser);
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result.getId()).isEqualTo(legacyUser.id());
    }

    private void mockSuccessfulUserModelCreationWithIdMigration(LegacyUser legacyUser) {
        final boolean addDefaultRoles = true;
        final boolean dontAddDefaultRequiredActions = false;
        when(userProvider.addUser(realm, legacyUser.id(), legacyUser.username(),
                addDefaultRoles, dontAddDefaultRequiredActions))
                .thenReturn(matchingUserModel(legacyUser));
    }

    @Test
    void shouldMigrateMappedRolesAndIgnoreUnmappedRoles() {
        final LegacyUser legacyUser = aLegacyUserWithTwoRoles();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        configureNoMigrationOfUnmappedRoles();
        final String newRoleName = configureMappingForFirstRole(legacyUser);
        mockRoleDoesNotExistInRealm(newRoleName);
        RoleModel newRoleModel = mockAddingRoleToRealm(newRoleName);
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result.getRoleMappingsStream().toList())
                .containsExactly(newRoleModel);
    }

    private void configureNoMigrationOfUnmappedRoles() {
        config.putSingle(MIGRATE_UNMAPPED_ROLES_PROPERTY, "false");
    }

    private String configureMappingForFirstRole(LegacyUser legacyUser) {
        final String oldRole = legacyUser.roles().getFirst();
        final var newRole = "newRole";
        config.put(ROLE_MAP_PROPERTY, List.of(oldRole + ":" + newRole));

        return newRole;
    }

    private void mockRoleDoesNotExistInRealm(String roleName) {
        when(realm.getRole(roleName))
                .thenReturn(null);
    }

    private RoleModel mockAddingRoleToRealm(String roleName) {
        final RoleModel roleModel = mock(RoleModel.class);
        when(realm.addRole(roleName))
                .thenReturn(roleModel);

        return roleModel;
    }

    @Test
    void shouldMigrateMappedRolesAndUnmappedRoles() {
        final LegacyUser legacyUser = aLegacyUserWithTwoRoles();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        configureMigrationOfUnmappedRoles();
        String mappedRoleName = configureMappingForFirstRole(legacyUser);
        String unmappedRoleName = legacyUser.roles().get(1);
        mockRoleDoesNotExistInRealm(mappedRoleName);
        mockRoleDoesNotExistInRealm(unmappedRoleName);
        RoleModel mappedRoleModel = mockAddingRoleToRealm(mappedRoleName);
        RoleModel unmappedRoleModel = mockAddingRoleToRealm(unmappedRoleName);
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result.getRoleMappingsStream().toList())
                .containsExactlyInAnyOrder(mappedRoleModel, unmappedRoleModel);
    }

    private void configureMigrationOfUnmappedRoles() {
        config.putSingle(MIGRATE_UNMAPPED_ROLES_PROPERTY, "true");
    }

    @Test
    void shouldMigrateMappedClientRolesAndIgnoreUnmappedClientRoles() {
        final LegacyUser legacyUser = aLegacyUserWithTwoRoles();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        configureNoMigrationOfUnmappedRoles();
        final var newRole = configureMappingForFirstRole(legacyUser);
        userModelFactory = constructUserModelFactory();

        final ClientModel clientModel1 = mockRealmHasClient();
        final RoleModel newRoleModel = mockClientHasRole(clientModel1, newRole);

        UserModel result = userModelFactory.create(legacyUser, realm);

        verify(clientModel1, times(1)).getRole(any());
        assertThat(result.getRoleMappingsStream().toList())
                .containsExactlyInAnyOrder(newRoleModel);
    }

    private ClientModel mockRealmHasClient() {
        final ClientModel clientModel = mock(ClientModel.class);
        mockRealmHasClients(clientModel);

        return clientModel;
    }

    private void mockRealmHasClients(ClientModel... clientModels) {
        when(realm.getClientsStream())
                .then(i -> Stream.of(clientModels));
    }

    private RoleModel mockClientHasRole(ClientModel clientModel, String role) {
        final RoleModel anotherRoleModel = mock(RoleModel.class);
        when(clientModel.getRole(role))
                .thenReturn(anotherRoleModel);

        return anotherRoleModel;
    }

    @Test
    void shouldAssignExistingRoleToUser() {
        final LegacyUser legacyUser = aLegacyUserWithTwoRoles();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        configureNoMigrationOfUnmappedRoles();
        String mappedRoleName = configureMappingForFirstRole(legacyUser);
        RoleModel mappedRoleModel = mockRoleExistsInRealm(mappedRoleName);
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result.getRoleMappingsStream().toList())
                .containsExactly(mappedRoleModel);
    }

    private RoleModel mockRoleExistsInRealm(String roleName) {
        final RoleModel roleModel = mock(RoleModel.class);
        when(realm.getRole(roleName))
                .thenReturn(roleModel);
        return roleModel;
    }

    @Test
    void shouldMigrateUserWithNullAndEmptyRoles() {
        final LegacyUser legacyUser = aLegacyUserWithNullAndEmptyRoles();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        configureMigrationOfUnmappedRoles();
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result.getRoleMappingsStream().toList()).isEmpty();
    }

    @Test
    void shouldSynchronizeRolesByAddingMissingAndRemovingStaleMappings() {
        final LegacyUser legacyUser = aLegacyUserWithTwoRoles();
        configureMigrationOfUnmappedRoles();
        userModelFactory = constructUserModelFactory();

        RoleModel mappedRole = mock(RoleModel.class);
        when(mappedRole.getName()).thenReturn(legacyUser.roles().getFirst());
        RoleModel missingRole = mock(RoleModel.class);
        when(missingRole.getName()).thenReturn(legacyUser.roles().get(1));
        RoleModel staleRole = mock(RoleModel.class);
        when(staleRole.getName()).thenReturn("staleRole");
        when(realm.getRole(legacyUser.roles().getFirst())).thenReturn(mappedRole);
        when(realm.getRole(legacyUser.roles().get(1))).thenReturn(missingRole);

        UserModel userModel = mock(UserModel.class);
        when(userModel.getRoleMappingsStream()).thenReturn(Stream.of(mappedRole, staleRole));

        userModelFactory.synchronizeRoles(legacyUser, realm, userModel);

        verify(userModel).grantRole(missingRole);
        verify(userModel).deleteRoleMapping(staleRole);
        verify(userModel, never()).deleteRoleMapping(mappedRole);
    }

    @Test
    void shouldSynchronizeRolesByOnlyAddingMissingMappings() {
        final LegacyUser legacyUser = aLegacyUserWithTwoRoles();
        configureMigrationOfUnmappedRoles();
        userModelFactory = constructUserModelFactory();

        RoleModel mappedRole = mock(RoleModel.class);
        when(mappedRole.getName()).thenReturn(legacyUser.roles().getFirst());
        RoleModel missingRole = mock(RoleModel.class);
        when(missingRole.getName()).thenReturn(legacyUser.roles().get(1));
        RoleModel staleRole = mock(RoleModel.class);
        when(staleRole.getName()).thenReturn("staleRole");
        when(realm.getRole(legacyUser.roles().getFirst())).thenReturn(mappedRole);
        when(realm.getRole(legacyUser.roles().get(1))).thenReturn(missingRole);

        UserModel userModel = mock(UserModel.class);
        when(userModel.getRoleMappingsStream()).thenReturn(Stream.of(mappedRole, staleRole));

        userModelFactory.synchronizeRolesAddOnly(legacyUser, realm, userModel);

        verify(userModel).grantRole(missingRole);
        verify(userModel, never()).deleteRoleMapping(any());
    }

    @Test
    void shouldMigrateMappedAndUnmappedClientRoles() {
        final LegacyUser legacyUser = aLegacyUserWithTwoRoles();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        configureMigrationOfUnmappedRoles();
        final String mappedRoleName = configureMappingForFirstRole(legacyUser);
        final String unmappedRoleName = legacyUser.roles().get(1);
        userModelFactory = constructUserModelFactory();

        final ClientModel clientModel1 = mock(ClientModel.class);
        final RoleModel roleModel1 = mockClientHasRole(clientModel1, mappedRoleName);
        final ClientModel clientModel2 = mock(ClientModel.class);
        final RoleModel roleModel2 = mockClientHasRole(clientModel2, unmappedRoleName);
        mockRealmHasClients(clientModel1, clientModel2);
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        verify(clientModel2, never()).getRole(mappedRoleName); // Only the first found client role will be used
        assertThat(result.getRoleMappingsStream().toList())
                .containsExactlyInAnyOrder(roleModel1, roleModel2);
    }

    @Test
    void shouldNotMigrateClientRoleIfNotFound() {
        final LegacyUser legacyUser = aLegacyUserWithTwoRoles();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        configureMigrationOfUnmappedRoles();
        configureMappingForFirstRole(legacyUser);
        final ClientModel clientModel = mockRealmHasClient();
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        verify(clientModel, times(2)).getRole(any());
        assertThat(result.getRoleMappingsStream().toList())
                .isEmpty();
    }

    @Test
    void shouldMigrateMappedGroupsAndIgnoreUnmappedGroups() {
        final LegacyUser legacyUser = aLegacyUserWithTwoGroups();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        configureNoMigrationOfUnmappedGroups();
        final String newGroup = configureMappingForFirstGroup(legacyUser);
        mockRealmHasNoGroups();
        final GroupModel newGroupModel = mockGroupCreationInRealm(newGroup);
        userModelFactory = constructUserModelFactory();

        var result = userModelFactory.create(legacyUser, realm);

        assertThat(result.getGroupsStream().toList())
                .containsExactly(newGroupModel);
    }

    private void configureNoMigrationOfUnmappedGroups() {
        config.putSingle(MIGRATE_UNMAPPED_GROUPS_PROPERTY, "false");
    }

    private String configureMappingForFirstGroup(LegacyUser legacyUser) {
        final String legacyGroupName = legacyUser.groups().getFirst();
        final var newGroupName = "newGroup";
        config.put(GROUP_MAP_PROPERTY, List.of(legacyGroupName + ":" + newGroupName));

        return newGroupName;
    }

    private GroupModel mockGroupCreationInRealm(String groupName) {
        final GroupModel newGroupModel = mock(GroupModel.class);
        when(realm.createGroup(groupName)).thenReturn(newGroupModel);

        return newGroupModel;
    }

    private void mockRealmHasNoGroups() {
        when(realm.getGroupsStream()).then(i -> Stream.empty());
    }

    @Test
    void shouldMigrateMappedAndUnmappedGroups() {
        final LegacyUser legacyUser = aLegacyUserWithTwoGroups();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        configureMigrationOfUnmappedGroups();
        String mappedGroupName = configureMappingForFirstGroup(legacyUser);
        String unmappedGroupName = legacyUser.groups().get(1);
        mockRealmHasNoGroups();
        final GroupModel mappedGroupModel = mockGroupCreationInRealm(mappedGroupName);
        final GroupModel unmappedGroupModel = mockGroupCreationInRealm(unmappedGroupName);
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result.getGroupsStream().toList())
                .containsExactlyInAnyOrder(mappedGroupModel, unmappedGroupModel);
    }

    private void configureMigrationOfUnmappedGroups() {
        config.putSingle(MIGRATE_UNMAPPED_GROUPS_PROPERTY, "true");
    }

    @Test
    void shouldAddUserToExistingGroup() {
        final LegacyUser legacyUser = aLegacyUserWithTwoGroups();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        configureNoMigrationOfUnmappedGroups();
        final String mappedGroupName = configureMappingForFirstGroup(legacyUser);
        final GroupModel mappedGroupModel = mockGroupExistsInRealm(mappedGroupName);
        userModelFactory = constructUserModelFactory();

        var result = userModelFactory.create(legacyUser, realm);

        assertThat(result.getGroupsStream().toList())
                .containsExactly(mappedGroupModel);
    }

    private GroupModel mockGroupExistsInRealm(String groupName) {
        final GroupModel groupModel = mock(GroupModel.class);
        when(groupModel.getName())
                .thenReturn(groupName);
        when(realm.getGroupsStream())
                .then(i -> Stream.of(groupModel));

        return groupModel;
    }

    @Test
    void shouldMigrateUserWithNullAndEmptyGroups() {
        final LegacyUser legacyUser = aLegacyUserWithNullAndEmptyGroups();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        configureMigrationOfUnmappedGroups();
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result.getGroupsStream().toList()).isEmpty();
    }

    @Test
    void shouldSynchronizeGroupsByAddingMissingAndRemovingStaleMemberships() {
        final LegacyUser legacyUser = aLegacyUserWithTwoGroups();
        configureMigrationOfUnmappedGroups();
        userModelFactory = constructUserModelFactory();

        GroupModel mappedGroup = mock(GroupModel.class);
        when(mappedGroup.getName()).thenReturn(legacyUser.groups().getFirst());
        GroupModel missingGroup = mock(GroupModel.class);
        when(missingGroup.getName()).thenReturn(legacyUser.groups().get(1));
        GroupModel staleGroup = mock(GroupModel.class);
        when(staleGroup.getName()).thenReturn("staleGroup");
        when(realm.getGroupsStream()).then(i -> Stream.of(mappedGroup, missingGroup, staleGroup));

        UserModel userModel = mock(UserModel.class);
        when(userModel.getGroupsStream()).thenReturn(Stream.of(mappedGroup, staleGroup));

        userModelFactory.synchronizeGroups(legacyUser, realm, userModel);

        verify(userModel).joinGroup(missingGroup);
        verify(userModel).leaveGroup(staleGroup);
        verify(userModel, never()).leaveGroup(mappedGroup);
    }

    @Test
    void shouldSynchronizeGroupsByOnlyAddingMissingMemberships() {
        final LegacyUser legacyUser = aLegacyUserWithTwoGroups();
        configureMigrationOfUnmappedGroups();
        userModelFactory = constructUserModelFactory();

        GroupModel mappedGroup = mock(GroupModel.class);
        when(mappedGroup.getName()).thenReturn(legacyUser.groups().getFirst());
        GroupModel missingGroup = mock(GroupModel.class);
        when(missingGroup.getName()).thenReturn(legacyUser.groups().get(1));
        GroupModel staleGroup = mock(GroupModel.class);
        when(staleGroup.getName()).thenReturn("staleGroup");
        when(realm.getGroupsStream()).then(i -> Stream.of(mappedGroup, missingGroup, staleGroup));

        UserModel userModel = mock(UserModel.class);
        when(userModel.getGroupsStream()).thenReturn(Stream.of(mappedGroup, staleGroup));

        userModelFactory.synchronizeGroupsAddOnly(legacyUser, realm, userModel);

        verify(userModel).joinGroup(missingGroup);
        verify(userModel, never()).leaveGroup(any());
    }

    @Test
    void shouldNotImportRolesOnFirstLoginWhenRoleSyncModeIsNoSync() {
        final LegacyUser legacyUser = aLegacyUserWithTwoRoles();
        config.putSingle(UPDATE_USER_ROLES_ON_LOGIN, "NO_SYNC");
        configureMigrationOfUnmappedRoles();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result.getRoleMappingsStream().toList()).isEmpty();
    }

    @Test
    void shouldNotRemoveIgnoredRolesDuringSynchronization() {
        final LegacyUser legacyUser = aLegacyUserWithTwoRoles();
        configureMigrationOfUnmappedRoles();
        config.put(IGNORED_SYNC_ROLES_PROPERTY, List.of("manage-*"));
        userModelFactory = constructUserModelFactory();

        RoleModel mappedRole = mock(RoleModel.class);
        when(mappedRole.getName()).thenReturn(legacyUser.roles().getFirst());
        RoleModel missingRole = mock(RoleModel.class);
        when(missingRole.getName()).thenReturn(legacyUser.roles().get(1));
        RoleModel untrackedRole = mock(RoleModel.class);
        when(untrackedRole.getName()).thenReturn("manage-account");
        when(realm.getRole(legacyUser.roles().getFirst())).thenReturn(mappedRole);
        when(realm.getRole(legacyUser.roles().get(1))).thenReturn(missingRole);

        UserModel userModel = mock(UserModel.class);
        when(userModel.getRoleMappingsStream()).thenReturn(Stream.of(mappedRole, untrackedRole));

        userModelFactory.synchronizeRoles(legacyUser, realm, userModel);

        verify(userModel, never()).deleteRoleMapping(untrackedRole);
    }

    @Test
    void shouldNotImportGroupsOnFirstLoginWhenGroupSyncModeIsNoSync() {
        final LegacyUser legacyUser = aLegacyUserWithTwoGroups();
        config.putSingle(UPDATE_USER_GROUPS_ON_LOGIN, "NO_SYNC");
        configureMigrationOfUnmappedGroups();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result.getGroupsStream().toList()).isEmpty();
    }

    @Test
    void shouldNotRemoveIgnoredGroupsDuringSynchronization() {
        final LegacyUser legacyUser = aLegacyUserWithTwoGroups();
        configureMigrationOfUnmappedGroups();
        config.put(IGNORED_SYNC_GROUPS_PROPERTY, List.of("manual-*"));
        userModelFactory = constructUserModelFactory();

        GroupModel mappedGroup = mock(GroupModel.class);
        when(mappedGroup.getName()).thenReturn(legacyUser.groups().getFirst());
        GroupModel missingGroup = mock(GroupModel.class);
        when(missingGroup.getName()).thenReturn(legacyUser.groups().get(1));
        GroupModel untrackedGroup = mock(GroupModel.class);
        when(untrackedGroup.getName()).thenReturn("manual-group");
        when(realm.getGroupsStream()).then(i -> Stream.of(mappedGroup, missingGroup, untrackedGroup));

        UserModel userModel = mock(UserModel.class);
        when(userModel.getGroupsStream()).thenReturn(Stream.of(mappedGroup, untrackedGroup));

        userModelFactory.synchronizeGroups(legacyUser, realm, userModel);

        verify(userModel, never()).leaveGroup(untrackedGroup);
    }

    @Test
    void shouldMigrateAdditionalAttributes() {
        final LegacyUser legacyUser = aLegacyUserWithOneAttribute();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result.getAttributes())
                .isEqualTo(legacyUser.attributes());
    }

    @Test
    void shouldSetFederationLink() {
        final LegacyUser legacyUser = aMinimalLegacyUser();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result.getFederationLink()).isEqualTo(MODEL_ID);
    }

    @Test
    void shouldMigrateRequiredActions() {
        final LegacyUser legacyUser = aLegacyUserWithTwoRequiredActions();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result.getRequiredActionsStream().toList())
                .containsExactlyInAnyOrderElementsOf(legacyUser.requiredActions());
    }

    @Test
    void shouldMigrateTotp() {
        final LegacyUser legacyUser = aLegacyUserWithTwoTotps();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        final LegacyTotp legacyTotp1 = legacyUser.totps().getFirst();
        final LegacyTotp legacyTotp2 = legacyUser.totps().get(1);
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        List<CredentialModel> storedTotpCredentials = getStoredTotpCredentials(result);
        assertCredentialsMigratedSuccessfully(storedTotpCredentials, legacyTotp1, legacyTotp2);
    }

    private void assertCredentialsMigratedSuccessfully(
            List<CredentialModel> storedTotpCredentials, LegacyTotp legacyTotp1, LegacyTotp legacyTotp2) {
        assertThat(storedTotpCredentials).hasSize(2);
        assertThat(findTotpCredentialByName(storedTotpCredentials, legacyTotp1.name())).get()
                .extracting(CredentialModel::getSecretData)
                .isEqualTo(expectedTotpSecretData(legacyTotp1));
        assertThat(findTotpCredentialByName(storedTotpCredentials, legacyTotp2.name())).get()
                .extracting(CredentialModel::getSecretData)
                .isEqualTo(expectedTotpSecretData(legacyTotp2));
    }

    private Optional<CredentialModel> findTotpCredentialByName(List<CredentialModel> storedTotpCredentials,
                                                               String anObject) {
        return storedTotpCredentials.stream()
                .filter(item -> item.getUserLabel().equals(anObject))
                .findFirst();
    }

    private String expectedTotpSecretData(LegacyTotp legacyTotp) {
        return "{\"value\":\"" + legacyTotp.secret() + "\"}";
    }

    private List<CredentialModel> getStoredTotpCredentials(UserModel result) {
        return result.credentialManager()
                .getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE)
                .toList();
    }

    @Test
    void isDuplicateUserIdShouldReturnFalseGivenNotMigratingUserId() {
        final LegacyUser legacyUser = aMinimalLegacyUser();
        userModelFactory = constructUserModelFactory();

        var result = userModelFactory.isDuplicateUserId(legacyUser, realm);

        assertThat(result).isFalse();
    }

    @Test
    void isDuplicateUserIdShouldReturnTrueGivenMigratingIdAndItAlreadyExists() {
        final LegacyUser legacyUser = aLegacyUserWithId();
        mockUserModelWithSameIdAlreadyExists(legacyUser);
        userModelFactory = constructUserModelFactory();

        var result = userModelFactory.isDuplicateUserId(legacyUser, realm);

        assertThat(result).isTrue();
    }

    private void mockUserModelWithSameIdAlreadyExists(LegacyUser legacyUser) {
        when(userProvider.getUserById(realm, legacyUser.id()))
                .thenReturn(matchingUserModel(legacyUser));
    }

    @Test
    void isDuplicateUserIdShouldReturnFalseGivenMigratingIdAndItDoesntExist() {
        final LegacyUser legacyUser = aLegacyUserWithId();
        mockNoExistingUserModelWithSameId(legacyUser);
        userModelFactory = constructUserModelFactory();

        var result = userModelFactory.isDuplicateUserId(legacyUser, realm);

        assertThat(result).isFalse();
    }

    private void mockNoExistingUserModelWithSameId(LegacyUser legacyUser) {
        when(userProvider.getUserById(realm, legacyUser.id()))
                .thenReturn(null);
    }

    @Test
    void shouldCreateUserWithExistingOrganization() {
        final  LegacyUser legacyUser = aLegacyUserWithOneOrg();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        userModelFactory = constructUserModelFactory();

        when(realm.isOrganizationsEnabled()).thenReturn(true);
        OrganizationModel orgMock = mock(OrganizationModel.class);
        when(organizationProvider.getByAlias(anyString())).thenReturn(orgMock);

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result).isNotNull();
        verify(organizationProvider, times(1)).addManagedMember(orgMock, result);
    }

    @Test
    void shouldCreateUserWithNotExistingOrganization() {

        final LegacyUser legacyUser = aLegacyUserWithOneOrg();
        final LegacyOrganization legacyOrganization = legacyUser.organizations().getFirst();
        OrganizationModel orgMock = mock(OrganizationModel.class);

        when(realm.isOrganizationsEnabled()).thenReturn(true);
        when(organizationProvider.getByAlias(legacyOrganization.orgAlias())).thenReturn(null);
        when(organizationProvider.create(legacyOrganization.orgName(), legacyOrganization.orgAlias())).thenReturn(orgMock);

        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);

        userModelFactory = constructUserModelFactory();
        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result).isNotNull();
        verify(organizationProvider, times(1)).create(legacyOrganization.orgName(), legacyOrganization.orgAlias());
        verify(organizationProvider, times(1)).addManagedMember(orgMock, result);
    }
}
