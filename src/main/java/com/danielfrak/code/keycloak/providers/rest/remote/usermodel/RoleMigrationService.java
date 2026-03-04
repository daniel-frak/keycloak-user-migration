package com.danielfrak.code.keycloak.providers.rest.remote.usermodel;

import com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class RoleMigrationService {

    private static final Logger LOG = Logger.getLogger(RoleMigrationService.class);

    private final Map<String, String> newRolesByLegacyRole;
    private final List<Pattern> ignoredRoleMatchPatterns;
    private final ComponentModel model;

    public RoleMigrationService(ComponentModel model, LegacyMappingParser legacyMappingParser,
                                WildcardPatternFactory wildcardPatternFactory) {
        this.model = model;
        this.newRolesByLegacyRole = legacyMappingParser.parse(model, ConfigurationProperties.ROLE_MAP_PROPERTY);
        this.ignoredRoleMatchPatterns = wildcardPatternFactory.create(ignoredRolePatterns());
    }

    private List<String> ignoredRolePatterns() {
        List<String> configured = model.getConfig().getList(ConfigurationProperties.IGNORED_SYNC_ROLES_PROPERTY);
        if (configured == null || configured.isEmpty()) {
            return ConfigurationProperties.DEFAULT_IGNORED_SYNC_ROLES;
        }
        return configured;
    }

    public boolean isNotIgnoredRole(RoleModel roleModel) {
        String roleName = roleModel.getName();
        if (roleName == null || roleName.isBlank()) {
            return true;
        }
        return ignoredRoleMatchPatterns.stream()
                .noneMatch(pattern -> wildcardMatch(pattern, roleName));
    }

    private boolean wildcardMatch(Pattern pattern, String value) {
        return pattern.matcher(value).matches();
    }

    public Stream<RoleModel> getOrMigrateRoleModels(LegacyUser legacyUser, RealmModel realm) {
        if (legacyUser.roles() == null) {
            return Stream.empty();
        }
        return legacyUser.roles().stream()
                .map(legacyRoleName -> getOrMigrateRoleModel(realm, legacyRoleName))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    /**
     * @return A {@link RoleModel} for this role in the realm.
     * Created if not found in the realm or in any of the realm's clients.
     * Migrated only if present in the map or config enables this.
     *
     * @see ConfigurationProperties#MIGRATE_UNMAPPED_ROLES_PROPERTY
     */
    private Optional<RoleModel> getOrMigrateRoleModel(RealmModel realm, String roleName) {
        return getMappedRoleName(roleName)
                .filter(mappedRoleName -> !mappedRoleName.isBlank())
                .flatMap(mappedRoleName -> getOrMigrateMappedRoleModel(realm, mappedRoleName));
    }

    private Optional<String> getMappedRoleName(String roleName) {
        if (newRolesByLegacyRole.containsKey(roleName)) {
            return Optional.ofNullable(newRolesByLegacyRole.get(roleName));
        } else if (shouldNotMigrateUnmappedRoles()) {
            return Optional.empty();
        }
        return Optional.ofNullable(roleName);
    }

    private boolean shouldNotMigrateUnmappedRoles() {
        return !Boolean.parseBoolean(model.getConfig().getFirst(
                ConfigurationProperties.MIGRATE_UNMAPPED_ROLES_PROPERTY));
    }

    private Optional<RoleModel> getOrMigrateMappedRoleModel(RealmModel realm, String mappedRoleName) {
        return Optional.ofNullable(realm.getRole(mappedRoleName))
                .or(() -> getFirstFoundClientRoleModel(realm, mappedRoleName))
                .or(() -> addRoleToRealm(realm, mappedRoleName));
    }

    private Optional<RoleModel> getFirstFoundClientRoleModel(RealmModel realm, String roleName) {
        return realm.getClientsStream()
                .map(clientModel -> clientModel.getRole(roleName))
                .filter(Objects::nonNull)
                .findFirst();
    }

    private Optional<RoleModel> addRoleToRealm(RealmModel realm, String roleName) {
        LOG.debug(String.format("Added role %s to realm %s", roleName, realm.getName()));
        return Optional.ofNullable(realm.addRole(roleName));
    }
}
