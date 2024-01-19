package com.danielfrak.code.keycloak.providers.rest.remote;

import org.keycloak.models.ClientModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.models.UserModel;

import java.util.*;
import java.util.stream.Stream;

public class TestUserModel implements UserModel {

    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private boolean isEnabled;
    private boolean isEmailVerified;
    private final Map<String, List<String>> attributes = new HashMap<>();
    private final Set<RoleModel> roles = new HashSet<>();
    private final Set<GroupModel> groups = new HashSet<>();
    private final Set<String> requiredActions = new HashSet<>();
    private String federationLink;

    public TestUserModel(String username) {
        this.username = username;
    }

    public TestUserModel(String username, String id) {
        this.username = username;
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public Long getCreatedTimestamp() {
        return null;
    }

    @Override
    public void setCreatedTimestamp(Long timestamp) {

    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String getFirstName() {
        return firstName;
    }

    @Override
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    @Override
    public String getLastName() {
        return lastName;
    }

    @Override
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    @Override
    public void setSingleAttribute(String name, String value) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        attributes.put(name, values);
    }

    @Override
    public void removeAttribute(String name) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getFirstAttribute(String name) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Stream<String> getAttributeStream(String s) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isEmailVerified() {
        return isEmailVerified;
    }

    @Override
    public void setEmailVerified(boolean emailVerified) {
        isEmailVerified = emailVerified;
    }

    @Override
    public Stream<GroupModel> getGroupsStream() {
        return this.groups.stream();
    }

    @Override
    public void joinGroup(GroupModel group) {
        groups.add(group);
    }

    @Override
    public void leaveGroup(GroupModel group) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isMemberOf(GroupModel group) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public String getFederationLink() {
        return federationLink;
    }

    @Override
    public void setFederationLink(String link) {
        this.federationLink = link;
    }

    @Override
    public String getServiceAccountClientLink() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void setServiceAccountClientLink(String clientInternalId) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public SubjectCredentialManager credentialManager() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        return attributes;
    }

    @Override
    public Stream<String> getRequiredActionsStream() {
        return this.requiredActions.stream();
    }

    @Override
    public void addRequiredAction(String action) {
        this.requiredActions.add(action);
    }

    @Override
    public void removeRequiredAction(String action) {
        this.requiredActions.remove(action);
    }

    @Override
    public void addRequiredAction(RequiredAction action) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void removeRequiredAction(RequiredAction action) {
        throw new RuntimeException("Not implemented");
    }


    @Override
    public Stream<RoleModel> getRealmRoleMappingsStream() {
        return this.roles.stream();
    }

    @Override
    public Stream<RoleModel> getClientRoleMappingsStream(ClientModel clientModel) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean hasRole(RoleModel role) {
        return roles.contains(role);
    }

    @Override
    public void grantRole(RoleModel role) {
        roles.add(role);
    }

    @Override
    public Stream<RoleModel> getRoleMappingsStream() {
        return this.roles.stream();
    }

    @Override
    public void deleteRoleMapping(RoleModel role) {
        throw new RuntimeException("Not implemented");
    }
}
