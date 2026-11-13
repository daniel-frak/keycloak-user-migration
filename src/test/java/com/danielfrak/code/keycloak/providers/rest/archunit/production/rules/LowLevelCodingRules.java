package com.danielfrak.code.keycloak.providers.rest.archunit.production.rules;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.library.GeneralCodingRules.*;

/**
 * Rules about low-abstraction coding concepts.
 */
@SuppressWarnings("unused")
public class LowLevelCodingRules {

    @ArchTest
    static final ArchRule GENERIC_EXCEPTIONS_SHOULD_NOT_BE_THROWN = NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS
            .because("""
                    generic exceptions make it difficult to distinguish between different types of failures.
                    
                    Context:
                    Throwing generic exceptions such as `RuntimeException`, `Exception` or `Throwable` \
                    reduces the granularity of error handling.
                    Developers who catch these exceptions have no clear indication of what type of failure occurred.
                    This makes debugging harder, increases the risk of swallowing important errors, and reduces the \
                    ability to handle failures appropriately.
                    
                    Positive consequences:
                    - Improved debugging and troubleshooting, \
                    since developers can more easily understand the type of failure.
                    - More meaningful stack traces.
                    - Allows better error-handling strategies, where each failure type is handled appropriately.
                    - Greater maintainability, preventing vague and hard-to-trace failures in production systems.
                    
                    Negative consequences:
                    - Requires defining and maintaining a set of meaningful exception classes.
                    """);

    @ArchTest
    static final ArchRule JAVA_UTIL_LOGGING_SHOULD_NOT_BE_USED = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING
            .because("""
                    java.util.logging is outdated and lacks flexibility compared to modern logging frameworks.
                    
                    Context:
                    While `java.util.logging` is built into Java and provides basic logging capabilities, \
                    it has several limitations that make it unsuitable for modern software development. \
                    It suffers from poor configurability and performance inefficiencies, \
                    particularly in large-scale applications. \
                    More advanced logging frameworks such as SLF4J, Logback, or Log4j offer better flexibility, \
                    structured logging capabilities, and richer configuration options.
                    
                    Positive consequences:
                    - Encourages use of more modern logging frameworks, which provide more robust logging.
                    
                    Negative consequences:
                    - Introduces an additional dependency on third-party logging frameworks, requiring maintenance.
                    """);

    @ArchTest
    static final ArchRule JODA_TIME_SHOULD_NOT_BE_USED = NO_CLASSES_SHOULD_USE_JODATIME
            .because("""
                    Joda-Time is outdated and has been superseded by the java.time package introduced in Java 8.
                    
                    Context:
                    Joda-Time was widely used before Java 8 to handle date and time operations more effectively \
                    than the native Java APIs at the time. However, with the introduction of the `java.time` \
                    package (JSR-310) in Java 8, Joda-Time is no longer necessary. The modern `java.time` \
                    package provides better design, improved clarity, and enhanced functionality, eliminating \
                    many of the issues present in Joda-Time.
                    
                    Positive consequences:
                    - Encourages the use of the officially supported `java.time` package, which is part of the JDK.
                    """);

    @ArchTest
    static final ArchRule STANDARD_STREAMS_SHOULD_NOT_BE_USED = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
            .because("""
                    using standard streams directly leads to unstructured logging \
                    and reduces flexibility in input/output management.
                    
                    Context:
                    Standard streams (`System.out`, `System.err`, `System.in`) \
                    are often used for debugging or simple logging. \
                    However, relying on them for structured output can lead to poor maintainability, \
                    unfiltered console clutter, and a lack of control over log management. \
                    Modern logging frameworks such as SLF4J, Logback, or Log4j provide superior control, \
                    configurability, and integration options compared to direct usage of `System.out.println` or \
                    `System.err.println`. Similarly, using `System.in` for input handling bypasses robust input \
                    management strategies and makes code harder to test.
                    
                    Positive consequences:
                    - Ensures proper logging practices by using logging frameworks instead of standard output.
                    - Enhances testability, as logging frameworks and input handling can be mocked \
                    or redirected appropriately.
                    """);
}
