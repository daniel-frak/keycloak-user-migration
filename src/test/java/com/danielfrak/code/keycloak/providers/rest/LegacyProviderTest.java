package com.danielfrak.code.keycloak.providers.rest;

import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUserService;
import com.danielfrak.code.keycloak.providers.rest.remote.usermodel.UserModelFactory;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationFlowException;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.*;
import org.keycloak.credential.UserCredentialManager;
import org.keycloak.models.*;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.policy.PasswordPolicyManagerProvider;
import org.keycloak.policy.PolicyError;
import org.keycloak.tracing.TracingProvider;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;
import static com.danielfrak.code.keycloak.providers.rest.remote.TestLegacyUser.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        var migrationConfiguration = new MigrationConfiguration(model);
        var localUserLookup = new LocalUserLookup(session);
        var userMigrationService = new UserMigrationService(
                session, legacyUserService, localUserLookup, userModelFactory, migrationConfiguration);
        var credentialValidationService =
                new CredentialValidationService(session, legacyUserService, migrationConfiguration);
        legacyProvider = new LegacyProvider(userMigrationService, credentialValidationService, migrationConfiguration);

        sessionIsConfigured();
        noUsersExistInLegacySystem();
    }

    private void sessionIsConfigured() {
        lenient().when(session.getProvider(PasswordPolicyManagerProvider.class))
                .thenReturn(passwordPolicyManagerProvider);
        lenient().when(session.users())
                .thenReturn(userProvider);
    }

    private void legacyUserExistsInKeycloakWithId(UserModel existingUser, String id) {
        lenient().when(userProvider.getUserById(realmModel, id))
                .thenReturn(existingUser);
    }

    private UserModel userWithDifferentUsername() {
        var existingUser = mock(UserModel.class);
        lenient().when(existingUser.getUsername())
                .thenReturn("different");
        return existingUser;
    }

    private void noUserExistsInKeycloak() {
        lenient().when(userProvider.getUserByUsername(any(), any()))
                .thenReturn(null);
    }

    private void userWillBeSuccessfullyCreated(LegacyUser user) {
        lenient().when(userModelFactory.create(user, realmModel))
                .thenReturn(userModel);
    }

    private void userExistsInLegacySystemWithEmail(LegacyUser user, String email) {
        lenient().when(legacyUserService.findByEmail(email))
                .thenReturn(Optional.of(user));
    }

    private void everyBooleanSessionAttributeIsTrue() {
        when(session.getAttribute(anyString(), eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);
    }

    private void legacyUserExistsInKeycloakWithUserName(String username) {
        when(userProvider.getUserByUsername(realmModel, username))
                .thenReturn(userModel);
    }

    private void userExistsInLegacySystemWithUserName(LegacyUser legacyUser, String username) {
        lenient().when(legacyUserService.findByUsername(username))
                .thenReturn(Optional.of(legacyUser));
    }

    private void mockUserModelRequiresUpdatingPassword() {
        when(userModel.getRequiredActionsStream())
                .thenReturn(Stream.of(UserModel.RequiredAction.UPDATE_PASSWORD.name()));
    }

    private void passwordBreaksPolicy(String password) {
        when(passwordPolicyManagerProvider.validate(realmModel, userModel, password))
                .thenReturn(new PolicyError("someError"));
    }

    private void mockUserModelHasId(String userId) {
        lenient().when(userModel.getId())
                .thenReturn(userId);
    }

    private void userModelFactoryThrowsWhenUpdatingAttributes() {
        doThrow(new RuntimeException("boom"))
                .when(userModelFactory).updateUserAttributes(any(), any());
    }

    private void passwordIsValid(LegacyUser legacyUser, String password) {
        String username = legacyUser.username();
        passwordIsValid(username, password);
    }

    private void passwordIsValid(String username, String password) {
        lenient().when(legacyUserService.isPasswordValid(username, password))
                .thenReturn(true);
    }

    private void mockUserHasCredentialManager(SubjectCredentialManager userCredentialManager) {
        lenient().when(userModel.credentialManager())
                .thenReturn(userCredentialManager);
    }

    private CredentialInput inputWithPassword(String password) {
        CredentialInput input = inputOfType(PasswordCredentialModel.TYPE);
        when(input.getChallengeResponse())
                .thenReturn(password);
        return input;
    }

    private void passwordIsInvalid(String username, String password) {
        when(legacyUserService.isPasswordValid(username, password))
                .thenReturn(false);
    }

    private void mockUserModelHasUserName(String username) {
        lenient().when(userModel.getUsername())
                .thenReturn(username);
    }

    private void modelIsConfigured(MultivaluedHashMap<String, String> config) {
        when(model.getConfig()).thenReturn(config);
    }

    private CredentialInput inputOfType(String kerberos) {
        var input = mock(CredentialInput.class);
        when(input.getType())
                .thenReturn(kerberos);
        return input;
    }

    private void mockUserModelHasFederationLink() {
        String federationLink = "someId";
        mockUserModelHasFederationLink(federationLink);
    }

    private void mockUserModelHasFederationLink(String federationLink) {
        when(userModel.getFederationLink())
                .thenReturn(federationLink);
    }

    private void noUsersExistInLegacySystem() {
        lenient().when(legacyUserService.findByUsername(any()))
                .thenReturn(Optional.empty());
        lenient().when(legacyUserService.findByEmail(any()))
                .thenReturn(Optional.empty());
    }

    @Nested
    class GetByUserId {

        @Test
        void getUserByIdShouldThrowException() {
            var realm = mock(RealmModel.class);
            assertThatThrownBy(() -> legacyProvider.getUserById(realm, "someId"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    class GetUserByUsername {

        @Test
        void shouldGetUserByUsername() {
            final String username = "user";
            final LegacyUser user = withId();
            userExistsInLegacySystemWithUserName(user, username);
            userWillBeSuccessfullyCreated(user);

            UserModel result = legacyProvider.getUserByUsername(realmModel, username);

            assertThat(result).isEqualTo(userModel);
        }

        @Test
        void shouldReturnNullGivenUserNotFoundByUsername() {
            noUsersExistInLegacySystem();

            UserModel result = legacyProvider.getUserByUsername(realmModel, "user");

            assertThat(result).isNull();
        }
    }

    @Nested
    class GetUserByEmail {

        @Test
        void shouldGetUserByEmail() {
            final String email = "email";
            final LegacyUser user = withId();
            userExistsInLegacySystemWithEmail(user, email);
            userWillBeSuccessfullyCreated(user);

            UserModel result = legacyProvider.getUserByEmail(realmModel, email);

            assertThat(result).isEqualTo(userModel);
        }

        @Test
        void shouldReturnExistingUserAndUpdateAttributesGivenUserIdExistsButHasDifferentUsername() {
            final String email = "email";
            final LegacyUser legacyUser = withId();
            userExistsInLegacySystemWithEmail(legacyUser, email);
            noUserExistsInKeycloak();
            UserModel existingKeycloakUser = userWithDifferentUsername();
            legacyUserExistsInKeycloakWithId(existingKeycloakUser, legacyUser.id());

            UserModel result = legacyProvider.getUserByEmail(realmModel, email);

            assertThat(result).isNotNull();
            verify(userModelFactory).updateUserAttributes(legacyUser, existingKeycloakUser);
            verify(userModelFactory, never()).create(any(), any());
        }

        @Test
        void shouldReturnNullGivenUserNotFoundByEmail() {
            final String username = "user";
            noUsersExistInLegacySystem();

            UserModel result = legacyProvider.getUserByEmail(realmModel, username);

            assertThat(result).isNull();
        }
    }

    @Nested
    class GetUserByUserName {

        @Test
        void shouldUpdateExistingUserDataGivenFoundByUsername() {
            final String username = "user";
            final LegacyUser legacyUser = withId();
            userExistsInLegacySystemWithUserName(legacyUser, username);
            legacyUserExistsInKeycloakWithUserName(legacyUser.username());

            UserModel result = legacyProvider.getUserByUsername(realmModel, username);

            assertThat(result).isEqualTo(userModel);
            verify(userModelFactory).updateUserAttributes(legacyUser, userModel);
            verify(userModelFactory, never()).create(any(), any());
        }

        @Test
        void shouldUpdateExistingUserDataGivenFoundByLegacyIdWithSameUsername() {
            final String username = "user";
            final LegacyUser legacyUser = withId();
            userExistsInLegacySystemWithUserName(legacyUser, username);
            noUserExistsInKeycloak();
            legacyUserExistsInKeycloakWithId(userModel, legacyUser.id());
            userExistsInLegacySystemWithUserName(legacyUser, legacyUser.username());

            UserModel result = legacyProvider.getUserByUsername(realmModel, username);

            assertThat(result).isEqualTo(userModel);
            verify(userModelFactory).updateUserAttributes(legacyUser, userModel);
            verify(userModelFactory, never()).create(any(), any());
        }

        @Test
        void shouldUpdateExistingUserDataGivenFoundByLegacyIdWithUsernameDifferingOnlyInCase() {
            final String username = "user";
            final LegacyUser legacyUser = withId();
            userExistsInLegacySystemWithUserName(legacyUser, username);
            noUserExistsInKeycloak();
            legacyUserExistsInKeycloakWithId(userModel, legacyUser.id());
            mockUserModelHasUserName(legacyUser.username().toLowerCase());

            UserModel result = legacyProvider.getUserByUsername(realmModel, username);

            assertThat(result).isEqualTo(userModel);
            verify(userModelFactory).updateUserAttributes(legacyUser, userModel);
            verify(userModelFactory, never()).create(any(), any());
        }

        @Test
        void shouldCreateUserGivenLegacyIdIsBlankAndNoLocalUserExists() {
            final String username = "user";
            final LegacyUser user = new LegacyUser(
                    " ",
                    username,
                    "user@email.com",
                    "John",
                    "Smith",
                    true,
                    true,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
            userExistsInLegacySystemWithUserName(user, username);
            noUserExistsInKeycloak();
            userWillBeSuccessfullyCreated(user);

            UserModel result = legacyProvider.getUserByUsername(realmModel, username);

            assertThat(result).isEqualTo(userModel);
            verify(userProvider, never()).getUserById(any(), any());
        }

        @Test
        void shouldCreateUserGivenLegacyIdIsNullAndNoLocalUserExists() {
            final String username = "user";
            final LegacyUser user = new LegacyUser(
                    null,
                    username,
                    "user@email.com",
                    "John",
                    "Smith",
                    true,
                    true,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
            userExistsInLegacySystemWithUserName(user, username);
            noUserExistsInKeycloak();
            userWillBeSuccessfullyCreated(user);

            UserModel result = legacyProvider.getUserByUsername(realmModel, username);

            assertThat(result).isEqualTo(userModel);
            verify(userProvider, never()).getUserById(any(), any());
        }

        @Test
        void shouldSkipLegacyLookupGivenExistingLookupInProgress() {
            final String username = "user";
            everyBooleanSessionAttributeIsTrue();

            UserModel result = legacyProvider.getUserByUsername(realmModel, username);

            assertThat(result).isNull();
            verifyNoInteractions(legacyUserService);
        }
    }

    @Nested
    class IsValid {

        @Test
        void shouldReturnFalseOnWrongCredentialType() {
            CredentialInput input = inputOfType(CredentialModel.KERBEROS);

            boolean result = legacyProvider.isValid(realmModel, userModel, input);

            assertThat(result).isFalse();
        }

        @Test
        void shouldReturnFalseOnWrongCredentialTypeEvenGivenUserIsNull() {
            CredentialInput input = inputOfType(CredentialModel.KERBEROS);

            boolean result = legacyProvider.isValid(realmModel, null, input);

            assertThat(result).isFalse();
        }

        @Test
        void shouldThrowGivenNullInput() {
            assertThatThrownBy(() -> legacyProvider.isValid(realmModel, userModel, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldReturnFalseGivenInvalidCredentialValue() {

            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("false"));
            config.put(UPDATE_USER_ON_LOGIN, List.of("true"));
            modelIsConfigured(config);

            final String username = "user";
            final String password = "password";

            mockUserModelHasUserName(username);
            CredentialInput input = inputWithPassword(password);
            passwordIsInvalid(username, password);

            boolean result = legacyProvider.isValid(realmModel, userModel, input);

            assertThat(result).isFalse();
        }

        @Test
        void shouldRefreshUserAttributesGivenLegacyUserExists() {
            var userCredentialManager = mock(SubjectCredentialManager.class);

            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("false"));
            config.put(UPDATE_USER_ON_LOGIN, List.of("true"));
            modelIsConfigured(config);

            String password = "password";
            final LegacyUser legacyUser = withId();
            mockUserModelHasUserName(legacyUser.username());
            mockUserHasCredentialManager(userCredentialManager);
            CredentialInput input = inputWithPassword(password);
            passwordIsValid(legacyUser, password);
            userExistsInLegacySystemWithUserName(legacyUser, legacyUser.username());

            boolean result = legacyProvider.isValid(realmModel, userModel, input);

            assertThat(result).isTrue();
            verify(userModelFactory).updateUserAttributes(legacyUser, userModel);
        }

        @Test
        void shouldContinueAuthenticationGivenRefreshThrowsException() {
            var userCredentialManager = mock(SubjectCredentialManager.class);

            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("false"));
            config.put(UPDATE_USER_ON_LOGIN, List.of("true"));
            modelIsConfigured(config);

            String password = "password";
            final LegacyUser legacyUser = withId();
            mockUserModelHasUserName(legacyUser.username());
            mockUserHasCredentialManager(userCredentialManager);
            CredentialInput input = inputWithPassword(password);
            userExistsInLegacySystemWithUserName(legacyUser, legacyUser.username());
            passwordIsValid(legacyUser, password);
            userModelFactoryThrowsWhenUpdatingAttributes();

            boolean result = legacyProvider.isValid(realmModel, userModel, input);

            assertThat(result).isTrue();
            verify(userCredentialManager).updateCredential(input);
        }

        @Test
        void shouldNotRefreshUserAttributesGivenRefreshDisabled() {
            var userCredentialManager = mock(SubjectCredentialManager.class);

            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("false"));
            config.put(UPDATE_USER_ON_LOGIN, List.of("false"));
            modelIsConfigured(config);

            String password = "password";
            final LegacyUser legacyUser = withId();
            mockUserModelHasUserName(legacyUser.username());
            mockUserHasCredentialManager(userCredentialManager);
            CredentialInput input = inputWithPassword(password);
            passwordIsValid(legacyUser, password);

            boolean result = legacyProvider.isValid(realmModel, userModel, input);

            assertThat(result).isTrue();
            verify(userModelFactory, never()).updateUserAttributes(any(), any());
            verify(legacyUserService, never()).findByUsername(anyString());
        }

        @Test
        void shouldRefreshUserGroupsGivenEnabled() {
            var userCredentialManager = mock(SubjectCredentialManager.class);

            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("false"));
            config.put(UPDATE_USER_ON_LOGIN, List.of("false"));
            config.put(UPDATE_USER_GROUPS_ON_LOGIN, List.of("SYNC_EVERY_LOGIN"));
            modelIsConfigured(config);

            String password = "password";
            final LegacyUser legacyUser = withId();
            mockUserModelHasUserName(legacyUser.username());
            mockUserHasCredentialManager(userCredentialManager);
            CredentialInput input = inputWithPassword(password);
            passwordIsValid(legacyUser, password);
            userExistsInLegacySystemWithUserName(legacyUser, legacyUser.username());

            boolean result = legacyProvider.isValid(realmModel, userModel, input);

            assertThat(result).isTrue();
            verify(userModelFactory).synchronizeGroups(legacyUser, realmModel, userModel);
        }

        @Test
        void shouldRefreshUserRolesGivenEnabled() {
            var userCredentialManager = mock(SubjectCredentialManager.class);

            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("false"));
            config.put(UPDATE_USER_ON_LOGIN, List.of("false"));
            config.put(UPDATE_USER_ROLES_ON_LOGIN, List.of("SYNC_EVERY_LOGIN"));
            modelIsConfigured(config);

            String password = "password";
            final LegacyUser legacyUser = withId();
            mockUserModelHasUserName(legacyUser.username());
            mockUserHasCredentialManager(userCredentialManager);
            CredentialInput input = inputWithPassword(password);
            passwordIsValid(legacyUser, password);
            userExistsInLegacySystemWithUserName(legacyUser, legacyUser.username());

            boolean result = legacyProvider.isValid(realmModel, userModel, input);

            assertThat(result).isTrue();
            verify(userModelFactory).synchronizeRoles(legacyUser, realmModel, userModel);
        }

        @Test
        void shouldRefreshUserGroupsOnlyAddGivenEnabled() {
            var userCredentialManager = mock(SubjectCredentialManager.class);

            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("false"));
            config.put(UPDATE_USER_ON_LOGIN, List.of("false"));
            config.put(UPDATE_USER_GROUPS_ON_LOGIN, List.of("SYNC_EVERY_LOGIN_ONLY_ADD"));
            modelIsConfigured(config);

            String password = "password";
            final LegacyUser legacyUser = withId();
            mockUserModelHasUserName(legacyUser.username());
            mockUserHasCredentialManager(userCredentialManager);
            CredentialInput input = inputWithPassword(password);
            passwordIsValid(legacyUser, password);
            userExistsInLegacySystemWithUserName(legacyUser, legacyUser.username());

            boolean result = legacyProvider.isValid(realmModel, userModel, input);

            assertThat(result).isTrue();
            verify(userModelFactory).synchronizeGroupsAddOnly(legacyUser, realmModel, userModel);
            verify(userModelFactory, never()).synchronizeGroups(legacyUser, realmModel, userModel);
        }

        @Test
        void shouldRefreshUserRolesOnlyAddGivenEnabled() {
            var userCredentialManager = mock(SubjectCredentialManager.class);

            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("false"));
            config.put(UPDATE_USER_ON_LOGIN, List.of("false"));
            config.put(UPDATE_USER_ROLES_ON_LOGIN, List.of("SYNC_EVERY_LOGIN_ONLY_ADD"));
            modelIsConfigured(config);

            String password = "password";
            final LegacyUser legacyUser = withId();
            mockUserModelHasUserName(legacyUser.username());
            mockUserHasCredentialManager(userCredentialManager);
            CredentialInput input = inputWithPassword(password);
            passwordIsValid(legacyUser, password);
            userExistsInLegacySystemWithUserName(legacyUser, legacyUser.username());

            boolean result = legacyProvider.isValid(realmModel, userModel, input);

            assertThat(result).isTrue();
            verify(userModelFactory).synchronizeRolesAddOnly(legacyUser, realmModel, userModel);
            verify(userModelFactory, never()).synchronizeRoles(legacyUser, realmModel, userModel);
        }

        @Test
        void shouldReturnTrueAndUpdateCredentialsGivenUserValidatedWithUserId() {
            var subjectCredentialManager = mock(SubjectCredentialManager.class);

            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("true"));
            modelIsConfigured(config);

            final String userId = "1234567890";
            final String password = "password";

            mockUserModelHasId(userId);
            CredentialInput input = inputWithPassword(password);
            passwordIsValid(userId, password);
            mockUserHasCredentialManager(subjectCredentialManager);

            boolean result = legacyProvider.isValid(realmModel, userModel, input);

            assertThat(result).isTrue();
            verify(subjectCredentialManager).updateCredential(input);
        }

        @Test
        void shouldReturnTrueGivenUserValidatedButPasswordBreaksPolicy() {
            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("true"));
            modelIsConfigured(config);

            final String userId = "1234567890";
            final String password = "password";

            mockUserModelHasId(userId);
            CredentialInput input = inputWithPassword(password);
            passwordIsValid(userId, password);
            passwordBreaksPolicy(password);

            boolean result = legacyProvider.isValid(realmModel, userModel, input);

            assertThat(result).isTrue();
        }

        @Test
        void shouldNotUpdateCredentialsGivenUserValidatedButPasswordBreaksPolicy() {
            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("true"));
            modelIsConfigured(config);

            final String userId = "1234567890";
            final String password = "password";

            mockUserModelHasId(userId);
            CredentialInput input = inputWithPassword(password);
            passwordIsValid(userId, password);
            passwordBreaksPolicy(password);

            legacyProvider.isValid(realmModel, userModel, input);

            verify(userModel, never()).credentialManager();
        }

        @Test
        void shouldRequirePasswordChangeGivenUserValidatedButPasswordBreaksPolicy() {
            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("true"));
            modelIsConfigured(config);

            final String userId = "1234567890";
            final String password = "password";

            mockUserModelHasId(userId);
            CredentialInput input = inputWithPassword(password);
            passwordIsValid(userId, password);
            passwordBreaksPolicy(password);

            legacyProvider.isValid(realmModel, userModel, input);

            verify(userModel).addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
        }

        @Test
        void shouldNotAddRequirePasswordActionGivenOneAlreadyExists() {
            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("true"));
            modelIsConfigured(config);

            final String userId = "1234567890";
            final String password = "password";

            mockUserModelHasId(userId);
            CredentialInput input = inputWithPassword(password);
            passwordIsValid(userId, password);
            passwordBreaksPolicy(password);
            mockUserModelRequiresUpdatingPassword();

            legacyProvider.isValid(realmModel, userModel, input);

            verify(userModel, never()).addRequiredAction(UserModel.RequiredAction.UPDATE_PASSWORD);
        }
    }

    @Nested
    class IsPasswordValid {

        @Test
        void shouldReturnTrueAndUpdateCredentialsGivenUserValidated() {
            var userCredentialManager = mock(SubjectCredentialManager.class);

            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.put(USE_USER_ID_FOR_CREDENTIAL_VERIFICATION, List.of("false"));
            config.put(UPDATE_USER_ON_LOGIN, List.of("true"));
            modelIsConfigured(config);

            final String username = "user";
            final String password = "password";

            mockUserModelHasUserName(username);
            CredentialInput input = inputWithPassword(password);
            passwordIsValid(username, password);
            mockUserHasCredentialManager(userCredentialManager);

            boolean result = legacyProvider.isValid(realmModel, userModel, input);

            assertThat(result).isTrue();
            verify(userCredentialManager).updateCredential(input);
        }
    }

    @Nested
    class SupportsCredentialType {

        @Test
        void shouldSupportPasswordCredentialType() {
            boolean result = legacyProvider.supportsCredentialType(PasswordCredentialModel.TYPE);

            assertThat(result).isTrue();
        }
    }

    @Nested
    class IsConfiguredFor {

        @Test
        void isConfiguredForShouldReturnTrueForSupportedCredentialType() {
            boolean result = legacyProvider.isConfiguredFor(mock(RealmModel.class), mock(UserModel.class),
                    PasswordCredentialModel.TYPE);

            assertThat(result).isTrue();
        }

        @Test
        void isConfiguredForShouldReturnFalseForUnsupportedCredentialType() {
            boolean result = legacyProvider.isConfiguredFor(mock(RealmModel.class), mock(UserModel.class),
                    "someUnsupportedType");

            assertThat(result).isFalse();
        }
    }

    @Nested
    class UpdateCredential {

        @Test
        void updateCredentialShouldSeverFederationLinkGivenEnabled() {
            var input = mock(CredentialInput.class);
            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.put(SEVER_FEDERATION_LINK, List.of("true"));
            modelIsConfigured(config);
            mockUserModelHasFederationLink();

            boolean result = legacyProvider.updateCredential(realmModel, userModel, input);

            assertThat(result).isFalse();
            verify(userModel).setFederationLink(null);
        }

        @Test
        void updateCredentialShouldNotSeverFederationLinkGivenDisabled() {
            var input = mock(CredentialInput.class);
            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.put(SEVER_FEDERATION_LINK, List.of("false"));
            modelIsConfigured(config);

            boolean result = legacyProvider.updateCredential(realmModel, userModel, input);

            assertThat(result).isFalse();
            verify(userModel, never()).setFederationLink(null);
        }

        @Test
        void updateCredentialShouldNotAttemptSeverGivenUserIsNull() {
            var input = mock(CredentialInput.class);

            boolean result = legacyProvider.updateCredential(realmModel, null, input);

            assertThat(result).isFalse();
        }

        @Test
        void updateCredentialShouldAcceptNullInputAndStillSeverGivenEnabledByDefault() {
            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            modelIsConfigured(config);
            mockUserModelHasFederationLink();

            boolean result = legacyProvider.updateCredential(realmModel, userModel, null);

            assertThat(result).isFalse();

            verify(userModel).setFederationLink(null);
        }

        @Test
        void updateCredentialShouldNotClearBlankFederationLink() {
            var input = mock(CredentialInput.class);
            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.put(SEVER_FEDERATION_LINK, List.of("true"));
            modelIsConfigured(config);
            mockUserModelHasFederationLink("  ");

            boolean result = legacyProvider.updateCredential(realmModel, userModel, input);

            assertThat(result).isFalse();

            verify(userModel, never()).setFederationLink(null);
        }

        @Test
        void updateCredentialShouldNotClearNullFederationLink() {
            var input = mock(CredentialInput.class);
            MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
            config.put(SEVER_FEDERATION_LINK, List.of("true"));
            modelIsConfigured(config);
            mockUserModelHasFederationLink(null);

            boolean result = legacyProvider.updateCredential(realmModel, userModel, input);

            assertThat(result).isFalse();

            verify(userModel, never()).setFederationLink(null);
        }
    }

    @Nested
    class UpdateCredentialType {

        @Test
        void disableCredentialTypeShouldDoNothing() {
            legacyProvider.disableCredentialType(realmModel, userModel, "someType");
            Mockito.verifyNoInteractions(session, legacyUserService, userModelFactory, realmModel);
        }
    }

    @Nested
    class Close {

        @Test
        void closeShouldDoNothing() {
            legacyProvider.close();
            Mockito.verifyNoInteractions(session, legacyUserService, userModelFactory, realmModel, userModel);
        }
    }

    @Nested
    class GetDisableableCredentialTypesStream {

        @Test
        void getDisableableCredentialTypesShouldAlwaysReturnEmptySet() {
            Stream<String> result = legacyProvider.getDisableableCredentialTypesStream(realmModel, userModel);

            assertThat(result.toList()).isEmpty();
        }
    }

    @Nested
    class AddUser {

        @Test
        void addUserShouldReturnNull() {
            UserModel result = legacyProvider.addUser(realmModel, "someUser");
            assertThat(result).isNull();
        }
    }

    @Nested
    class RemoveUser {

        @Test
        void removeUserShouldReturnTrue() {
            var result = legacyProvider.removeUser(realmModel, userModel);
            assertThat(result).isTrue();
        }

        @Test
        void removeUserShouldReturnTrueForNullUser() {
            var result = legacyProvider.removeUser(realmModel, null);
            assertThat(result).isTrue();
        }
    }

    @Nested
    class IsValidWithTotpRefresh {

        private final MultivaluedHashMap<String, String> config = new MultivaluedHashMap<>();
        private final UserCredentialModel password = UserCredentialModel.password("password");

        @Mock
        private KeycloakTransactionManager transaction;

        @Mock
        private SubjectCredentialManager credentials;

        private LegacyUser legacyUser;

        @BeforeEach
        void setUp() {
            config.putSingle(UPDATE_USER_TOTPS_ON_LOGIN, "true");
            config.putSingle(SEVER_FEDERATION_LINK, "false");
            modelIsConfigured(config);
            legacyUser = response("id", true);

            mockUserModelHasUserName("username");
            mockUserModelHasId("id");
            mockUserHasCredentialManager(credentials);
            sessionTransactionManagerIsAvailable();
            passwordIsValid("username", "password");
            userExistsInLegacySystemWithUserName(legacyUser, "username");
        }

        private void sessionTransactionManagerIsAvailable() {
            lenient().when(session.getTransactionManager())
                    .thenReturn(transaction);
        }

        private LegacyUser response(String id, boolean enabled) {
            return new LegacyUser(id, "username", null, null, null, enabled, false,
                    null, null, null, null, List.of(), null);
        }

        private UserCredentialManager keycloakCredentialManager(LocalPasswordProvider localPassword) {
            var providerId = "legacy-provider";
            var component = new ComponentModel();
            component.setId(providerId);
            component.setConfig(config);
            when(userModel.isFederated()).thenReturn(true);
            mockUserModelHasFederationLink(providerId);
            when(realmModel.getComponent(providerId)).thenReturn(component);
            when(session.getAttribute(providerId)).thenReturn(legacyProvider);
            var sessionFactory = mock(KeycloakSessionFactory.class);
            var localFactory = mock(LocalPasswordFactory.class);
            lenient().when(localFactory.getId()).thenReturn(password.getType());
            lenient().when(session.getKeycloakSessionFactory()).thenReturn(sessionFactory);
            lenient().when(sessionFactory.getProviderFactoriesStream(CredentialProvider.class))
                    .thenAnswer(invocation -> Stream.of(localFactory));
            lenient().when(session.getProvider(CredentialProvider.class, password.getType()))
                    .thenReturn(localPassword);
            lenient().when(localPassword.supportsCredentialType(password.getType())).thenReturn(true);
            lenient().when(localPassword.isValid(realmModel, userModel, password)).thenReturn(true);
            var tracing = mock(TracingProvider.class);
            when(session.getProvider(TracingProvider.class)).thenReturn(tracing);
            when(tracing.trace(org.mockito.ArgumentMatchers.<Class<?>>any(), eq("isValid"),
                    org.mockito.ArgumentMatchers.<Function<Span, Boolean>>any()))
                    .thenAnswer(invocation -> {
                        Function<Span, Boolean> operation = invocation.getArgument(2);
                        return operation.apply(Span.getInvalid());
                    });

            return new UserCredentialManager(session, realmModel, userModel);
        }

        @Test
        void shouldSynchronizeAfterPasswordValidationOnEveryPasswordLogin() {
            boolean firstLogin = legacyProvider.isValid(realmModel, userModel, password);
            boolean secondLogin = legacyProvider.isValid(realmModel, userModel, password);

            assertThat(firstLogin).isTrue();
            assertThat(secondLogin).isTrue();
            InOrder order = inOrder(legacyUserService, credentials, userModelFactory);
            for (int login = 0; login < 2; login++) {
                order.verify(legacyUserService).isPasswordValid("username", "password");
                order.verify(credentials).updateCredential(password);
                order.verify(legacyUserService).findByUsername("username");
                order.verify(userModelFactory).synchronizeTotps(legacyUser, userModel);
            }
            verify(userModelFactory, never()).updateUserAttributes(any(), any());
            verify(transaction, never()).setRollbackOnly();
        }

        @Test
        void shouldRefreshThroughCredentialManagerOnRepeatedLogins() {
            LocalPasswordProvider localPassword = mock(LocalPasswordProvider.class);
            UserCredentialManager manager = keycloakCredentialManager(localPassword);

            boolean firstLogin = manager.isValid(List.of(password));
            boolean secondLogin = manager.isValid(List.of(password));

            assertThat(firstLogin).isTrue();
            assertThat(secondLogin).isTrue();
            verify(userModelFactory, times(2)).synchronizeTotps(legacyUser, userModel);
            verify(localPassword, never()).isValid(any(), any(), any());
        }

        @Test
        void shouldNotFallBackToLocalPasswordGivenLegacyPasswordIsRejected() {
            var localPassword = mock(LocalPasswordProvider.class);
            var manager = keycloakCredentialManager(localPassword);
            passwordIsInvalid("username", "password");
            List<CredentialInput> inputs = List.of(password);

            assertThatThrownBy(() -> manager.isValid(inputs))
                    .isInstanceOf(AuthenticationFlowException.class)
                    .extracting(AuthenticationFlowException.class::cast)
                    .extracting(AuthenticationFlowException::getError)
                    .isEqualTo(AuthenticationFlowError.INVALID_CREDENTIALS);
            verify(localPassword, never()).isValid(any(), any(), any());
            verify(userModelFactory, never()).synchronizeTotps(any(), any());
        }

        @Test
        void shouldNotFallBackToLocalPasswordGivenTotpRefreshFails() {
            var localPassword = mock(LocalPasswordProvider.class);
            var manager = keycloakCredentialManager(localPassword);
            noUsersExistInLegacySystem();
            List<CredentialInput> inputs = List.of(password);

            assertThatThrownBy(() -> manager.isValid(inputs))
                    .isInstanceOf(AuthenticationFlowException.class)
                    .extracting(AuthenticationFlowException.class::cast)
                    .extracting(AuthenticationFlowException::getError)
                    .isEqualTo(AuthenticationFlowError.INTERNAL_ERROR);
            verify(transaction).setRollbackOnly();
            verify(localPassword, never()).isValid(any(), any(), any());
        }

        @Test
        void shouldPreserveLocalPasswordFallbackGivenRefreshIsDisabled() {
            var localPassword = mock(LocalPasswordProvider.class);
            var manager = keycloakCredentialManager(localPassword);
            config.putSingle(UPDATE_USER_TOTPS_ON_LOGIN, "false");
            passwordIsInvalid("username", "password");

            boolean result = manager.isValid(List.of(password));

            assertThat(result).isTrue();
            verify(localPassword).isValid(realmModel, userModel, password);
            verify(userModelFactory, never()).synchronizeTotps(any(), any());
        }

        @Test
        void shouldReuseLegacyResponseForOtherRefreshOptions() {
            config.putSingle(UPDATE_USER_ON_LOGIN, "true");
            config.putSingle(UPDATE_USER_GROUPS_ON_LOGIN, "SYNC_EVERY_LOGIN");
            config.putSingle(UPDATE_USER_ROLES_ON_LOGIN, "SYNC_EVERY_LOGIN");

            boolean result = legacyProvider.isValid(realmModel, userModel, password);

            assertThat(result).isTrue();
            verify(legacyUserService).findByUsername("username");
            var order = inOrder(userModelFactory);
            order.verify(userModelFactory).synchronizeTotps(legacyUser, userModel);
            order.verify(userModelFactory).updateUserAttributes(legacyUser, userModel);
            order.verify(userModelFactory).synchronizeGroups(legacyUser, realmModel, userModel);
            order.verify(userModelFactory).synchronizeRoles(legacyUser, realmModel, userModel);
        }

        @Test
        void shouldNotSynchronizeGivenOptionIsAbsent() {
            config.remove(UPDATE_USER_TOTPS_ON_LOGIN);

            boolean result = legacyProvider.isValid(realmModel, userModel, password);

            assertThat(result).isTrue();
            verify(legacyUserService, never()).findByUsername(anyString());
            verifyNoInteractions(userModelFactory);
        }

        @Test
        void shouldAbortWithoutFetchingTotpsGivenPasswordIsInvalid() {
            passwordIsInvalid("username", "password");

            assertThatThrownBy(() -> legacyProvider.isValid(realmModel, userModel, password))
                    .isInstanceOf(AuthenticationFlowException.class)
                    .extracting(AuthenticationFlowException.class::cast)
                    .extracting(AuthenticationFlowException::getError)
                    .isEqualTo(AuthenticationFlowError.INVALID_CREDENTIALS);
            verify(legacyUserService, never()).findByUsername(anyString());
            verifyNoInteractions(credentials, userModelFactory);
        }

        @Test
        void shouldAbortAndRollBackGivenLegacyUserIsUnavailable() {
            noUserExistsInKeycloak();
            noUsersExistInLegacySystem();
            noUsersExistInLegacySystem();

            assertThatThrownBy(() -> legacyProvider.isValid(realmModel, userModel, password))
                    .isInstanceOf(AuthenticationFlowException.class)
                    .extracting(AuthenticationFlowException.class::cast)
                    .extracting(AuthenticationFlowException::getError)
                    .isEqualTo(AuthenticationFlowError.INTERNAL_ERROR);
            verify(transaction).setRollbackOnly();
            verifyNoInteractions(userModelFactory);
        }

        @Test
        void shouldAbortAndRollBackGivenLegacyRequestFails() {
            legacyUserServiceThrowsWhenRetrievingByUserName();

            assertThatThrownBy(() -> legacyProvider.isValid(realmModel, userModel, password))
                    .isInstanceOf(AuthenticationFlowException.class)
                    .extracting(AuthenticationFlowException.class::cast)
                    .extracting(AuthenticationFlowException::getError)
                    .isEqualTo(AuthenticationFlowError.INTERNAL_ERROR);
            verify(transaction).setRollbackOnly();
        }

        private void legacyUserServiceThrowsWhenRetrievingByUserName() {
            when(legacyUserService.findByUsername(any())).thenThrow(new IllegalStateException("unavailable"));
        }

        @Test
        void shouldAbortAndRollBackGivenTotpSynchronizationFails() {
            totpSynchronizationThrows();

            assertThatThrownBy(() -> legacyProvider.isValid(realmModel, userModel, password))
                    .isInstanceOf(AuthenticationFlowException.class)
                    .extracting(AuthenticationFlowException.class::cast)
                    .extracting(AuthenticationFlowException::getError)
                    .isEqualTo(AuthenticationFlowError.INTERNAL_ERROR);
            verify(transaction).setRollbackOnly();
        }

        private void totpSynchronizationThrows() {
            doThrow(new IllegalArgumentException("Exception"))
                    .when(userModelFactory).synchronizeTotps(legacyUser, userModel);
        }

        @Test
        void shouldNotSwallowTotpFailuresGivenProfileRefreshIsEnabled() {
            config.putSingle(UPDATE_USER_ON_LOGIN, "true");
            totpSynchronizationThrows();

            assertThatThrownBy(() -> legacyProvider.isValid(realmModel, userModel, password))
                    .isInstanceOf(AuthenticationFlowException.class)
                    .extracting(AuthenticationFlowException.class::cast)
                    .extracting(AuthenticationFlowException::getError)
                    .isEqualTo(AuthenticationFlowError.INTERNAL_ERROR);
            verify(transaction).setRollbackOnly();
            verify(userModelFactory, never()).updateUserAttributes(any(), any());
        }

        @Test
        void shouldKeepExistingBestEffortProfileRefreshBehavior() {
            config.putSingle(UPDATE_USER_ON_LOGIN, "true");
            userModelFactoryThrowsWhenUpdatingAttributes();

            boolean result = legacyProvider.isValid(realmModel, userModel, password);

            assertThat(result).isTrue();
            verify(userModelFactory).synchronizeTotps(legacyUser, userModel);
            verify(transaction, never()).setRollbackOnly();
        }

        @Test
        void shouldRejectFederationSevering() {
            config.putSingle(SEVER_FEDERATION_LINK, "true");

            assertThatThrownBy(() -> legacyProvider.isValid(realmModel, userModel, password))
                    .isInstanceOf(AuthenticationFlowException.class)
                    .extracting(AuthenticationFlowException.class::cast)
                    .extracting(AuthenticationFlowException::getError)
                    .isEqualTo(AuthenticationFlowError.INTERNAL_ERROR);
            verify(transaction).setRollbackOnly();
            verify(legacyUserService, never()).findByUsername(anyString());
            verifyNoInteractions(userModelFactory);
        }

        @Test
        void shouldRejectMismatchedLegacyUserId() {
            userExistsInLegacySystemWithUserName(response("different-id", true), "username");

            assertThatThrownBy(() -> legacyProvider.isValid(realmModel, userModel, password))
                    .isInstanceOf(AuthenticationFlowException.class)
                    .extracting(AuthenticationFlowException.class::cast)
                    .extracting(AuthenticationFlowException::getError)
                    .isEqualTo(AuthenticationFlowError.INTERNAL_ERROR);
            verify(transaction).setRollbackOnly();
            verifyNoInteractions(userModelFactory);
        }

        @ParameterizedTest
        @NullAndEmptySource
        void shouldSupportLegacyResponsesWithoutAnId(String id) {
            LegacyUser response = response(id, true);
            userExistsInLegacySystemWithUserName(response, "username");

            boolean result = legacyProvider.isValid(realmModel, userModel, password);

            assertThat(result).isTrue();
            verify(userModelFactory).synchronizeTotps(response, userModel);
        }

        @Test
        void shouldRejectDisabledLegacyUser() {
            userExistsInLegacySystemWithUserName(response("id", false), "username");

            assertThatThrownBy(() -> legacyProvider.isValid(realmModel, userModel, password))
                    .isInstanceOf(AuthenticationFlowException.class)
                    .extracting(AuthenticationFlowException.class::cast)
                    .extracting(AuthenticationFlowException::getError)
                    .isEqualTo(AuthenticationFlowError.INTERNAL_ERROR);
            verify(transaction).setRollbackOnly();
            verifyNoInteractions(userModelFactory);
        }

        private abstract static class LocalPasswordProvider
                implements CredentialProvider<PasswordCredentialModel>, CredentialInputValidator {
            @Override
            public abstract boolean supportsCredentialType(String type);
        }

        private abstract static class LocalPasswordFactory implements CredentialProviderFactory<LocalPasswordProvider> {
        }
    }
}
