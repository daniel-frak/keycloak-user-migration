package com.danielfrak.code.keycloak.providers.rest;

import com.danielfrak.code.keycloak.providers.rest.rest.RestUser;
import com.danielfrak.code.keycloak.providers.rest.rest.RestUserService;
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

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.ROLE_MAP_PROPERTY;

public class RestProvider implements UserStorageProvider, UserLookupProvider, CredentialInputValidator {

    private static final Logger log = Logger.getLogger(RestProvider.class);

    private static final Set<String> supportedCredentialTypes = Collections.singleton(PasswordCredentialModel.TYPE);

    private final KeycloakSession session;
    private final ComponentModel model;

    private final RestUserService remoteUserService;

    /**
     * String format:
     * oldRole1:newRole1,oldRole2:newRole2
     */
    private final Map<String, String> roleMap;

    public RestProvider(KeycloakSession session, ComponentModel model,
                        RestUserService remoteUserService) {
        this.session = session;
        this.model = model;
        this.remoteUserService = remoteUserService;
        this.roleMap = getRoleMap(model);
    }

    private Map<String, String> getRoleMap(ComponentModel model) {
        Map<String, String> roleMap = new HashMap<>();
        String roleMapValue = model.getConfig().getFirst(ROLE_MAP_PROPERTY);
        String[] pairs = roleMapValue.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            roleMap.put(keyValue[0], keyValue[1]);
        }
        return roleMap;
    }

    @Override
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

    private UserModel getUserModel(RealmModel realm, String username, Supplier<Optional<RestUser>> user) {
        return user.get()
                .map(u -> toUserModel(u, realm))
                .orElseGet(() -> {
                    log.errorf("User not found in external repository: %s", username);
                    return null;
                });
    }

    private UserModel toUserModel(RestUser remoteUser, RealmModel realm) {
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

    private void validateUsernamesEqual(RestUser remoteUser, UserModel userModel) {
        if (!userModel.getUsername().equals(remoteUser.getUsername())) {
            throw new IllegalStateException(String.format("Local and remote users differ: [%s != %s]",
                    userModel.getUsername(),
                    remoteUser.getUsername()));
        }
    }

    private Stream<RoleModel> getRoleModels(RestUser remoteUser, RealmModel realm) {
        if (remoteUser.getRoles() == null) {
            return Stream.empty();
        }
        return remoteUser.getRoles().stream()
                .map(r -> getRoleModel(realm, r))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private Optional<RoleModel> getRoleModel(RealmModel realm, String role) {
        if (roleMap.containsKey(role)) {
            role = roleMap.get(role);
        }
        if (role == null || role.equals("")) {
            return Optional.empty();
        }
        RoleModel roleModel = realm.getRole(role);
        return Optional.ofNullable(roleModel);
    }

    @Override
    public UserModel getUserByEmail(String email, RealmModel realm) {
        return getUserModel(realm, email, () -> remoteUserService.findByEmail(email));
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

    @Override
    public boolean supportsCredentialType(String s) {
        return supportedCredentialTypes.contains(s);
    }
}
