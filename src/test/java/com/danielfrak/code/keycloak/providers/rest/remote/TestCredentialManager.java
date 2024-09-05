package com.danielfrak.code.keycloak.providers.rest.remote;

import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.SubjectCredentialManager;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public class TestCredentialManager implements SubjectCredentialManager {
    private final Set<CredentialModel> storedCredentials = new HashSet<>();

    @Override
    public boolean isValid(List<CredentialInput> inputs) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean updateCredential(CredentialInput input) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void updateStoredCredential(CredentialModel cred) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public CredentialModel createStoredCredential(CredentialModel cred) {
        this.storedCredentials.add(cred);
        return cred;
    }

    @Override
    public boolean removeStoredCredentialById(String id) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public CredentialModel getStoredCredentialById(String id) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Stream<CredentialModel> getStoredCredentialsStream() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Stream<CredentialModel> getStoredCredentialsByTypeStream(String type) {
        return this.storedCredentials.stream().filter(credentialModel -> Objects.equals(credentialModel.getType(), type));
    }

    @Override
    public CredentialModel getStoredCredentialByNameAndType(String name, String type) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean moveStoredCredentialTo(String id, String newPreviousCredentialId) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void updateCredentialLabel(String credentialId, String credentialLabel) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void disableCredentialType(String credentialType) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Stream<String> getDisableableCredentialTypesStream() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isConfiguredFor(String type) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public boolean isConfiguredLocally(String type) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public Stream<String> getConfiguredUserStorageCredentialTypesStream() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public CredentialModel createCredentialThroughProvider(CredentialModel model) {
        throw new RuntimeException("Not implemented");
    }
}
