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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Stream;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;
import static com.danielfrak.code.keycloak.providers.rest.remote.TestLegacyUser.*;
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
        lenient().when(model.getConfig())
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

    private UserModel userModelWithRoles(RoleModel... roleModels) {
        UserModel userModel = mock(UserModel.class);
        when(userModel.getRoleMappingsStream())
                .thenReturn(Stream.of(roleModels));
        return userModel;
    }

    private RoleModel asRoleModel(String roleName) {
        RoleModel roleModel = mock(RoleModel.class);
        when(roleModel.getName())
                .thenReturn(roleName);
        return roleModel;
    }

    private RoleModel staleRole() {
        return asRoleModel("staleRole");
    }

    private void existInRealm(RoleModel... roleModels) {
        for (RoleModel roleModel : roleModels) {
            when(realm.getRole(roleModel.getName()))
                    .thenReturn(roleModel);
        }
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

        RoleModel mappedRole = asRoleModel(legacyUser.roles().getFirst());
        RoleModel missingRole = asRoleModel(legacyUser.roles().get(1));
        RoleModel staleRole = staleRole();
        existInRealm(mappedRole, missingRole);
        UserModel userModel = userModelWithRoles(mappedRole, staleRole);

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

        RoleModel mappedRole = asRoleModel(legacyUser.roles().getFirst());
        RoleModel missingRole = asRoleModel(legacyUser.roles().get(1));
        RoleModel staleRole = staleRole();
        existInRealm(mappedRole, missingRole);
        UserModel userModel = userModelWithRoles(mappedRole, staleRole);

        userModelFactory.synchronizeRolesAddOnly(legacyUser, realm, userModel);

        verify(userModel).grantRole(missingRole);
        verify(userModel, never()).deleteRoleMapping(any());
    }

    @Test
    void shouldSynchronizeRolesUsingRoleIdAsIdentityKey() {
        final LegacyUser legacyUser = aLegacyUserWithRoles(List.of("legacy-role"));
        configureMigrationOfUnmappedRoles();
        userModelFactory = constructUserModelFactory();

        RoleModel desiredRole = asRoleModel("legacy-role");
        when(desiredRole.getId())
                .thenReturn("role-id-1");
        existInRealm(desiredRole);

        RoleModel currentRole = mock(RoleModel.class);
        when(currentRole.getId())
                .thenReturn("role-id-1");
        UserModel userModel = userModelWithRoles(currentRole);

        userModelFactory.synchronizeRoles(legacyUser, realm, userModel);

        verify(userModel, never()).grantRole(any());
        verify(userModel, never()).deleteRoleMapping(any());
    }

    @Test
    void shouldSynchronizeRolesUsingNameKeyWhenRoleIdIsBlank() {
        final LegacyUser legacyUser = aLegacyUserWithRoles(List.of("legacy-role"));
        configureMigrationOfUnmappedRoles();
        userModelFactory = constructUserModelFactory();

        RoleModel desiredRole = asRoleModel("legacy-role");
        when(desiredRole.getId())
                .thenReturn(" ");
        existInRealm(desiredRole);

        RoleModel currentRole = asRoleModel("legacy-role");
        when(currentRole.getId())
                .thenReturn("");
        UserModel userModel = userModelWithRoles(currentRole);

        userModelFactory.synchronizeRoles(legacyUser, realm, userModel);

        verify(userModel, never()).grantRole(any());
        verify(userModel, never()).deleteRoleMapping(any());
    }

    @Test
    void shouldHandleDuplicateRoleKeysInReconcileAndAddOnlyPaths() {
        final LegacyUser legacyUser = aLegacyUserWithRoles(List.of("legacy-role-1", "legacy-role-2"));
        configureMigrationOfUnmappedRoles();
        userModelFactory = constructUserModelFactory();

        RoleModel desiredRole1 = mock(RoleModel.class);
        RoleModel desiredRole2 = mock(RoleModel.class);
        when(desiredRole1.getId()).thenReturn("dup-role-id");
        when(desiredRole2.getId()).thenReturn("dup-role-id");
        when(realm.getRole("legacy-role-1")).thenReturn(desiredRole1);
        when(realm.getRole("legacy-role-2")).thenReturn(desiredRole2);

        RoleModel currentRole1 = mock(RoleModel.class);
        RoleModel currentRole2 = mock(RoleModel.class);
        when(currentRole1.getId()).thenReturn("dup-role-id");
        when(currentRole2.getId()).thenReturn("dup-role-id");
        UserModel userModel = mock(UserModel.class);
        when(userModel.getRoleMappingsStream())
                .thenAnswer(invocation -> Stream.of(currentRole1, currentRole2));

        userModelFactory.synchronizeRoles(legacyUser, realm, userModel);
        userModelFactory.synchronizeRolesAddOnly(legacyUser, realm, userModel);

        verify(userModel, never()).grantRole(any());
        verify(userModel, never()).deleteRoleMapping(any());
    }

    @Test
    void shouldRemoveCurrentRoleWithBlankNameWhenAbsentFromLegacy() {
        final LegacyUser legacyUser = aLegacyUserWithRoles(List.of());
        configureMigrationOfUnmappedRoles();
        userModelFactory = constructUserModelFactory();

        RoleModel currentRole = asRoleModel(" ");
        when(currentRole.getId()).thenReturn(null);
        UserModel userModel = userModelWithRoles(currentRole);

        userModelFactory.synchronizeRoles(legacyUser, realm, userModel);

        verify(userModel).deleteRoleMapping(currentRole);
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
        when(realm.createGroup(groupName))
                .thenReturn(newGroupModel);

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

    private UserModel userModelWithGroups(GroupModel... groupModels) {
        UserModel userModel = mock(UserModel.class);
        when(userModel.getGroupsStream())
                .thenReturn(Stream.of(groupModels));
        return userModel;
    }

    private GroupModel asGroupModel(String groupName) {
        GroupModel groupModel = mock(GroupModel.class);
        when(groupModel.getName())
                .thenReturn(groupName);
        return groupModel;
    }

    private GroupModel staleGroup() {
        return asGroupModel("staleGroup");
    }

    private void existingGroupsInRealm(GroupModel... groupModels) {
        when(realm.getGroupsStream())
                .then(i -> Stream.of(groupModels));
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

        GroupModel mappedGroup = asGroupModel(legacyUser.groups().getFirst());
        GroupModel missingGroup = asGroupModel(legacyUser.groups().get(1));
        GroupModel staleGroup = staleGroup();
        existingGroupsInRealm(mappedGroup, missingGroup, staleGroup);
        UserModel userModel = userModelWithGroups(mappedGroup, staleGroup);

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

        GroupModel mappedGroup = asGroupModel(legacyUser.groups().getFirst());
        GroupModel missingGroup = asGroupModel(legacyUser.groups().get(1));
        GroupModel staleGroup = staleGroup();
        existingGroupsInRealm(mappedGroup, missingGroup, staleGroup);
        UserModel userModel = userModelWithGroups(mappedGroup, staleGroup);

        userModelFactory.synchronizeGroupsAddOnly(legacyUser, realm, userModel);

        verify(userModel).joinGroup(missingGroup);
        verify(userModel, never()).leaveGroup(any());
    }

    @Test
    void shouldSynchronizeGroupsUsingGroupIdAsIdentityKey() {
        final LegacyUser legacyUser = aLegacyUserWithGroups(List.of("legacy-group"));
        configureMigrationOfUnmappedGroups();
        userModelFactory = constructUserModelFactory();

        GroupModel desiredGroup = asGroupModel("legacy-group");
        when(desiredGroup.getId())
                .thenReturn("group-id-1");
        existingGroupsInRealm(desiredGroup);

        GroupModel currentGroup = mock(GroupModel.class);
        when(currentGroup.getId())
                .thenReturn("group-id-1");
        UserModel userModel = userModelWithGroups(currentGroup);

        userModelFactory.synchronizeGroups(legacyUser, realm, userModel);

        verify(userModel, never()).joinGroup(any());
        verify(userModel, never()).leaveGroup(any());
    }

    @Test
    void shouldSynchronizeGroupsUsingNameKeyWhenGroupIdIsBlank() {
        final LegacyUser legacyUser = aLegacyUserWithGroups(List.of("legacy-group"));
        configureMigrationOfUnmappedGroups();
        userModelFactory = constructUserModelFactory();

        GroupModel desiredGroup = asGroupModel("legacy-group");
        when(desiredGroup.getId())
                .thenReturn(" ");
        existingGroupsInRealm(desiredGroup);

        GroupModel currentGroup = asGroupModel("legacy-group");
        when(currentGroup.getId())
                .thenReturn("");
        UserModel userModel = userModelWithGroups(currentGroup);

        userModelFactory.synchronizeGroups(legacyUser, realm, userModel);

        verify(userModel, never()).joinGroup(any());
        verify(userModel, never()).leaveGroup(any());
    }

    @Test
    void shouldHandleDuplicateGroupKeysInAddOnlyPath() {
        final LegacyUser legacyUser = aLegacyUserWithGroups(List.of("legacy-group-1", "legacy-group-2"));
        configureMigrationOfUnmappedGroups();
        userModelFactory = constructUserModelFactory();

        GroupModel desiredGroup1 = mock(GroupModel.class);
        GroupModel desiredGroup2 = mock(GroupModel.class);
        when(desiredGroup1.getId()).thenReturn("dup-group-id");
        when(desiredGroup2.getId()).thenReturn("dup-group-id");
        when(desiredGroup1.getName()).thenReturn("legacy-group-1");
        when(desiredGroup2.getName()).thenReturn("legacy-group-2");
        existingGroupsInRealm(desiredGroup1, desiredGroup2);

        GroupModel currentGroup1 = mock(GroupModel.class);
        GroupModel currentGroup2 = mock(GroupModel.class);
        when(currentGroup1.getId()).thenReturn("dup-group-id");
        when(currentGroup2.getId()).thenReturn("dup-group-id");
        UserModel userModel = userModelWithGroups(currentGroup1, currentGroup2);

        userModelFactory.synchronizeGroupsAddOnly(legacyUser, realm, userModel);

        verify(userModel, never()).joinGroup(any());
    }

    @Test
    void shouldHandleConfiguredIgnoredRolePatternsContainingNullAndBlankEntries() {
        final LegacyUser legacyUser = aLegacyUserWithTwoRoles();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        configureMigrationOfUnmappedRoles();
        config.put(IGNORED_SYNC_ROLES_PROPERTY, java.util.Arrays.asList(null, "  ", "old*"));
        RoleModel ignoredRole = asRoleModel(legacyUser.roles().getFirst());
        RoleModel importedRole = asRoleModel(legacyUser.roles().get(1));
        existInRealm(ignoredRole, importedRole);
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result.getRoleMappingsStream().toList())
                .containsExactly(importedRole);
    }

    @Test
    void shouldRemoveCurrentGroupWithNullNameWhenAbsentFromLegacy() {
        final LegacyUser legacyUser = aLegacyUserWithGroups(List.of());
        configureMigrationOfUnmappedGroups();
        userModelFactory = constructUserModelFactory();

        GroupModel currentGroup = mock(GroupModel.class);
        when(currentGroup.getId())
                .thenReturn(null);
        when(currentGroup.getName())
                .thenReturn(null);
        UserModel userModel = userModelWithGroups(currentGroup);

        userModelFactory.synchronizeGroups(legacyUser, realm, userModel);

        verify(userModel).leaveGroup(currentGroup);
    }

    @Test
    void shouldRemoveCurrentGroupWithBlankNameWhenAbsentFromLegacy() {
        final LegacyUser legacyUser = aLegacyUserWithGroups(List.of());
        configureMigrationOfUnmappedGroups();
        userModelFactory = constructUserModelFactory();

        GroupModel currentGroup = asGroupModel(" ");
        when(currentGroup.getId()).thenReturn(null);
        UserModel userModel = userModelWithGroups(currentGroup);

        userModelFactory.synchronizeGroups(legacyUser, realm, userModel);

        verify(userModel).leaveGroup(currentGroup);
    }

    @Test
    void shouldUseDefaultIgnoredRolesAndEmptyIgnoredGroupsWhenConfiguredListsAreNull() {
        ComponentModel modelWithNullIgnoredLists = mock(ComponentModel.class);
        MultivaluedHashMap<String, String> configWithNullIgnoredLists = mock(MultivaluedHashMap.class);
        when(modelWithNullIgnoredLists.getConfig()).thenReturn(configWithNullIgnoredLists);
        when(configWithNullIgnoredLists.getList(ROLE_MAP_PROPERTY)).thenReturn(List.of());
        when(configWithNullIgnoredLists.getList(GROUP_MAP_PROPERTY)).thenReturn(List.of());
        when(configWithNullIgnoredLists.getList(IGNORED_SYNC_ROLES_PROPERTY)).thenReturn(null);
        when(configWithNullIgnoredLists.getList(IGNORED_SYNC_GROUPS_PROPERTY)).thenReturn(null);
        when(configWithNullIgnoredLists.getFirst(MIGRATE_UNMAPPED_ROLES_PROPERTY)).thenReturn("true");
        when(configWithNullIgnoredLists.getFirst(MIGRATE_UNMAPPED_GROUPS_PROPERTY)).thenReturn("true");
        when(configWithNullIgnoredLists.getFirst(UPDATE_USER_ROLES_ON_LOGIN)).thenReturn("SYNC_FIRST_LOGIN");
        when(configWithNullIgnoredLists.getFirst(UPDATE_USER_GROUPS_ON_LOGIN)).thenReturn("SYNC_FIRST_LOGIN");
        when(modelWithNullIgnoredLists.getId()).thenReturn(MODEL_ID);
        UserModelFactory factory = new UserModelFactory(session, modelWithNullIgnoredLists);

        LegacyUser legacyUser = new LegacyUser(
                null,
                "someUserName",
                "user@email.com",
                "John",
                "Smith",
                true,
                true,
                Map.of(),
                List.of("offline_access"),
                List.of("manual-group"),
                List.of(),
                List.of(),
                List.of()
        );
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        GroupModel manualGroup = asGroupModel("manual-group");
        existingGroupsInRealm(manualGroup);

        UserModel result = factory.create(legacyUser, realm);

        assertThat(result.getRoleMappingsStream().toList()).isEmpty();
        assertThat(result.getGroupsStream().toList()).containsExactly(manualGroup);
    }

    @Test
    void shouldSkipOrganizationMigrationWhenFeatureEnabledButLegacyOrganizationsAreEmpty() {
        final LegacyUser legacyUser = aLegacyUserWithId();
        mockSuccessfulUserModelCreationWithIdMigration(legacyUser);
        userModelFactory = constructUserModelFactory();
        when(realm.isOrganizationsEnabled()).thenReturn(true);
        OrganizationProvider provider = mock(OrganizationProvider.class);
        when(session.getProvider(OrganizationProvider.class)).thenReturn(provider);

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result).isNotNull();
        verifyNoInteractions(provider);
    }

    @Test
    void shouldSkipOrganizationMigrationWhenFeatureEnabledButLegacyOrganizationsAreNull() {
        final LegacyUser legacyUser = aMinimalLegacyUser();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        userModelFactory = constructUserModelFactory();
        when(realm.isOrganizationsEnabled()).thenReturn(true);
        OrganizationProvider provider = mock(OrganizationProvider.class);
        when(session.getProvider(OrganizationProvider.class)).thenReturn(provider);

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result).isNotNull();
        verifyNoInteractions(provider);
    }

    @Test
    void shouldDoOrganizationMigrationWhenWhenAvailable() {
        final  LegacyUser legacyUser = aMinimalLegacyUser();
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

        RoleModel mappedRole = asRoleModel(legacyUser.roles().getFirst());
        RoleModel missingRole = asRoleModel(legacyUser.roles().get(1));
        RoleModel untrackedRole = asRoleModel("manage-account");
        existInRealm(mappedRole, missingRole);
        UserModel userModel = userModelWithRoles(mappedRole, untrackedRole);

        userModelFactory.synchronizeRoles(legacyUser, realm, userModel);

        verify(userModel, never()).deleteRoleMapping(untrackedRole);
    }

    @Test
    void shouldTreatRegexMetaCharactersAsLiteralsInIgnoredRolePatterns() {
        final LegacyUser legacyUser = aLegacyUserWithTwoRoles();
        configureMigrationOfUnmappedRoles();
        config.put(IGNORED_SYNC_ROLES_PROPERTY, List.of("team.(ops)-*"));
        userModelFactory = constructUserModelFactory();

        RoleModel mappedRole = asRoleModel(legacyUser.roles().getFirst());
        RoleModel missingRole = asRoleModel(legacyUser.roles().get(1));
        RoleModel untrackedRole = asRoleModel("team.(ops)-admin");
        existInRealm(mappedRole, missingRole);
        UserModel userModel = userModelWithRoles(mappedRole, untrackedRole);

        userModelFactory.synchronizeRoles(legacyUser, realm, userModel);

        verify(userModel, never()).deleteRoleMapping(untrackedRole);
    }

    @Test
    void shouldNotImportIgnoredRolesOnFirstLogin() {
        final LegacyUser legacyUser = aLegacyUserWithTwoRoles();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        configureMigrationOfUnmappedRoles();
        config.put(IGNORED_SYNC_ROLES_PROPERTY, List.of("another*"));
        RoleModel importedRole = asRoleModel(legacyUser.roles().getFirst());
        RoleModel ignoredRole = asRoleModel(legacyUser.roles().get(1));
        existInRealm(importedRole, ignoredRole);
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result.getRoleMappingsStream().toList())
                .containsExactly(importedRole);
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

        GroupModel mappedGroup = asGroupModel(legacyUser.groups().getFirst());
        GroupModel missingGroup = asGroupModel(legacyUser.groups().get(1));
        GroupModel untrackedGroup = asGroupModel("manual-group");
        existingGroupsInRealm(mappedGroup, missingGroup, untrackedGroup);
        UserModel userModel = userModelWithGroups(mappedGroup, untrackedGroup);

        userModelFactory.synchronizeGroups(legacyUser, realm, userModel);

        verify(userModel, never()).leaveGroup(untrackedGroup);
    }

    @Test
    void shouldNotImportIgnoredGroupsOnFirstLogin() {
        final LegacyUser legacyUser = aLegacyUserWithTwoGroups();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        configureMigrationOfUnmappedGroups();
        config.put(IGNORED_SYNC_GROUPS_PROPERTY, List.of("another*"));

        GroupModel importedGroup = asGroupModel(legacyUser.groups().getFirst());
        GroupModel ignoredGroup = asGroupModel(legacyUser.groups().get(1));
        existingGroupsInRealm(importedGroup, ignoredGroup);
        userModelFactory = constructUserModelFactory();

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result.getGroupsStream().toList())
                .containsExactly(importedGroup);
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

    private LegacyUser aLegacyUserWithRoles(List<String> roles) {
        return new LegacyUser(
                null,
                "someUserName",
                "user@email.com",
                "John",
                "Smith",
                true,
                true,
                Map.of(),
                roles,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    private LegacyUser aLegacyUserWithGroups(List<String> groups) {
        return new LegacyUser(
                null,
                "someUserName",
                "user@email.com",
                "John",
                "Smith",
                true,
                true,
                Map.of(),
                List.of(),
                groups,
                List.of(),
                List.of(),
                List.of()
        );
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
        final LegacyUser legacyUser = aLegacyUserWithOneOrg();
        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);
        userModelFactory = constructUserModelFactory();

        when(realm.isOrganizationsEnabled())
                .thenReturn(true);
        OrganizationModel orgMock = mock(OrganizationModel.class);
        when(organizationProvider.getByAlias(anyString()))
                .thenReturn(orgMock);

        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result).isNotNull();
        verify(organizationProvider, times(1)).addManagedMember(orgMock, result);
    }

    @Test
    void shouldCreateUserWithNotExistingOrganization() {

        final LegacyUser legacyUser = aLegacyUserWithOneOrg();
        final LegacyOrganization legacyOrganization = legacyUser.organizations().getFirst();
        OrganizationModel orgMock = mock(OrganizationModel.class);

        when(realm.isOrganizationsEnabled())
                .thenReturn(true);
        when(organizationProvider.getByAlias(legacyOrganization.orgAlias()))
                .thenReturn(null);
        when(organizationProvider.create(legacyOrganization.orgName(), legacyOrganization.orgAlias()))
                .thenReturn(orgMock);

        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);

        userModelFactory = constructUserModelFactory();
        UserModel result = userModelFactory.create(legacyUser, realm);

        ArgumentCaptor<Set<OrganizationDomainModel>> captor = ArgumentCaptor.forClass(Set.class);

        assertThat(result).isNotNull();
        verify(organizationProvider, times(1)).create(legacyOrganization.orgName(), legacyOrganization.orgAlias());
        verify(organizationProvider, times(1)).addManagedMember(orgMock, result);
        verify(orgMock).setDomains(captor.capture());
        Set<OrganizationDomainModel> organizationDomainModelSet = captor.getValue();
        assertThat(organizationDomainModelSet.stream().findFirst().get().getName())
                .isEqualTo(legacyUser.organizations().get(0).domains().get(0).domainName());
    }

    @Test
    void shouldCreateUserWithNotExistingOrganizationButWithoutDomain() {

        final LegacyUser legacyUser = aLegacyUserWithOneOrgButWithoutDomains();
        final LegacyOrganization legacyOrganization = legacyUser.organizations().getFirst();
        OrganizationModel orgMock = mock(OrganizationModel.class);

        when(realm.isOrganizationsEnabled())
                .thenReturn(true);
        when(organizationProvider.getByAlias(legacyOrganization.orgAlias()))
                .thenReturn(null);
        when(organizationProvider.create(legacyOrganization.orgName(), legacyOrganization.orgAlias()))
                .thenReturn(orgMock);

        mockSuccessfulUserModelCreationWithoutIdMigration(legacyUser);

        userModelFactory = constructUserModelFactory();
        UserModel result = userModelFactory.create(legacyUser, realm);

        assertThat(result).isNotNull();
        verify(organizationProvider, times(1)).create(legacyOrganization.orgName(), legacyOrganization.orgAlias());
        verify(organizationProvider, times(1)).addManagedMember(orgMock, result);
        verify(orgMock, never()).setDomains(anySet());
    }
}
