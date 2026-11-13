package com.danielfrak.code.keycloak.providers.rest.archunit.production;

import com.danielfrak.code.keycloak.providers.rest.LegacyProvider;
import com.danielfrak.code.keycloak.providers.rest.archunit.production.rules.LowLevelCodingRules;
import com.danielfrak.code.keycloak.providers.rest.archunit.production.rules.LowLevelNamingRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.junit.CacheMode;

@SuppressWarnings("unused")
@AnalyzeClasses(packagesOf = LegacyProvider.class, importOptions = ImportOption.DoNotIncludeTests.class,
        // https://www.archunit.org/userguide/html/000_Index.html#_controlling_the_cache
        cacheMode = CacheMode.PER_CLASS)
public class ArchUnitProductionCodeTest {

    @ArchTest
    static final ArchTests LOW_LEVEL_CODING = ArchTests.in(LowLevelCodingRules.class);

    @ArchTest
    static final ArchTests LOW_LEVEL_NAMING = ArchTests.in(LowLevelNamingRules.class);
}
