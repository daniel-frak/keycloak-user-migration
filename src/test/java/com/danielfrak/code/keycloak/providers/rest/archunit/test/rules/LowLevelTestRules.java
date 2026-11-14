package com.danielfrak.code.keycloak.providers.rest.archunit.test.rules;

import com.danielfrak.code.keycloak.providers.rest.LegacyProvider;
import com.danielfrak.code.keycloak.providers.rest.archunit.ArchUnitMetaAnnotation;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Disabled;

import java.util.*;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

/**
 * Rules about low-abstraction testing concepts.
 */
@SuppressWarnings("unused")
public class LowLevelTestRules {

    static final List<String> TEST_SUFFIXES = List.of("Test", "IT");
    static final Map<String, List<JavaClass>> PRODUCTION_CLASSES_BY_SIMPLE_NAME;

    @ArchTest
    static final ArchRule ASSERTJ_SHOULD_BE_USED_INSTEAD_OF_JUNIT_ASSERTIONS =
            noClasses().that().resideOutsideOfPackage("..archunit..")
                    .should()
                    .dependOnClassesThat()
                    .haveFullyQualifiedName(org.junit.jupiter.api.Assertions.class.getName())
                    .because("""
                            AssertJ assertions provide a more fluent and expressive API, \
                            offering a wider range of assertion options and clearer failure messages""");

    @ArchTest
    static final ArchRule DISABLED_TESTS_SHOULD_HAVE_A_REASON =
            methods().that().areMetaAnnotatedWith(Disabled.class)
                    .should(haveNonBlankDisabledReason())
                    .allowEmptyShould(true)
                    .because("""
                            disabled tests without a reason make it unclear why they were disabled \
                            and whether they should be re-enabled or removed""");

    @ArchTest
    static final ArchRule DISABLED_TEST_CLASSES_SHOULD_HAVE_A_REASON =
            classes().that().areMetaAnnotatedWith(Disabled.class)
                    .should(haveNonBlankDisabledReasonOnClass())
                    .allowEmptyShould(true)
                    .because("""
                            disabled tests without a reason make it unclear why they were disabled \
                            and whether they should be re-enabled or removed""");

    @ArchTest
    static final ArchRule TEST_CLASSES_SHOULD_RESIDE_IN_CORRECT_PACKAGE = classes()
            .that(new DescribedPredicate<>("are top-level classes") {
                @Override
                public boolean test(JavaClass c) {
                    return c.getEnclosingClass().isEmpty();
                }
            })
            .and(new DescribedPredicate<>("are test classes") {
                @Override
                public boolean test(JavaClass c) {
                    // Is a test class if a recognized suffix was successfully stripped
                    return !stripTestSuffix(c.getSimpleName()).equals(c.getSimpleName());
                }
            })
            .and(new DescribedPredicate<>("have a matching production class") {
                @Override
                public boolean test(JavaClass c) {
                    return PRODUCTION_CLASSES_BY_SIMPLE_NAME.containsKey(stripTestSuffix(c.getSimpleName()));
                }
            })
            .should(new ResideInSamePackageAsTheirProductionClassCondition())
            .because("""
                    a consistent structure makes code easier to work with.
                    
                    Context:
                    Test classes are generally created in the same package as what they're testing. \
                    However, subsequent refactorings may cause them to drift, as it's easy to forget to move the tests \
                    when moving the production class. This causes the project structure to become messy, making \
                    tests harder to find than they should be.
                    
                    Positive consequences:
                    - Test classes will be easier to find without the help of the IDE.
                    """);

    static {
        JavaClasses productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackagesOf(LegacyProvider.class);

        Map<String, List<JavaClass>> index = new HashMap<>();
        for (JavaClass clazz : productionClasses) {
            if (clazz.getEnclosingClass().isPresent()) {
                continue;
            }
            index.computeIfAbsent(clazz.getSimpleName(), unused -> new ArrayList<>()).add(clazz);
        }
        PRODUCTION_CLASSES_BY_SIMPLE_NAME = Collections.unmodifiableMap(index);
    }

    private static ArchCondition<JavaMethod> haveNonBlankDisabledReason() {
        return new ArchCondition<>("have a non-blank reason in @Disabled") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Optional<Disabled> disabled = ArchUnitMetaAnnotation.tryGet(Disabled.class, method.getAnnotations());
                if (disabled.isPresent() && disabled.get().value().isBlank()) {
                    events.add(SimpleConditionEvent.violated(method, String.format(
                            "Method '%s' is @Disabled without stating a reason in %s",
                            method.getOwner().getFullName() + "#" + method.getName(),
                            method.getSourceCodeLocation()
                    )));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> haveNonBlankDisabledReasonOnClass() {
        return new ArchCondition<>("have a non-blank reason in @Disabled") {

            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                Optional<Disabled> disabled = ArchUnitMetaAnnotation.tryGet(Disabled.class, javaClass.getAnnotations());
                if (disabled.isPresent() && disabled.get().value().isBlank()) {
                    events.add(SimpleConditionEvent.violated(javaClass, String.format(
                            "Class '%s' is @Disabled without stating a reason in %s",
                            javaClass.getFullName(),
                            javaClass.getSourceCodeLocation()
                    )));
                }
            }
        };
    }

    private static String stripTestSuffix(String simpleName) {
        for (String suffix : TEST_SUFFIXES) {
            if (simpleName.endsWith(suffix)) {
                return simpleName.substring(0, simpleName.length() - suffix.length());
            }
        }
        return simpleName;
    }

    private static class ResideInSamePackageAsTheirProductionClassCondition extends ArchCondition<JavaClass> {

        ResideInSamePackageAsTheirProductionClassCondition() {
            super("reside in the same package as their production class");
        }

        @Override
        public void check(JavaClass testClass, ConditionEvents events) {
            String testedSimpleName = stripTestSuffix(testClass.getSimpleName());
            List<JavaClass> candidates = PRODUCTION_CLASSES_BY_SIMPLE_NAME.get(testedSimpleName);

            boolean packageMatches = candidates.stream()
                    .anyMatch(pc -> pc.getPackageName().equals(testClass.getPackageName()));

            if (!packageMatches) {
                String expectedPackages = candidates.stream()
                        .map(JavaClass::getPackageName)
                        .collect(Collectors.joining("] or ["));
                String message = String.format(
                        "%s should be in package [%s] but was found in [%s] %s",
                        testClass.getSimpleName(),
                        expectedPackages,
                        testClass.getPackageName(),
                        testClass.getSourceCodeLocation()
                );
                events.add(SimpleConditionEvent.violated(testClass, message));
            }
        }
    }
}
