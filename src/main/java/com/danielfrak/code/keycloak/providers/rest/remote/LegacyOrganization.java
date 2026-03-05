package com.danielfrak.code.keycloak.providers.rest.remote;

import java.util.List;

public record LegacyOrganization(
        String orgName,
        String orgAlias,
        List<LegacyOrganizationDomain> domains
)
{

}
