package com.danielfrak.code.keycloak.providers.rest.remote;

import java.util.List;
import java.util.Optional;

import java.util.Collections;

public record LegacyOrganization(
        String orgName,
        String orgAlias,
        List<LegacyDomain> domains
) {
    public LegacyOrganization(String orgName, String orgAlias, List<LegacyDomain> domains) {
        this.orgName = orgName;
        this.orgAlias = orgAlias;
        this.domains = domains == null ? Collections.emptyList() : domains;
    }
}
