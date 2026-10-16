package com.danielfrak.code.keycloak.providers.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUserService;
import com.danielfrak.code.keycloak.providers.rest.remote.usermodel.UserModelFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationFlowException;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialProvider;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.credential.CredentialProviderFactory;
import org.keycloak.credential.UserCredentialManager;
import org.keycloak.models.*;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.policy.PasswordPolicyManagerProvider;
import org.keycloak.tracing.TracingProvider;
import io.opentelemetry.api.trace.Span;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TotpLoginTest {

    @Mock
    private KeycloakSession session;
    @Mock
    private KeycloakTransactionManager transaction;
    @Mock
    private LegacyUserService legacyService;
    @Mock
    private UserModelFactory userModelFactory;
    @Mock
    private RealmModel realm;
    @Mock
    private UserModel user;
    @Mock
    private SubjectCredentialManager credentials;
    @Mock
    private PasswordPolicyManagerProvider passwordPolicy;

    private final ComponentModel model = new ComponentModel();
    private final UserCredentialModel password = UserCredentialModel.password("password");
    private LegacyProvider provider;
    private LegacyUser legacyUser;

    @BeforeEach
    void setUp() {
        model.getConfig().putSingle(UPDATE_USER_TOTPS_ON_LOGIN, "true");
        model.getConfig().putSingle(SEVER_FEDERATION_LINK, "false");
        var config = new MigrationConfiguration(model);
        var migration = new UserMigrationService(session, legacyService, new LocalUserLookup(session),
                userModelFactory, config);
        provider = new LegacyProvider(migration, new CredentialValidationService(session, legacyService, config), config);
        legacyUser = response("id", true);
        lenient().when(user.getUsername()).thenReturn("username");
        lenient().when(user.getId()).thenReturn("id");
        lenient().when(user.credentialManager()).thenReturn(credentials);
        lenient().when(session.getProvider(PasswordPolicyManagerProvider.class)).thenReturn(passwordPolicy);
        lenient().when(session.getTransactionManager()).thenReturn(transaction);
        lenient().when(legacyService.isPasswordValid("username", "password")).thenReturn(true);
        lenient().when(legacyService.findByUsername("username")).thenReturn(Optional.of(legacyUser));
    }

    private LegacyUser response(String id, boolean enabled) {
        return new LegacyUser(id, "username", null, null, null, enabled, false,
                null, null, null, null, List.of(), null);
    }

    @Test
    void shouldSynchronizeAfterPasswordValidationOnEveryPasswordLogin() {
        assertThat(provider.isValid(realm, user, password)).isTrue();
        assertThat(provider.isValid(realm, user, password)).isTrue();

        var order = inOrder(legacyService, credentials, userModelFactory);
        for (int login = 0; login < 2; login++) {
            order.verify(legacyService).isPasswordValid("username", "password");
            order.verify(credentials).updateCredential(password);
            order.verify(legacyService).findByUsername("username");
            order.verify(userModelFactory).synchronizeTotps(legacyUser, user);
        }
        verify(userModelFactory, never()).updateUserAttributes(any(), any());
        verify(transaction, never()).setRollbackOnly();
    }

    @Test
    void shouldRefreshThroughKeycloakCredentialManagerOnRepeatedLogins() {
        var localPassword = mock(LocalPasswordProvider.class);
        var manager = keycloakCredentialManager(localPassword);

        assertThat(manager.isValid(List.of(password))).isTrue();
        assertThat(manager.isValid(List.of(password))).isTrue();

        verify(userModelFactory, times(2)).synchronizeTotps(legacyUser, user);
        verify(localPassword, never()).isValid(any(), any(), any());
    }

    private UserCredentialManager keycloakCredentialManager(LocalPasswordProvider localPassword) {
        model.setId("legacy-provider");
        when(user.isFederated()).thenReturn(true);
        when(user.getFederationLink()).thenReturn(model.getId());
        when(realm.getComponent(model.getId())).thenReturn(model);
        when(session.getAttribute(model.getId())).thenReturn(provider);
        var sessionFactory = mock(KeycloakSessionFactory.class);
        var localFactory = mock(LocalPasswordFactory.class);
        lenient().when(localFactory.getId()).thenReturn(password.getType());
        lenient().when(session.getKeycloakSessionFactory()).thenReturn(sessionFactory);
        lenient().when(sessionFactory.getProviderFactoriesStream(CredentialProvider.class))
                .thenAnswer(invocation -> Stream.of(localFactory));
        lenient().when(session.getProvider(CredentialProvider.class, password.getType()))
                .thenReturn(localPassword);
        lenient().when(localPassword.supportsCredentialType(password.getType())).thenReturn(true);
        lenient().when(localPassword.isValid(realm, user, password)).thenReturn(true);
        var tracing = mock(TracingProvider.class);
        when(session.getProvider(TracingProvider.class)).thenReturn(tracing);
        when(tracing.trace(org.mockito.ArgumentMatchers.<Class<?>>any(), eq("isValid"),
                org.mockito.ArgumentMatchers.<Function<Span, Boolean>>any()))
                .thenAnswer(invocation -> {
                    Function<Span, Boolean> operation = invocation.getArgument(2);
                    return operation.apply(Span.getInvalid());
                });
        return new UserCredentialManager(session, realm, user);
    }

    private abstract static class LocalPasswordProvider
            implements CredentialProvider<PasswordCredentialModel>, CredentialInputValidator {
        @Override
        public abstract boolean supportsCredentialType(String type);
    }

    private abstract static class LocalPasswordFactory implements CredentialProviderFactory<LocalPasswordProvider> {
    }

    @Test
    void shouldNotFallBackToLocalPasswordIfLegacyPasswordIsRejected() {
        var localPassword = mock(LocalPasswordProvider.class);
        var manager = keycloakCredentialManager(localPassword);
        when(legacyService.isPasswordValid("username", "password")).thenReturn(false);

        assertThatThrownBy(() -> manager.isValid(List.of(password)))
                .isInstanceOf(AuthenticationFlowException.class);

        verify(localPassword, never()).isValid(any(), any(), any());
        verify(userModelFactory, never()).synchronizeTotps(any(), any());
    }

    @Test
    void shouldNotFallBackToLocalPasswordIfTotpRefreshFails() {
        var localPassword = mock(LocalPasswordProvider.class);
        var manager = keycloakCredentialManager(localPassword);
        when(legacyService.findByUsername("username")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> manager.isValid(List.of(password)))
                .isInstanceOf(AuthenticationFlowException.class);

        verify(transaction).setRollbackOnly();
        verify(localPassword, never()).isValid(any(), any(), any());
    }

    @Test
    void shouldPreserveLocalPasswordFallbackWhenTotpRefreshIsDisabled() {
        var localPassword = mock(LocalPasswordProvider.class);
        var manager = keycloakCredentialManager(localPassword);
        model.getConfig().putSingle(UPDATE_USER_TOTPS_ON_LOGIN, "false");
        when(legacyService.isPasswordValid("username", "password")).thenReturn(false);

        assertThat(manager.isValid(List.of(password))).isTrue();

        verify(localPassword).isValid(realm, user, password);
        verify(userModelFactory, never()).synchronizeTotps(any(), any());
    }

    @Test
    void shouldReuseLegacyResponseForOtherRefreshOptions() {
        model.getConfig().putSingle(UPDATE_USER_ON_LOGIN, "true");
        model.getConfig().putSingle(UPDATE_USER_GROUPS_ON_LOGIN, "SYNC_EVERY_LOGIN");
        model.getConfig().putSingle(UPDATE_USER_ROLES_ON_LOGIN, "SYNC_EVERY_LOGIN");

        assertThat(provider.isValid(realm, user, password)).isTrue();

        verify(legacyService).findByUsername("username");
        var order = inOrder(userModelFactory);
        order.verify(userModelFactory).synchronizeTotps(legacyUser, user);
        order.verify(userModelFactory).updateUserAttributes(legacyUser, user);
        order.verify(userModelFactory).synchronizeGroups(legacyUser, realm, user);
        order.verify(userModelFactory).synchronizeRoles(legacyUser, realm, user);
    }

    @Test
    void shouldNotSynchronizeWhenOptionIsAbsent() {
        model.getConfig().remove(UPDATE_USER_TOTPS_ON_LOGIN);

        assertThat(provider.isValid(realm, user, password)).isTrue();

        verify(legacyService, never()).findByUsername(anyString());
        verifyNoInteractions(userModelFactory);
    }

    @Test
    void shouldAbortOnInvalidPasswordWithoutFetchingOrChangingTotps() {
        when(legacyService.isPasswordValid("username", "password")).thenReturn(false);

        assertThatThrownBy(() -> provider.isValid(realm, user, password))
                .isInstanceOfSatisfying(AuthenticationFlowException.class,
                        ex -> assertThat(ex.getError()).isEqualTo(AuthenticationFlowError.INVALID_CREDENTIALS));

        verify(legacyService, never()).findByUsername(anyString());
        verifyNoInteractions(credentials, userModelFactory);
    }

    @Test
    void shouldAbortAndRollBackWhenLegacyUserIsUnavailable() {
        when(legacyService.findByUsername("username")).thenReturn(Optional.empty());

        assertRefreshFailure();

        verifyNoInteractions(userModelFactory);
    }

    private void assertRefreshFailure() {
        assertThatThrownBy(() -> provider.isValid(realm, user, password))
                .isInstanceOfSatisfying(AuthenticationFlowException.class,
                        ex -> assertThat(ex.getError()).isEqualTo(AuthenticationFlowError.INTERNAL_ERROR));
        verify(transaction).setRollbackOnly();
    }

    @Test
    void shouldAbortAndRollBackWhenLegacyRequestFails() {
        when(legacyService.findByUsername("username")).thenThrow(new IllegalStateException("unavailable"));

        assertRefreshFailure();
    }

    @Test
    void shouldAbortAndRollBackWhenTotpSynchronizationFails() {
        doThrow(new IllegalArgumentException("Invalid TOTP configuration"))
                .when(userModelFactory).synchronizeTotps(legacyUser, user);

        assertRefreshFailure();
    }

    @Test
    void shouldNotSwallowTotpFailuresWhenProfileRefreshIsEnabled() {
        model.getConfig().putSingle(UPDATE_USER_ON_LOGIN, "true");
        doThrow(new IllegalStateException("Storage failed"))
                .when(userModelFactory).synchronizeTotps(legacyUser, user);

        assertRefreshFailure();

        verify(userModelFactory, never()).updateUserAttributes(any(), any());
    }

    @Test
    void shouldKeepExistingBestEffortProfileRefreshBehavior() {
        model.getConfig().putSingle(UPDATE_USER_ON_LOGIN, "true");
        doThrow(new IllegalStateException("Profile failed"))
                .when(userModelFactory).updateUserAttributes(legacyUser, user);

        assertThat(provider.isValid(realm, user, password)).isTrue();

        verify(userModelFactory).synchronizeTotps(legacyUser, user);
        verify(transaction, never()).setRollbackOnly();
    }

    @Test
    void shouldRejectSeveringFederationEvenIfConfigurationValidationWasBypassed() {
        model.getConfig().putSingle(SEVER_FEDERATION_LINK, "true");

        assertRefreshFailure();

        verify(legacyService, never()).findByUsername(anyString());
        verifyNoInteractions(userModelFactory);
    }

    @Test
    void shouldRejectMismatchedLegacyUserId() {
        when(legacyService.findByUsername("username")).thenReturn(Optional.of(response("different-id", true)));

        assertRefreshFailure();

        verifyNoInteractions(userModelFactory);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldSupportLegacyResponsesWithoutAnId(String id) {
        var response = response(id, true);
        when(legacyService.findByUsername("username")).thenReturn(Optional.of(response));

        assertThat(provider.isValid(realm, user, password)).isTrue();

        verify(userModelFactory).synchronizeTotps(response, user);
    }

    @Test
    void shouldRejectDisabledLegacyUser() {
        when(legacyService.findByUsername("username")).thenReturn(Optional.of(response("id", false)));

        assertRefreshFailure();

        verifyNoInteractions(userModelFactory);
    }
}
