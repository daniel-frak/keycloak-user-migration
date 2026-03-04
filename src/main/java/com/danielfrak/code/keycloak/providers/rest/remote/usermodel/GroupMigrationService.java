package com.danielfrak.code.keycloak.providers.rest.remote.usermodel;

import com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties;
import com.danielfrak.code.keycloak.providers.rest.remote.LegacyUser;
import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.RealmModel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class GroupMigrationService {

    private static final Logger LOG = Logger.getLogger(GroupMigrationService.class);

    private final Map<String, String> newGroupsByLegacyGroup;
    private final List<Pattern> ignoredGroupMatchPatterns;
    private final ComponentModel model;

    public GroupMigrationService(ComponentModel model, LegacyMappingParser legacyMappingParser,
                                 WildcardPatternFactory wildcardPatternFactory) {
        this.model = model;
        this.newGroupsByLegacyGroup = legacyMappingParser.parse(model, ConfigurationProperties.GROUP_MAP_PROPERTY);
        this.ignoredGroupMatchPatterns = wildcardPatternFactory.create(ignoredGroupPatterns());
    }

    private List<String> ignoredGroupPatterns() {
        List<String> configured = model.getConfig().getList(ConfigurationProperties.IGNORED_SYNC_GROUPS_PROPERTY);
        if (configured == null) {
            return List.of();
        }
        return configured;
    }

    public boolean isNotIgnoredGroup(GroupModel groupModel) {
        String groupName = groupModel.getName();
        if (groupName == null || groupName.isBlank()) {
            return true;
        }
        return ignoredGroupMatchPatterns.stream().noneMatch(pattern -> wildcardMatch(pattern, groupName));
    }

    private boolean wildcardMatch(Pattern pattern, String value) {
        return pattern.matcher(value).matches();
    }

    public Stream<GroupModel> getOrMigrateGroupModels(LegacyUser legacyUser, RealmModel realm) {
        if (legacyUser.groups() == null) {
            return Stream.empty();
        }

        return legacyUser.groups().stream()
                .map(group -> getOrMigrateGroupModel(realm, group))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private Optional<GroupModel> getOrMigrateGroupModel(RealmModel realm, String groupName) {
        return getMappedGroupName(groupName)
                .filter(mappedGroupName -> !mappedGroupName.isBlank())
                .map(mappedGroupName -> getOrMigrateMappedGroupModel(realm, mappedGroupName));
    }

    private Optional<String> getMappedGroupName(String groupName) {
        if (newGroupsByLegacyGroup.containsKey(groupName)) {
            return Optional.ofNullable(newGroupsByLegacyGroup.get(groupName));
        } else if (shouldNotMigrateUnmappedGroups()) {
            return Optional.empty();
        }
        return Optional.ofNullable(groupName);
    }

    private boolean shouldNotMigrateUnmappedGroups() {
        return !Boolean.parseBoolean(model.getConfig().getFirst(
                ConfigurationProperties.MIGRATE_UNMAPPED_GROUPS_PROPERTY));
    }

    private GroupModel getOrMigrateMappedGroupModel(RealmModel realm, String mappedGroupName) {
        return realm.getGroupsStream()
                .filter(g -> mappedGroupName.equalsIgnoreCase(g.getName())).findFirst()
                .map(this::getExistingGroup)
                .orElseGet(() -> createGroup(realm, mappedGroupName));
    }

    private GroupModel getExistingGroup(GroupModel g) {
        LOG.infof("Found existing group %s with id %s", g.getName(), g.getId());
        return g;
    }

    private GroupModel createGroup(RealmModel realm, String mappedGroupName) {
        GroupModel newGroup = realm.createGroup(mappedGroupName);
        LOG.infof("Created group %s with id %s", newGroup.getName(), newGroup.getId());
        return newGroup;
    }
}
