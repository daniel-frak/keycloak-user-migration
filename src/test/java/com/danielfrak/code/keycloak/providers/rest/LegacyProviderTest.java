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
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.models.UserModel;
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

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.USE_USER_ID_FOR_CREDENTIAL_VERIFICATION;
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
    private ComponentModel model;

    @Mock
    private PasswordPolicyManagerProvider passwordPolicyManagerProvider;

    @BeforeEach
    void setUp() {
        legacyProvider = new LegacyProvider(session, legacyUserService, userModelFactory, model);

        lenient().when(session.getProvider(PasswordPolicyManagerProvider.class))
                .thenReturn(passwordPolicyManagerProvider);
    }

    @Test
    void shouldGetUserByUsername() {
        final String username = "user";
        final LegacyUser user = new LegacyUser();
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
        final LegacyUser user = new LegacyUser();
        when(legacyUserService.findByEmail(email))
                .thenReturn(Optional.of(user));
        when(userModelFactory.create(user, realmModel))
                .thenReturn(userModel);

        var result = legacyProvider.getUserByEmail(realmModel, email);

        assertEquals(userModel, result);
    }

    @Test
    void shouldReturnNullIfUserWithDuplicateIdExists() {
        final String email = "email";
        final LegacyUser user = new LegacyUser();
        when(legacyUserService.findByEmail(email))
                .thenReturn(Optional.of(user));
        when(userModelFactory.isDuplicateUserId(user, realmModel))
                .thenReturn(true);

        var result = legacyProvider.getUserByEmail(realmModel, email);

        assertNull(result);
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
    void shouldRemoveFederationLinkWhenCredentialUpdates() {
        var input = mock(CredentialInput.class);
        when(userModel.getFederationLink())
                .thenReturn("someId");

        assertFalse(legacyProvider.updateCredential(realmModel, userModel, input));

        verify(userModel)
                .setFederationLink(null);
    }

    @Test
    void shouldNotRemoveFederationLinkWhenBlankAndCredentialUpdates() {
        var input = mock(CredentialInput.class);
        when(userModel.getFederationLink())
                .thenReturn(" ");

        assertFalse(legacyProvider.updateCredential(realmModel, userModel, input));

        verify(userModel, never())
                .setFederationLink(null);
    }

    @Test
    void shouldNotRemoveFederationLinkWhenNullAndCredentialUpdates() {
        var input = mock(CredentialInput.class);
        when(userModel.getFederationLink())
                .thenReturn(null);

        assertFalse(legacyProvider.updateCredential(realmModel, userModel, input));

        verify(userModel, never())
                .setFederationLink(null);
    }

    @Test
    void disableCredentialTypeShouldDoNothing() {
        legacyProvider.disableCredentialType(realmModel, userModel, "someType");
        Mockito.verifyNoInteractions(session, legacyUserService, userModelFactory, realmModel, userModel);
    }

    @Test
    void closeShouldDoNothing() {
        legacyProvider.close();
        Mockito.verifyNoInteractions(session, legacyUserService, userModelFactory, realmModel, userModel);
    }

    @Test
    void getDisableableCredentialTypesShouldAlwaysReturnEmptySet() {
        assertEquals(emptySet(), legacyProvider.getDisableableCredentialTypesStream(realmModel, userModel).collect(Collectors.toSet()));
    }
}