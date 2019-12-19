package com.danielfrak.code.keycloak.providers.test;

import com.danielfrak.code.keycloak.providers.test.fakes.FakeRemoteUserService;
import com.danielfrak.code.keycloak.providers.test.fakes.FakeUser;
import org.jboss.logging.Logger;
import org.keycloak.models.*;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class TestProvider implements UserFederationProvider {

    private static final Logger log = Logger.getLogger(TestProvider.class);

    private static final Set<String> supportedCredentialTypes = Collections.singleton(UserCredentialModel.PASSWORD);

    private final KeycloakSession session;
    private final UserFederationProviderModel model;

    private final FakeRemoteUserService remoteUserService;

    public TestProvider(KeycloakSession session, UserFederationProviderModel model,
                        FakeRemoteUserService remoteUserService) {
        this.session = session;
        this.model = model;
        this.remoteUserService = remoteUserService;
    }

    public UserModel validateAndProxy(RealmModel realmModel, UserModel userModel) {
        // Gives the provider an option to validate if user still exists in federation backend and then proxy UserModel
        // loaded from local storage. This method is called whenever a UserModel is pulled from Keycloak local storage.
        // For example, the LDAP provider proxies the UserModel and does on-demand synchronization with LDAP whenever
        // UserModel update methods are invoked. It also overrides UserModel.updateCredential for the credential
        // types it supports
        return userModel;
    }

    public boolean synchronizeRegistrations() {
        // Should user registrations be synchronized with this provider?
        // FYI, only one provider will be chosen (by priority) to have this synchronization
        return false;
    }

    public UserModel register(RealmModel realmModel, UserModel userModel) {
        return null;
    }

    public boolean removeUser(RealmModel realmModel, UserModel userModel) {
        return false;
    }

    public UserModel getUserByUsername(RealmModel realm, String username) {
        return getUserModel(realm, username, () -> remoteUserService.findByUsername(username));
    }

    private UserModel getUserModel(RealmModel realm, String username, Supplier<Optional<FakeUser>> user) {
        return user.get()
                .map(u -> toUserModel(u, realm))
                .orElseGet(() -> {
                    log.errorf("User not found in external repository: %s", username);
                    return null;
                });
    }

    private UserModel toUserModel(FakeUser remoteUser, RealmModel realm) {
        log.infof("Creating user model for: %s", remoteUser.getUsername());

        var userModel = session.userStorage().addUser(realm, remoteUser.getUsername());
        validateUsernamesEqual(remoteUser, userModel);

        userModel.setFederationLink(model.getId());
        userModel.setEnabled(remoteUser.isEnabled());
        userModel.setEmail(remoteUser.getEmail());
        userModel.setEmailVerified(remoteUser.isEmailVerified());
        userModel.setFirstName(remoteUser.getFirstName());
        userModel.setLastName(remoteUser.getLastName());

        if (remoteUser.getAttributes() != null) {
            remoteUser.getAttributes()
                    .forEach(userModel::setAttribute);
        }

        getRoleModels(remoteUser, realm)
                .forEach(userModel::grantRole);

        return userModel;
    }

    private void validateUsernamesEqual(FakeUser remoteUser, UserModel userModel) {
        if (!userModel.getUsername().equals(remoteUser.getUsername())) {
            throw new IllegalStateException(String.format("Local and remote users differ: [%s != %s]",
                    userModel.getUsername(),
                    remoteUser.getUsername()));
        }
    }

    private Stream<RoleModel> getRoleModels(FakeUser remoteUser, RealmModel realm) {
        if (remoteUser.getRoles() == null) {
            return Stream.empty();
        }
        return remoteUser.getRoles().stream()
                .map(r -> getRoleModel(realm, r))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private Optional<RoleModel> getRoleModel(RealmModel realm, String role) {
        RoleModel roleModel = realm.getRole(role);
        if (roleModel == null) {
            log.warnf("Could not find role %s", role);
        }
        return Optional.ofNullable(roleModel);
    }

    public UserModel getUserByEmail(RealmModel realm, String email) {
        return getUserModel(realm, email, () -> remoteUserService.findByEmail(email));
    }

    public List<UserModel> searchByAttributes(Map<String, String> map, RealmModel realmModel, int i) {
        // Keycloak does not search in local storage first before calling this method.
        // The implementation must check to see if user is already in local storage (KeycloakSession.userStorage())
        // before doing an import. Currently only attributes USERNAME, EMAIL, FIRST_NAME and LAST_NAME will be used.
        return Collections.emptyList();
    }

    public List<UserModel> getGroupMembers(RealmModel realmModel, GroupModel groupModel, int i, int i1) {
        // Return group members from federation storage. Useful if info about group memberships is stored
        // in the federation storage.
        // Return empty list if your federation provider doesn't support storing user-group memberships
        return Collections.emptyList();
    }

    public void preRemove(RealmModel realmModel) {

    }

    public void preRemove(RealmModel realmModel, RoleModel roleModel) {

    }

    public void preRemove(RealmModel realmModel, GroupModel groupModel) {

    }

    public boolean isValid(RealmModel realmModel, UserModel userModel) {
        return remoteUserService.userExistsByUsername(userModel.getUsername());
    }

    public Set<String> getSupportedCredentialTypes(UserModel userModel) {
        return supportedCredentialTypes;
    }

    public Set<String> getSupportedCredentialTypes() {
        return supportedCredentialTypes;
    }

    public boolean validCredentials(RealmModel realm, UserModel userModel, UserCredentialModel... credential) {
        return validCredentials(realm, userModel, Arrays.asList(credential));
    }

    public boolean validCredentials(RealmModel realmModel, UserModel userModel, List<UserCredentialModel> input) {
        // Validate credentials for this user. This method will only be called with credential parameters supported
        // by this provider

        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("UserCredentialModel list is empty or null!");
        }

        UserCredentialModel credentials = input.get(0);

        if (remoteUserService.validatePassword(userModel.getUsername(), credentials.getValue())) {
            userModel.updateCredential(credentials);
            userModel.setFederationLink(null);
            return true;
        }

        return false;
    }

    public CredentialValidationOutput validCredentials(RealmModel realm, UserCredentialModel credential) {
        return CredentialValidationOutput.failed();
    }

    public void close() {

    }
}
