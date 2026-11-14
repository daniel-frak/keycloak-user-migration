package com.danielfrak.code.keycloak.providers.rest.archunit.production.rules;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Rules about naming low-abstraction concepts.
 */
@SuppressWarnings("unused")
public class LowLevelNamingRules {

    @ArchTest
    static final ArchRule INTERFACE_NAMES_SHOULD_NOT_START_WITH_I =
            noClasses().that().areInterfaces().should().haveNameMatching(".*\\.I[A-Z][A-Za-z0-9_-]*")
                    .because("""
                            the prefix does not add any useful information.
                            
                            Context:
                            Historically, some codebases prefix interfaces with 'I' (e.g., `IService`, `IRepository`). \
                            This practice does not add meaningful information in modern development.
                            
                            Positive consequences:
                            - Interface names will feel more natural and domain-driven, \
                            making them easier to understand at a glance.
                            """);

    @ArchTest
    static final ArchRule INTERFACE_NAMES_SHOULD_NOT_END_WITH_I =
            noClasses().that().areInterfaces().should().haveNameMatching(".*I")
                    .because("""
                            the suffix does not add any useful information.
                            
                            Context:
                            Historically, some codebases suffix interfaces with 'I' (e.g., `IService`, `IRepository`). \
                            This practice does not add meaningful information in modern development.
                            
                            Positive consequences:
                            - Interface names will feel more natural and domain-driven, \
                            making them easier to understand at a glance.
                            """);

    @ArchTest
    static final ArchRule INTERFACE_NAMES_SHOULD_NOT_CONTAIN_THE_WORD_INTERFACE =
            noClasses().that().areInterfaces().should().haveSimpleNameContaining("Interface")
                    .because("""
                            the word does not add any useful information.
                            
                            Context:
                            Historically, some codebases add the word 'Interface' to interface names \
                            (e.g., `ServiceInterface`, `RepositoryInterface`). \
                            This practice does not add meaningful information in modern development.
                            
                            Positive consequences:
                            - Interface names will feel more natural and domain-driven, \
                            making them easier to understand at a glance.
                            """);

    @ArchTest
    static final ArchRule NO_CLASS_SHOULD_END_WITH_IMPL_UNLESS_GENERATED = noClasses().that()
            .areNotAssignableTo(annotatedWith("org.mapstruct.Mapper"))
            .should().haveSimpleNameEndingWith("Impl")
            .because("""
                    the suffix does not contain any useful information.
                    
                    Context:
                    Traditionally, some codebases use the 'Impl' suffix to indicate an implementation of an interface \
                    (e.g., `ServiceImpl`, `RepositoryImpl`). This practice likely originates from older conventions \
                    where it was difficult to mock implementations in tests and so every class had an interface \
                    to facilitate mocking.
                    However, in modern development, this practice is not necessary.
                    
                    Positive consequences:
                    - Reduces unnecessary boilerplate in naming, making the codebase cleaner and more intuitive.
                    """);
}
