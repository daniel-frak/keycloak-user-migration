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
import org.keycloak.models.UserCredentialManager;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static java.util.Collections.emptySet;
import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @BeforeEach
    void setUp() {
        legacyProvider = new LegacyProvider(session, legacyUserService, userModelFactory, model);
    }

    @Test
    void getsUserByUsername() {
        final String username = "user";
        final LegacyUser user = new LegacyUser();
        when(legacyUserService.findByUsername(username))
                .thenReturn(Optional.of(user));
        when(userModelFactory.create(user, realmModel))
                .thenReturn(userModel);

        var result = legacyProvider.getUserByUsername(username, realmModel);

        assertEquals(userModel, result);
    }

    @Test
    void returnsNullIfUserNotFoundByUsername() {
        final String username = "user";
        when(legacyUserService.findByUsername(username))
                .thenReturn(Optional.empty());

        var result = legacyProvider.getUserByUsername(username, realmModel);

        assertNull(result);
    }

    @Test
    void getsUserByEmail() {
        final String email = "email";
        final LegacyUser user = new LegacyUser();
        when(legacyUserService.findByEmail(email))
                .thenReturn(Optional.of(user));
        when(userModelFactory.create(user, realmModel))
                .thenReturn(userModel);

        var result = legacyProvider.getUserByEmail(email, realmModel);

        assertEquals(userModel, result);
    }

    @Test
    void returnsNullIfUserNotFoundByEmail() {
        final String username = "user";
        when(legacyUserService.findByEmail(username))
                .thenReturn(Optional.empty());

        var result = legacyProvider.getUserByEmail(username, realmModel);

        assertNull(result);
    }

    @Test
    void isValidReturnsFalseOnWrongCredentialType() {
        var input = mock(CredentialInput.class);
        when(input.getType())
                .thenReturn(CredentialModel.KERBEROS);

        var result = legacyProvider.isValid(realmModel, userModel, input);

        assertFalse(result);
    }

    @Test
    void isValidReturnsFalseWhenInvalidCredentialValue() {
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
    void isValidReturnsTrueWhenUserValidated() {
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

        // JUnit was complaining about this "unnecessary" stub
        //        when(session.userCredentialManager())
        //                .thenReturn(mock(UserCredentialManager.class));

        var result = legacyProvider.isValid(realmModel, userModel, input);

        assertTrue(result);
    }

    @Test
    void isValidReturnsTrueWhenUserValidatedWithUserId() {
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
        
        // JUnit was complaining about this "unnecessary" stub
        //        when(session.userCredentialManager())
        //                .thenReturn(mock(UserCredentialManager.class));

        var result = legacyProvider.isValid(realmModel, userModel, input);

        assertTrue(result);
    }

    @Test
    void supportsPasswordCredentialType() {
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
        assertThrows(UnsupportedOperationException.class, () -> legacyProvider.getUserById("someId", realm));
    }

    @Test
    void removeFederationLinkWhenCredentialUpdates() {
        var input = mock(CredentialInput.class);
        when(userModel.getFederationLink())
                .thenReturn("someId");

        assertFalse(legacyProvider.updateCredential(realmModel, userModel, input));

        verify(userModel)
                .setFederationLink(null);
    }

    @Test
    void doNotRemoveFederationLinkWhenBlankAndCredentialUpdates() {
        var input = mock(CredentialInput.class);
        when(userModel.getFederationLink())
                .thenReturn(" ");

        assertFalse(legacyProvider.updateCredential(realmModel, userModel, input));

        verify(userModel, never())
                .setFederationLink(null);
    }

    @Test
    void doNotRemoveFederationLinkWhenNullAndCredentialUpdates() {
        var input = mock(CredentialInput.class);
        when(userModel.getFederationLink())
                .thenReturn(null);

        assertFalse(legacyProvider.updateCredential(realmModel, userModel, input));

        verify(userModel, never())
                .setFederationLink(null);
    }

    @Test
    void disableCredentialTypeDoesNothing() {
        legacyProvider.disableCredentialType(realmModel, userModel, "someType");
        Mockito.verifyNoInteractions(session, legacyUserService, userModelFactory, realmModel, userModel);
    }

    @Test
    void closeDoesNothing() {
        legacyProvider.close();
        Mockito.verifyNoInteractions(session, legacyUserService, userModelFactory, realmModel, userModel);
    }

    @Test
    void getDisableableCredentialTypesAlwaysReturnsEmptySet() {
        assertEquals(emptySet(), legacyProvider.getDisableableCredentialTypes(realmModel, userModel));
    }
}