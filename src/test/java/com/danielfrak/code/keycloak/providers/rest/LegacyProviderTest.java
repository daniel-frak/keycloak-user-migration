package com.danielfrak.code.keycloak.providers.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUserService;
import com.danielfrak.code.keycloak.providers.rest.remote.UserModelFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.*;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.policy.PasswordPolicyManagerProvider;
import org.keycloak.policy.PolicyError;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.SEVER_FEDERATION_LINK;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.UPDATE_USER_GROUPS_ON_LOGIN;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.UPDATE_USER_ON_LOGIN;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.UPDATE_USER_ROLES_ON_LOGIN;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.USE_USER_ID_FOR_CREDENTIAL_VERIFICATION;
import static com.danielfrak.code.keycloak.providers.rest.remote.TestLegacyUser.aLegacyUserWithId;
import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegacyProviderTest {

    private LegacyProvider legacyProvider;

    @Mock
    private KeycloakSession session;

    @Mock
    private LegacyUserService legacyUserService;

    @Mock
    private UserModelFactory userModelFactory;

    @Mock
    private RealmModel realmModel;

    @Mock
    private UserModel userModel;

    @Mock
    private UserProvider userProvider;

    @Mock
    private ComponentModel model;

    @Mock
    private PasswordPolicyManagerProvider passwordPolicyManagerProvider;

    @BeforeEach
    void setUp() {
        legacyProvider = new LegacyProvider(session, legacyUserService, userModelFactory, model);

        lenient().when(session.getProvider(PasswordPolicyManagerProvider.class))
                .thenReturn(passwordPolicyManagerProvider);
        lenient().when(session.users())
                .thenReturn(userProvider);
        lenient().when(legacyUserService.findByUsername(anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void shouldGetUserByUsername() {
        final String username = "user";
        final LegacyUser user = aLegacyUserWithId();
        when(legacyUserService.findByUsername(username))
                .thenReturn(Optional.of(user));
        when(userModelFactory.create(user, realmModel))
                .thenReturn(userModel);

        var result = legacyProvider.getUserByUsername(realmModel, username);

        assertEquals(userModel, result);
    }

    @Test
    void shouldReturnNullIfUserNotFoundByUsername() {
        final String username = "user";
        when(legacyUserService.findByUsername(username))
                .thenReturn(Optional.empty());

        var result = legacyProvider.getUserByUsername(realmModel, username);

        assertNull(result);
    }

    @Test
    void shouldGetUserByEmail() {
        final String email = "email";
        final LegacyUser user = aLegacyUserWithId();
        when(legacyUserService.findByEmail(email))
                .thenReturn(Optional.of(user));
        when(userModelFactory.create(user, realmModel))
                .thenReturn(userModel);

        var result = legacyProvider.getUserByEmail(realmModel, email);

        assertEquals(userModel, result);
    }

    @Test
    void shouldUpdateExistingUserDataWhenFoundByUsername() {
        final String username = "user";
        final LegacyUser user = aLegacyUserWithId();
        when(legacyUserService.findByUsername(username))
                .thenReturn(Optional.of(user));
        when(userProvider.getUserByUsername(realmModel, user.username()))
                .thenReturn(userModel);

        var result = legacyProvider.getUserByUsername(realmModel, username);

        assertEquals(userModel, result);
        verify(userModel).setEmail(user.email());
        verify(userModel).setFirstName(user.firstName());
        verify(userModel).setLastName(user.lastName());
        verify(userModel, never()).setFederationLink(any());
        verify(userModelFactory, never()).create(any(), any());
    }

    @Test
    void shouldReturnNullIfUserIdExistsButHasDifferentUsername() {
        final String email = "email";
        final LegacyUser user = aLegacyUserWithId();
        when(legacyUserService.findByEmail(email))
                .thenReturn(Optional.of(user));
        when(userProvider.getUserByUsername(realmModel, user.username()))
                .thenReturn(null);
        var existingUser = mock(UserModel.class);
        when(existingUser.getUsername())
                .thenReturn("different");
        when(userProvider.getUserById(realmModel, user.id()))
                .thenReturn(existingUser);
        when(userModelFactory.isDuplicateUserId(user, realmModel))
                .thenReturn(true);

        var result = legacyProvider.getUserByEmail(realmModel, email);

        assertNull(result);
        verify(userModelFactory, never()).create(any(), any());
    }

    @Test
    void shouldSkipLegacyLookupWhenExistingLookupInProgress() {
        final String username = "user";
        when(session.getAttribute(anyString(), eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);

        var result = legacyProvider.getUserByUsername(realmModel, username);

        assertNull(result);
        verifyNoInteractions(legacyUserService);
    }

    @Test
    void shouldReturnNullIfUserNotFoundByEmail() {
        final String username = "user";
        when(legacyUserService.findByEmail(username))
                .thenReturn(Optional.empty());

        var result = legacyProvider.getUserByEmail(realmModel, username);

        assertNull(result);
    }

    @Test
    void isValidShouldReturnFalseOnWrongCredentialType() {
        var input = mock(CredentialInput.class);
        when(input.getType())
                .thenReturn(CredentialModel.KERBEROS);

        var result = legacyProvider.isValid(realmModel, userModel, input);

        assertFalse(result);
    }

    @Test
    void isValidShouldReturnFalseWhenInvalidCredentialValue() {
        var input = mock(CredentialInput.class);
        when(input.getType())
                .thenReturn(PasswordCredentialModel.TYPE);

        MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
        config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("false"));
        config.put(UPDATE_USER_ON_LOGIN, List.of("true"));
        when(model.getConfig()).thenReturn(config);

        final String username = "user";
        final String password = "password";

        when(userModel.getUsername())
                .thenReturn(username);
        when(input.getChallengeResponse())
                .thenReturn(password);
        when(legacyUserService.isPasswordValid(username, password))
                .thenReturn(false);

        var result = legacyProvider.isValid(realmModel, userModel, input);

        assertFalse(result);
    }

    @Test
    void isValidShouldReturnTrueAndUpdateCredentialsWhenUserValidated() {
        var userCredentialManager = mock(SubjectCredentialManager.class);
        var input = mock(CredentialInput.class);
        when(input.getType())
                .thenReturn(PasswordCredentialModel.TYPE);

        MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
        config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("false"));
        config.put(UPDATE_USER_ON_LOGIN, List.of("true"));
        when(model.getConfig()).thenReturn(config);

        final String username = "user";
        final String password = "password";

        when(userModel.getUsername())
                .thenReturn(username);
        when(input.getChallengeResponse())
                .thenReturn(password);
        when(legacyUserService.isPasswordValid(username, password))
                .thenReturn(true);

        when(userModel.credentialManager())
                .thenReturn(userCredentialManager);

        var result = legacyProvider.isValid(realmModel, userModel, input);

        assertTrue(result);
        verify(userCredentialManager).updateCredential(input);
    }

    @Test
    void isValidShouldRefreshUserAttributesWhenLegacyUserExists() {
        var userCredentialManager = mock(SubjectCredentialManager.class);
        var input = mock(CredentialInput.class);
        when(input.getType())
                .thenReturn(PasswordCredentialModel.TYPE);

        MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
        config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("false"));
        config.put(UPDATE_USER_ON_LOGIN, List.of("true"));
        when(model.getConfig()).thenReturn(config);

        final LegacyUser legacyUser = aLegacyUserWithId();
        when(userModel.getUsername())
                .thenReturn(legacyUser.username());
        when(userModel.credentialManager())
                .thenReturn(userCredentialManager);
        when(input.getChallengeResponse())
                .thenReturn("password");
        when(legacyUserService.isPasswordValid(legacyUser.username(), "password"))
                .thenReturn(true);
        when(legacyUserService.findByUsername(legacyUser.username()))
                .thenReturn(Optional.of(legacyUser));

        var result = legacyProvider.isValid(realmModel, userModel, input);

        assertTrue(result);
        verify(userModel).setEmail(legacyUser.email());
        verify(userModel).setFirstName(legacyUser.firstName());
    }

    @Test
    void shouldNotRefreshUserAttributesWhenRefreshDisabled() {
        var userCredentialManager = mock(SubjectCredentialManager.class);
        var input = mock(CredentialInput.class);
        when(input.getType())
                .thenReturn(PasswordCredentialModel.TYPE);

        MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
        config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("false"));
        config.put(UPDATE_USER_ON_LOGIN, List.of("false"));
        when(model.getConfig()).thenReturn(config);

        final LegacyUser legacyUser = aLegacyUserWithId();
        when(userModel.getUsername())
                .thenReturn(legacyUser.username());
        when(userModel.credentialManager())
                .thenReturn(userCredentialManager);
        when(input.getChallengeResponse())
                .thenReturn("password");
        when(legacyUserService.isPasswordValid(legacyUser.username(), "password"))
                .thenReturn(true);

        var result = legacyProvider.isValid(realmModel, userModel, input);

        assertTrue(result);
        verify(userModel, never()).setEmail(any());
        verify(userModel, never()).setFirstName(any());
        verify(legacyUserService, never()).findByUsername(anyString());
    }

    @Test
    void isValidShouldRefreshUserGroupsWhenEnabled() {
        var userCredentialManager = mock(SubjectCredentialManager.class);
        var input = mock(CredentialInput.class);
        when(input.getType()).thenReturn(PasswordCredentialModel.TYPE);

        MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
        config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("false"));
        config.put(UPDATE_USER_ON_LOGIN, List.of("false"));
        config.put(UPDATE_USER_GROUPS_ON_LOGIN, List.of("SYNC_EVERY_LOGIN"));
        when(model.getConfig()).thenReturn(config);

        final LegacyUser legacyUser = aLegacyUserWithId();
        when(userModel.getUsername()).thenReturn(legacyUser.username());
        when(userModel.credentialManager()).thenReturn(userCredentialManager);
        when(input.getChallengeResponse()).thenReturn("password");
        when(legacyUserService.isPasswordValid(legacyUser.username(), "password")).thenReturn(true);
        when(legacyUserService.findByUsername(legacyUser.username())).thenReturn(Optional.of(legacyUser));

        var result = legacyProvider.isValid(realmModel, userModel, input);

        assertTrue(result);
        verify(userModelFactory).synchronizeGroups(legacyUser, realmModel, userModel);
    }

    @Test
    void isValidShouldRefreshUserRolesWhenEnabled() {
        var userCredentialManager = mock(SubjectCredentialManager.class);
        var input = mock(CredentialInput.class);
        when(input.getType()).thenReturn(PasswordCredentialModel.TYPE);

        MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
        config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("false"));
        config.put(UPDATE_USER_ON_LOGIN, List.of("false"));
        config.put(UPDATE_USER_ROLES_ON_LOGIN, List.of("SYNC_EVERY_LOGIN"));
        when(model.getConfig()).thenReturn(config);

        final LegacyUser legacyUser = aLegacyUserWithId();
        when(userModel.getUsername()).thenReturn(legacyUser.username());
        when(userModel.credentialManager()).thenReturn(userCredentialManager);
        when(input.getChallengeResponse()).thenReturn("password");
        when(legacyUserService.isPasswordValid(legacyUser.username(), "password")).thenReturn(true);
        when(legacyUserService.findByUsername(legacyUser.username())).thenReturn(Optional.of(legacyUser));

        var result = legacyProvider.isValid(realmModel, userModel, input);

        assertTrue(result);
        verify(userModelFactory).synchronizeRoles(legacyUser, realmModel, userModel);
    }

    @Test
    void isValidShouldRefreshUserGroupsOnlyAddWhenEnabled() {
        var userCredentialManager = mock(SubjectCredentialManager.class);
        var input = mock(CredentialInput.class);
        when(input.getType()).thenReturn(PasswordCredentialModel.TYPE);

        MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
        config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("false"));
        config.put(UPDATE_USER_ON_LOGIN, List.of("false"));
        config.put(UPDATE_USER_GROUPS_ON_LOGIN, List.of("SYNC_EVERY_LOGIN_ONLY_ADD"));
        when(model.getConfig()).thenReturn(config);

        final LegacyUser legacyUser = aLegacyUserWithId();
        when(userModel.getUsername()).thenReturn(legacyUser.username());
        when(userModel.credentialManager()).thenReturn(userCredentialManager);
        when(input.getChallengeResponse()).thenReturn("password");
        when(legacyUserService.isPasswordValid(legacyUser.username(), "password")).thenReturn(true);
        when(legacyUserService.findByUsername(legacyUser.username())).thenReturn(Optional.of(legacyUser));

        var result = legacyProvider.isValid(realmModel, userModel, input);

        assertTrue(result);
        verify(userModelFactory).synchronizeGroupsAddOnly(legacyUser, realmModel, userModel);
        verify(userModelFactory, never()).synchronizeGroups(legacyUser, realmModel, userModel);
    }

    @Test
    void isValidShouldRefreshUserRolesOnlyAddWhenEnabled() {
        var userCredentialManager = mock(SubjectCredentialManager.class);
        var input = mock(CredentialInput.class);
        when(input.getType()).thenReturn(PasswordCredentialModel.TYPE);

        MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
        config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("false"));
        config.put(UPDATE_USER_ON_LOGIN, List.of("false"));
        config.put(UPDATE_USER_ROLES_ON_LOGIN, List.of("SYNC_EVERY_LOGIN_ONLY_ADD"));
        when(model.getConfig()).thenReturn(config);

        final LegacyUser legacyUser = aLegacyUserWithId();
        when(userModel.getUsername()).thenReturn(legacyUser.username());
        when(userModel.credentialManager()).thenReturn(userCredentialManager);
        when(input.getChallengeResponse()).thenReturn("password");
        when(legacyUserService.isPasswordValid(legacyUser.username(), "password")).thenReturn(true);
        when(legacyUserService.findByUsername(legacyUser.username())).thenReturn(Optional.of(legacyUser));

        var result = legacyProvider.isValid(realmModel, userModel, input);

        assertTrue(result);
        verify(userModelFactory).synchronizeRolesAddOnly(legacyUser, realmModel, userModel);
        verify(userModelFactory, never()).synchronizeRoles(legacyUser, realmModel, userModel);
    }

    @Test
    void isValidShouldReturnTrueAndUpdateCredentialsWhenUserValidatedWithUserId() {
        var subjectCredentialManager = mock(SubjectCredentialManager.class);
        var input = mock(CredentialInput.class);
        when(input.getType())
                .thenReturn(PasswordCredentialModel.TYPE);

        MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
        config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("true"));
        when(model.getConfig()).thenReturn(config);

        final String userId = "1234567890";
        final String password = "password";

        when(userModel.getId())
                .thenReturn(userId);
        when(input.getChallengeResponse())
                .thenReturn(password);
        when(legacyUserService.isPasswordValid(userId, password))
                .thenReturn(true);


        when(userModel.credentialManager())
                .thenReturn(subjectCredentialManager);


        var result = legacyProvider.isValid(realmModel, userModel, input);

        assertTrue(result);
        verify(subjectCredentialManager).updateCredential(input);
    }

    @Test
    void isValidShouldReturnTrueAndNotUpdateCredentialsAndRequirePasswordChangeWhenUserValidatedButPasswordBreaksPolicy() {
        var input = mock(CredentialInput.class);
        when(input.getType())
                .thenReturn(PasswordCredentialModel.TYPE);

        MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
        config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("true"));
        when(model.getConfig()).thenReturn(config);

        final String userId = "1234567890";
        final String password = "password";

        when(userModel.getId())
                .thenReturn(userId);
        when(input.getChallengeResponse())
                .thenReturn(password);
        when(legacyUserService.isPasswordValid(userId, password))
                .thenReturn(true);
        when(passwordPolicyManagerProvider.validate(realmModel, userModel, password))
                .thenReturn(new PolicyError("someError"));

        var result = legacyProvider.isValid(realmModel, userModel, input);

        assertTrue(result);
        verify(userModel, never()).credentialManager();
        verify(userModel).addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
    }

    @Test
    void isValidShouldNotAddRequirePasswordActionWhenOneAlreadyExists() {
        var input = mock(CredentialInput.class);
        when(input.getType())
                .thenReturn(PasswordCredentialModel.TYPE);

        MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
        config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("true"));
        when(model.getConfig()).thenReturn(config);

        final String userId = "1234567890";
        final String password = "password";

        when(userModel.getId())
                .thenReturn(userId);
        when(input.getChallengeResponse())
                .thenReturn(password);
        when(legacyUserService.isPasswordValid(userId, password))
                .thenReturn(true);
        when(passwordPolicyManagerProvider.validate(realmModel, userModel, password))
                .thenReturn(new PolicyError("someError"));
        when(userModel.getRequiredActionsStream())
                .thenReturn(Stream.of(UserModel.RequiredAction.UPDATE_PASSWORD.name()));

        legacyProvider.isValid(realmModel, userModel, input);

        verify(userModel, never()).addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
    }

    @Test
    void shouldSupportPasswordCredentialType() {
        assertTrue(legacyProvider.supportsCredentialType(PasswordCredentialModel.TYPE));
    }

    @Test
    void isConfiguredForShouldAlwaysReturnFalse() {
        assertFalse(legacyProvider.isConfiguredFor(mock(RealmModel.class), mock(UserModel.class),
                "someString"));
    }

    @Test
    void getUserByIdShouldThrowException() {
        var realm = mock(RealmModel.class);
        assertThrows(UnsupportedOperationException.class, () -> legacyProvider.getUserById(realm, "someId"));
    }

    @Test
    void updateCredentialShouldSeverFederationLinkWhenEnabled() {
        var input = mock(CredentialInput.class);
        MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
        config.put(SEVER_FEDERATION_LINK, List.of("true"));
        when(model.getConfig()).thenReturn(config);
        when(userModel.getFederationLink())
                .thenReturn("someId");

        assertFalse(legacyProvider.updateCredential(realmModel, userModel, input));

        verify(userModel).setFederationLink(null);
    }

    @Test
    void updateCredentialShouldNotSeverFederationLinkWhenDisabled() {
        var input = mock(CredentialInput.class);
        MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
        config.put(SEVER_FEDERATION_LINK, List.of("false"));
        when(model.getConfig()).thenReturn(config);

        assertFalse(legacyProvider.updateCredential(realmModel, userModel, input));

        verify(userModel, never()).setFederationLink(null);
    }

    @Test
    void disableCredentialTypeShouldDoNothing() {
        legacyProvider.disableCredentialType(realmModel, userModel, "someType");
        Mockito.verifyNoInteractions(session, legacyUserService, userModelFactory, realmModel);
    }

    @Test
    void closeShouldDoNothing() {
        legacyProvider.close();
        Mockito.verifyNoInteractions(session, legacyUserService, userModelFactory, realmModel, userModel);
    }

    @Test
    void getDisableableCredentialTypesShouldAlwaysReturnEmptySet() {
        assertEquals(emptySet(),
                legacyProvider.getDisableableCredentialTypesStream(realmModel, userModel).collect(Collectors.toSet()));
    }

    @Test
    void addUserShouldReturnNull() {
        UserModel result = legacyProvider.addUser(realmModel, "someUser");
        assertNull(result);
    }

    @Test
    void removeUserShouldReturnTrue() {
        var result = legacyProvider.removeUser(realmModel, userModel);
        assertTrue(result);
    }
}
