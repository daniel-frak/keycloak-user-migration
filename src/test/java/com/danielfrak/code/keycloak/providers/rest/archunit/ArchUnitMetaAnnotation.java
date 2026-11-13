package com.danielfrak.code.keycloak.providers.rest.archunit;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * ArchUnit's {@link JavaMethod#tryGetAnnotationOfType(Class)} does not support meta annotations.
 * <p>
 * This class provides a workaround for that.
 */
public final class ArchUnitMetaAnnotation {

    private ArchUnitMetaAnnotation() {
    }

    public static <T extends Annotation> Optional<T> tryGet(
            Class<T> annotationType,
            Collection<? extends JavaAnnotation<?>> annotations) {
        return tryGet(annotationType, annotations, new HashSet<>());
    }

    private static <T extends Annotation> Optional<T> tryGet(
            Class<T> annotationType,
            Collection<? extends JavaAnnotation<?>> annotations, Set<String> visited) {
        if (annotations.isEmpty()) {
            return Optional.empty();
        }
        for (JavaAnnotation<?> annotation : annotations) {
            JavaClass rawType = annotation.getRawType();
            if (!visited.add(rawType.getName())) {
                continue;
            }

            if (rawType.isEquivalentTo(annotationType)) {
                return Optional.of(annotation.as(annotationType));
            }

            Collection<? extends JavaAnnotation<?>> metaAnnotations = rawType.getAnnotations();
            Optional<T> metaAnnotation = tryGet(annotationType, metaAnnotations, visited);
            if (metaAnnotation.isPresent()) {
                return metaAnnotation;
            }
        }

        return Optional.empty();
    }
}
