package com.danielfrak.code.keycloak.providers.rest.archunit.test.rules;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

/**
 * Rules about creating readable tests.
  */
@SuppressWarnings("unused")
public class TestReadabilityRules {

    static final Pattern SHOULD_WORD = Pattern.compile("[sS]hould(?:[_\\W]|[A-Z])");
    static final Pattern WHEN_OR_IF = Pattern.compile("(?:[Ww]hen|[Ii]f)(?![a-z])");

    static final Set<String> STUBBING_ENTRY_POINTS = Set.of(
            "when",            // Mockito.when, LenientStubber.when
            "given",           // BDDMockito.given
            "doReturn", "doThrow", "doAnswer", "doNothing", "doCallRealMethod",
            "lenient",         // Mockito.lenient().when(...)
            "will", "willReturn", "willThrow", "willAnswer", "willDoNothing", "willCallRealMethod" // BDD style
    );
    static final List<Class<? extends Annotation>> TEST_ANNOTATIONS = List.of(
            Test.class,
            ParameterizedTest.class,
            RepeatedTest.class,
            TestFactory.class,
            TestTemplate.class
    );
    static final DescribedPredicate<JavaMethod> ARE_TEST_METHODS =
            DescribedPredicate.describe("are test methods", TestReadabilityRules::isTestMethod);
    static final int MAX_UNGROUPED_TESTS = 5;

    @ArchTest
    static final ArchRule TEST_NAMES_SHOULD_FOLLOW_PATTERN =
            methods()
                    .that().areAnnotatedWith(Test.class)
                    .or().areAnnotatedWith(ParameterizedTest.class)
                    .or().areAnnotatedWith(RepeatedTest.class)
                    // No easy way to check this for TestFactory/TestTemplate
                    .should(followShouldGivenConvention())
                    .because("""
                            consistency across the test suite improves readability and reduces
                            cognitive overhead when navigating tests, while enforcing a
                            'should ... (given) ...' structure clarifies intent by stating expected
                            behavior before test context.
                            """);

    @ArchTest
    static final ArchRule TEST_METHOD_NAMES_SHOULD_USE_GIVEN_INSTEAD_OF_WHEN_OR_IF =
            methods()
                    .that(ARE_TEST_METHODS)
                    .should(notContainWhenOrIf())
                    .because("""
                            consistency across the test suite reduces cognitive overhead when \
                            navigating tests and helps quickly identify intended behavior.
                            """);

    @ArchTest
    static final ArchRule STUBBING_SHOULD_BE_EXTRACTED_TO_METHODS =
            methods()
                    .that(DescribedPredicate.describe("are primary test methods", method ->
                            method.isAnnotatedWith(Test.class)
                                    || method.isAnnotatedWith(ParameterizedTest.class)
                                    || method.isAnnotatedWith(RepeatedTest.class)
                                    || method.isAnnotatedWith(BeforeEach.class)
                                    || method.isAnnotatedWith(BeforeAll.class)
                                    || method.isAnnotatedWith(AfterEach.class)
                                    || method.isAnnotatedWith(AfterAll.class)
                    ))
                    .should(new ArchCondition<>("have all mock stubbings extracted to methods") {
                        @Override
                        public void check(JavaMethod method, ConditionEvents events) {
                            method.getMethodCallsFromSelf().stream()
                                    .filter(call ->
                                            call.getTargetOwner().getPackageName().startsWith("org.mockito"))
                                    .filter(call -> STUBBING_ENTRY_POINTS.contains(call.getName()))
                                    .forEach(call -> events.add(SimpleConditionEvent.violated(method,
                                            "Method %s stubs directly via %s in %s".formatted(
                                                    method.getFullName(), call.getName(), call.getSourceCodeLocation())
                                    )));
                        }
                    })
                    .because("""
                            creating a higher-level DSL makes tests clearer and refactoring easier.
                            
                            Context:
                            Tests often need to stub mock logic. However, when reading a test, it's not always clear \
                            what the purpose of a given stubbing is. When mocking is complex or ubiquitous, \
                            a test may become noisy and difficult to understand.
                            
                            Extracting stubbing to methods helps reduce the size of the test method and abstract away
                            details which could otherwise overwhelm the reader. \
                            Naming the method forces the author to articulate why the stub exists, \
                            surfacing hidden assumptions.
                            
                            Arguably, some cases are simple enough that extracting stubbing methods may feel \
                            excessive, such as when a REST controller interacts with a use case. \
                            However, on the whole, this practice has a positive effect \
                            on test readability and maintainability. \
                            It would also be difficult to decide which exceptions to carve out without the rule \
                            becoming hard to reason about. Therefore, the rule is enforced strictly.
                            
                            Positive consequences:
                            - Complex tests will be easier to read and understand due to shorter method bodies \
                            and descriptive helper method names.
                            - Fewer places will need updating when a collaborator's API changes \
                            (or if the mock is replaced with a different kind of test double), due to multiple tests \
                            being able to use the same stubbing method.
                            
                            Neutral consequences:
                            - Readers must navigate one extra method call to see the raw Mockito invocation.
                            - Badly written method names may be more difficult to understand than a direct stubbing \
                            would have been.
                            
                            Negative consequences:
                            - For simple stubbing such as use case execution, extracting stubbing methods may feel \
                            excessive and unnecessary.
                            """);

    @ArchTest
    static final ArchRule LARGE_TEST_CLASSES_SHOULD_GROUP_TESTS_IN_NESTED_CLASSES =
            classes()
                    .that().areNotAnnotatedWith(Nested.class)
                    // @Nested doesn't work in abstract classes:
                    .and().doNotHaveModifier(JavaModifier.ABSTRACT)
                    .should(notHaveTooManyUngroupedTests())
                    .because("""
                            grouping related tests makes test reports easier to navigate \
                            and keeps the test class organized as it grows.
                            Small classes are exempt from this rule, \
                            since there may not be any meaningful groups for their tests.
                            """);

    @ArchTest
    static final ArchRule CLASSES_WITH_NESTED_GROUPS_SHOULD_NOT_DECLARE_TESTS_DIRECTLY =
            classes()
                    .that().areAnnotatedWith(Nested.class)
                    .should(haveEnclosingClassWithoutDirectTests())
                    .because("""
                            mixing grouped and ungrouped tests in one class makes the structure \
                            inconsistent and leaves readers unsure where a new test belongs.
                            """);

    private static ArchCondition<JavaClass> notHaveTooManyUngroupedTests() {
        return new ArchCondition<>(
                "directly declare at most " + MAX_UNGROUPED_TESTS + " tests") {

            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                long testCount = countTests(javaClass);

                if (testCount > MAX_UNGROUPED_TESTS) {
                    events.add(SimpleConditionEvent.violated(javaClass, String.format(
                            "Class %s declares %d tests directly (max %d without @Nested grouping) in %s",
                            javaClass.getFullName(),
                            testCount,
                            MAX_UNGROUPED_TESTS,
                            javaClass.getSourceCodeLocation()
                    )));
                }
            }
        };
    }

    private static long countTests(JavaClass javaClass) {
        return javaClass.getMethods().stream()
                .filter(TestReadabilityRules::isTestMethod)
                .count();
    }

    private static boolean isTestMethod(JavaMethod method) {
        return TEST_ANNOTATIONS.stream()
                .anyMatch(method::isAnnotatedWith);
    }

    private static ArchCondition<JavaClass> haveEnclosingClassWithoutDirectTests() {
        return new ArchCondition<>("have an enclosing class that declares no tests directly") {

            private final Set<JavaClass> checked = new HashSet<>();

            @Override
            public void init(Collection<JavaClass> allGroups) {
                checked.clear();
            }

            @Override
            public void check(JavaClass group, ConditionEvents events) {
                group.getEnclosingClass()
                        .filter(checked::add)
                        .ifPresent(enclosing -> enclosing.getMethods().stream()
                                .filter(TestReadabilityRules::isTestMethod)
                                .forEach(testMethod -> events.add(SimpleConditionEvent.violated(
                                        testMethod,
                                        String.format(
                                                "Test %s is declared outside the @Nested groups of its class in %s",
                                                testMethod.getFullName(),
                                                testMethod.getSourceCodeLocation()
                                        )
                                ))));
            }
        };
    }

    private static ArchCondition<JavaMethod> notContainWhenOrIf() {
        return new ArchCondition<>("not contain 'When' or 'If' in name or display name") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Optional<String> displayName = resolveTestDisplayName(method);
                boolean methodContainsWhen = containsWhenOrIfAfterShould(method.getName());
                boolean displayContainsWhen = displayName
                        .map(TestReadabilityRules::containsWhenOrIfAfterShould)
                        .orElse(false);

                if (methodContainsWhen || displayContainsWhen) {
                    events.add(SimpleConditionEvent.violated(method, String.format(
                            "Method '%s'%s should use 'Given' instead of 'When' or 'If' in %s",
                            method.getOwner().getFullName() + "#" + method.getName(),
                            displayName.map(n -> " (display name: '" + n + "')").orElse(""),
                            method.getSourceCodeLocation()
                    )));
                }
            }
        };
    }

    private static boolean containsWhenOrIfAfterShould(String name) {
        Matcher shouldMatcher = SHOULD_WORD.matcher(name);
        return shouldMatcher.find()
                && WHEN_OR_IF.matcher(name)
                .region(shouldMatcher.end(), name.length())
                .find();
    }

    private static Optional<String> resolveTestDisplayName(JavaMethod method) {
        if (method.isAnnotatedWith(DisplayName.class)) {
            return Optional.of(method.getAnnotationOfType(DisplayName.class).value());
        }
        if (method.isAnnotatedWith(ParameterizedTest.class)) {
            String name = method.getAnnotationOfType(ParameterizedTest.class).name();
            if (!"{default_display_name}".equals(name) && !name.isBlank()) {
                return Optional.of(name);
            }
        }
        if (method.isAnnotatedWith(RepeatedTest.class)) {
            String name = method.getAnnotationOfType(RepeatedTest.class).name();
            if (!name.equals(RepeatedTest.SHORT_DISPLAY_NAME)
                    && !name.equals(RepeatedTest.DISPLAY_NAME_PLACEHOLDER)) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    private static ArchCondition<JavaMethod> followShouldGivenConvention() {
        return new ArchCondition<>("follow 'should...(given)' naming convention") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Optional<String> displayName = resolveTestDisplayName(method);
                boolean methodFollowsConvention = followsShouldGivenConvention(method.getName());
                Boolean displayNameFollowsConvention = displayName
                        .map(this::followsShouldGivenConvention)
                        .orElse(false);

                if (!methodFollowsConvention && !displayNameFollowsConvention) {
                    events.add(SimpleConditionEvent.violated(method, String.format(
                            "Method '%s'%s fails the 'should...given' pattern in %s",
                            method.getOwner().getFullName() + "#" + method.getName(),
                            displayName.map(n -> " (display name: '" + n + "')").orElse(""),
                            method.getSourceCodeLocation()
                    )));
                }
            }

            private boolean followsShouldGivenConvention(String name) {
                String lower = name.toLowerCase(Locale.ROOT);
                int lastShouldIndex = lower.lastIndexOf("should");
                if (lastShouldIndex < 0) {
                    return false;
                }
                int firstGivenIndex = lower.indexOf("given");
                return firstGivenIndex < 0 || firstGivenIndex > lastShouldIndex;
            }
        };
    }
}
