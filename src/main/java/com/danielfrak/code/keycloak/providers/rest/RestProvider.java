package com.danielfrak.code.keycloak.providers.rest;

import com.danielfrak.code.keycloak.providers.rest.fakes.FakeRemoteUserService;
import com.danielfrak.code.keycloak.providers.rest.fakes.FakeUser;
import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class RestProvider implements UserStorageProvider, UserLookupProvider, CredentialInputValidator {

    private static final Logger log = Logger.getLogger(RestProvider.class);

    private static final Set<String> supportedCredentialTypes = Collections.singleton(PasswordCredentialModel.TYPE);

    private final KeycloakSession session;
    private final ComponentModel model;

    private final FakeRemoteUserService remoteUserService;

    public RestProvider(KeycloakSession session, ComponentModel model,
                        FakeRemoteUserService remoteUserService) {
        this.session = session;
        this.model = model;
        this.remoteUserService = remoteUserService;
    }

    public void close() {
    }

    @Override
    public UserModel getUserById(String id, RealmModel realm) {
        throw new RuntimeException("User lookup by id not implemented");
    }

    @Override
    public UserModel getUserByUsername(String username, RealmModel realm) {
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

        var userModel = session.userLocalStorage().addUser(realm, remoteUser.getUsername());
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

    @Override
    public UserModel getUserByEmail(String email, RealmModel realm) {
        return getUserModel(realm, email, () -> remoteUserService.findByEmail(email));
    }

    @Override
    public boolean supportsCredentialType(String s) {
        return supportedCredentialTypes.contains(s);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realmModel, UserModel userModel, String s) {
        return false;
    }

    @Override
    public boolean isValid(RealmModel realmModel, UserModel userModel, CredentialInput input) {
        if (!supportsCredentialType(input.getType())) {
            return false;
        }

        if (remoteUserService.validatePassword(userModel.getUsername(), input.getChallengeResponse())) {
            session.userCredentialManager().updateCredential(realmModel, userModel, input);
            userModel.setFederationLink(null);
            return true;
        }

        return false;
    }
}
