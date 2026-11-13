package com.danielfrak.code.keycloak.providers.rest.archunit.combined;

import com.danielfrak.code.keycloak.providers.rest.LegacyProvider;
import com.danielfrak.code.keycloak.providers.rest.archunit.combined.rules.ProductionAndTestEncapsulationRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.junit.CacheMode;

@SuppressWarnings("unused")
@AnalyzeClasses(packagesOf = LegacyProvider.class, importOptions = ImportOption.DoNotIncludeArchives.class,
        // https://www.archunit.org/userguide/html/000_Index.html#_controlling_the_cache
        cacheMode = CacheMode.PER_CLASS)
public class ArchUnitProductionAndTestCodeTest {

    @ArchTest
    static final ArchTests ENCAPSULATION = ArchTests.in(ProductionAndTestEncapsulationRules.class);
}
