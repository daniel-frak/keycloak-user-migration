package com.danielfrak.code.keycloak.providers.rest.archunit.combined.rules;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Rules that check how production code is used by tests.
 */
@SuppressWarnings("unused")
public class ProductionAndTestEncapsulationRules {

    private static final ImportOption ONLY_INCLUDE_TESTS = new ImportOption.OnlyIncludeTests();

    private static final DescribedPredicate<JavaClass> ARE_NOT_TEST_CLASSES =
            DescribedPredicate.describe("are not test classes",
                    javaClass -> !isTestClass(javaClass));

    @ArchTest
    static final ArchRule NON_PUBLIC_METHODS_SHOULD_NOT_BE_EXPOSED_ONLY_FOR_TESTS = methods()
            .that().areNotPublic()
            .and().areNotPrivate()
            .and().areDeclaredInClassesThat(ARE_NOT_TEST_CLASSES)
            .and().areDeclaredInClassesThat().doNotHaveModifier(JavaModifier.ABSTRACT)
            .should(notHaveWiderScopeOnlyForTests())
            .allowEmptyShould(true)
            .because("""
                    tests should exercise behavior through the public API \
                    so internals can be refactored without breaking them
                    """);

    private static ArchCondition<JavaMethod> notHaveWiderScopeOnlyForTests() {
        return new ArchCondition<>("not have their scope widened solely so that tests can call them") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                JavaClass topLevelClass = topLevelClassOf(method.getOwner());

                List<JavaCodeUnitAccess<?>> externalAccesses = method.getAccessesToSelf().stream()
                        .filter(access -> originatesOutside(access, topLevelClass))
                        .toList();

                boolean accessedExclusivelyByTests = !externalAccesses.isEmpty() && externalAccesses.stream()
                        .allMatch(access -> isTestClass(access.getOriginOwner()));

                if (accessedExclusivelyByTests) {
                    events.add(SimpleConditionEvent.violated(method,
                            "%s in %s is only used by tests"
                                    .formatted(method.getDescription(), method.getSourceCodeLocation())));
                }
            }
        };
    }

    private static boolean originatesOutside(JavaCodeUnitAccess<?> access, JavaClass topLevelClass) {
        return !topLevelClassOf(access.getOriginOwner()).equals(topLevelClass);
    }

    private static JavaClass topLevelClassOf(JavaClass javaClass) {
        JavaClass current = javaClass;
        while (current.getEnclosingClass().isPresent()) {
            current = current.getEnclosingClass().get();
        }
        return current;
    }

    private static boolean isTestClass(JavaClass javaClass) {
        return javaClass.getSource()
                .map(source -> ONLY_INCLUDE_TESTS.includes(Location.of(source.getUri())))
                .orElse(false);
    }
}
