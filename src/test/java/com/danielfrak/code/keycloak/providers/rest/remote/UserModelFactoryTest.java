package com.danielfrak.code.keycloak.providers.rest.remote;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserProvider;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserModelFactoryTest {

    private static String MODEL_ID = "modelId";

    private UserModelFactory userModelFactory;

    private MultivaluedHashMap<String, String> config;

    @Mock
    private KeycloakSession session;
    @Mock
    private ComponentModel model;

    @BeforeEach
    void setUp() {
        config = new MultivaluedHashMap<>();
        config.put(ROLE_MAP_PROPERTY, List.of("oldRole:newRole"));

        when(model.getConfig())
                .thenReturn(config);
        userModelFactory = new UserModelFactory(session, model);

        when(model.getId())
                .thenReturn(MODEL_ID);
    }

    @Test
    void createsUser() {
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";

        when(session.userLocalStorage())
                .thenReturn(userProvider);
        when(userProvider.addUser(realm, username))
                .thenReturn(new TestUserModel(username));

        LegacyUser legacyUser = createLegacyUser(username);
        var result = userModelFactory.create(legacyUser, realm);

        assertNotNull(result);
    }

    @Test
    void migratesBasicAttributes() {
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";

        when(session.userLocalStorage())
                .thenReturn(userProvider);
        when(userProvider.addUser(realm, username))
                .thenReturn(new TestUserModel(username));

        LegacyUser legacyUser = createLegacyUser(username);
        var result = userModelFactory.create(legacyUser, realm);

        assertEquals(legacyUser.getUsername(), result.getUsername());
        assertEquals(legacyUser.getEmail(), result.getEmail());
        assertEquals(legacyUser.isEmailVerified(), result.isEmailVerified());
        assertEquals(legacyUser.isEnabled(), result.isEnabled());
        assertEquals(legacyUser.getFirstName(), result.getFirstName());
        assertEquals(legacyUser.getLastName(), result.getLastName());
    }

    @Test
    void migratesLegacyUserId() {
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";
        final String id = "legacy-user-id";
        final LegacyUser legacyUser = createLegacyUser(username, id);
        final TestUserModel testUserModel = new TestUserModel(username, id);

        when(session.userLocalStorage())
                .thenReturn(userProvider);
        when(userProvider.addUser(realm, legacyUser.getId(), username, true, false))
                .thenReturn(testUserModel);

        var result = userModelFactory.create(legacyUser, realm);

        assertEquals(legacyUser.getId(), result.getId());
    }

    private LegacyUser createLegacyUser(String username) {
        return createLegacyUser(username, null);
    }

    private LegacyUser createLegacyUser(String username, String id) {
        var legacyUser = new LegacyUser();
        legacyUser.setId(id);
        legacyUser.setUsername(username);
        legacyUser.setEmail("user@email.com");
        legacyUser.setEmailVerified(true);
        legacyUser.setEnabled(true);
        legacyUser.setFirstName("John");
        legacyUser.setLastName("Smith");
        return legacyUser;
    }

    @Test
    void migratesRolesWithoutUnmapped() {
        config.putSingle(MIGRATE_UNMAPPED_ROLES_PROPERTY, "false");
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";
        final RoleModel newRoleModel = mock(RoleModel.class);

        when(session.userLocalStorage())
                .thenReturn(userProvider);
        when(userProvider.addUser(realm, username))
                .thenReturn(new TestUserModel(username));
        when(realm.getRole("newRole"))
                .thenReturn(newRoleModel);

        LegacyUser legacyUser = createLegacyUser(username);
        legacyUser.setRoles(List.of("oldRole", "anotherRole"));

        var result = userModelFactory.create(legacyUser, realm);

        assertEquals(Set.of(newRoleModel), result.getRoleMappings());
    }

    @Test
    void migratesUnmappedRoles() {
        config.putSingle(MIGRATE_UNMAPPED_ROLES_PROPERTY, "true");
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";
        final RoleModel newRoleModel = mock(RoleModel.class);
        final RoleModel anotherRoleModel = mock(RoleModel.class);

        when(session.userLocalStorage())
                .thenReturn(userProvider);
        when(userProvider.addUser(realm, username))
                .thenReturn(new TestUserModel(username));
        when(realm.getRole("newRole"))
                .thenReturn(newRoleModel);
        when(realm.getRole("anotherRole"))
                .thenReturn(anotherRoleModel);

        LegacyUser legacyUser = createLegacyUser(username);
        legacyUser.setRoles(List.of("oldRole", "anotherRole"));

        var result = userModelFactory.create(legacyUser, realm);

        assertEquals(Set.of(newRoleModel, anotherRoleModel), result.getRoleMappings());
    }

    @Test
    void migratesAttribute() {
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";

        when(session.userLocalStorage())
                .thenReturn(userProvider);
        when(userProvider.addUser(realm, username))
                .thenReturn(new TestUserModel(username));

        LegacyUser legacyUser = createLegacyUser(username);
        legacyUser.setAttributes(Map.of("someAttribute", List.of("someValue")));
        var result = userModelFactory.create(legacyUser, realm);

        assertEquals(legacyUser.getAttributes(), result.getAttributes());
    }

    @Test
    void setsFederationLink() {
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";

        when(session.userLocalStorage())
                .thenReturn(userProvider);
        when(userProvider.addUser(realm, username))
                .thenReturn(new TestUserModel(username));

        LegacyUser legacyUser = createLegacyUser(username);
        var result = userModelFactory.create(legacyUser, realm);

        assertEquals(MODEL_ID, result.getFederationLink());
    }
}