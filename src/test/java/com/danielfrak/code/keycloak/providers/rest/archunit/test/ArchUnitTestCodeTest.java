package com.danielfrak.code.keycloak.providers.rest.archunit.test;

import com.danielfrak.code.keycloak.providers.rest.LegacyProvider;
import com.danielfrak.code.keycloak.providers.rest.archunit.test.rules.LowLevelTestRules;
import com.danielfrak.code.keycloak.providers.rest.archunit.test.rules.TestReadabilityRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.junit.CacheMode;

@SuppressWarnings("unused")
@AnalyzeClasses(packagesOf = LegacyProvider.class, importOptions = ImportOption.OnlyIncludeTests.class,
        // https://www.archunit.org/userguide/html/000_Index.html#_controlling_the_cache
        cacheMode = CacheMode.PER_CLASS)
public class ArchUnitTestCodeTest {

    @ArchTest
    static final ArchTests LOW_LEVEL_RULES = ArchTests.in(LowLevelTestRules.class);

    @ArchTest
    static final ArchTests READABILITY = ArchTests.in(TestReadabilityRules.class);
}
