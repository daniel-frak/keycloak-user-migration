package com.danielfrak.code.keycloak.providers.rest.remote.usermodel;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class Reconcile {

    private Reconcile() {
    }

    public static <T> CurrentStep<T> from(Set<T> currentItems) {
        return new DslBuilder<>(currentItems);
    }

    public interface CurrentStep<T> {

        DesiredStep<T> towards(Set<T> desiredItems);
    }

    public interface DesiredStep<T> {

        KeyStep<T> byKey(Function<T, String> keyMapper);
    }

    public interface KeyStep<T> {

        OneWayAddStep<T> oneWay();

        TwoWayRemoveAllowedStep<T> twoWay();
    }

    public interface OneWayAddStep<T> {

        TerminalStep addWith(Consumer<T> addAction);
    }

    public interface TwoWayRemoveAllowedStep<T> {

        TwoWayRemoveActionStep<T> removeAllowedIf(Predicate<T> removeAllowed);
    }

    public interface TwoWayRemoveActionStep<T> {

        TwoWayAddStep<T> removeWith(Consumer<T> removeAction);
    }

    public interface TwoWayAddStep<T> {

        TerminalStep addWith(Consumer<T> addAction);
    }

    public interface TerminalStep {

        void apply();
    }

    @SuppressWarnings("java:S2972")
    private static final class DslBuilder<T> implements
            CurrentStep<T>,
            DesiredStep<T>,
            KeyStep<T>,
            OneWayAddStep<T>,
            TwoWayRemoveAllowedStep<T>,
            TwoWayRemoveActionStep<T>,
            TwoWayAddStep<T>,
            TerminalStep {

        private final Set<T> currentItems;

        private Set<T> desiredItems;
        private Function<T, String> keyMapper;

        private boolean twoWay;
        private Predicate<T> removeAllowed;
        private Consumer<T> removeAction;
        private Consumer<T> addAction;

        private DslBuilder(Set<T> currentItems) {
            this.currentItems = currentItems != null
                    ? new HashSet<>(currentItems)
                    : null;
        }

        @Override
        public DesiredStep<T> towards(Set<T> desiredItems) {
            this.desiredItems = desiredItems != null
                    ? new HashSet<>(desiredItems)
                    : null;
            return this;
        }

        @Override
        public KeyStep<T> byKey(Function<T, String> keyMapper) {
            this.keyMapper = keyMapper;
            return this;
        }

        @Override
        public OneWayAddStep<T> oneWay() {
            this.twoWay = false;
            return this;
        }

        @Override
        public TwoWayRemoveAllowedStep<T> twoWay() {
            this.twoWay = true;
            return this;
        }

        @Override
        public TwoWayRemoveActionStep<T> removeAllowedIf(Predicate<T> removeAllowed) {
            this.removeAllowed = removeAllowed;
            return this;
        }

        @Override
        public TwoWayAddStep<T> removeWith(Consumer<T> removeAction) {
            this.removeAction = removeAction;
            return this;
        }

        @Override
        public TerminalStep addWith(Consumer<T> addAction) {
            this.addAction = addAction;
            return this;
        }

        @Override
        public void apply() {
            if (currentItems == null) {
                throw new IllegalStateException("Missing current items.");
            }
            if (desiredItems == null) {
                throw new IllegalStateException("Missing desired items.");
            }
            if (keyMapper == null) {
                throw new IllegalStateException("Missing key mapper.");
            }
            if (addAction == null) {
                throw new IllegalStateException("Missing add action.");
            }

            Map<String, T> currentByKey = currentItems.stream()
                    .collect(Collectors.toMap(keyMapper, Function.identity(), (a, b) -> a));
            Map<String, T> desiredByKey = desiredItems.stream()
                    .collect(Collectors.toMap(keyMapper, Function.identity(), (a, b) -> a));

            if (twoWay) {
                currentByKey.keySet().stream()
                        .filter(key -> !desiredByKey.containsKey(key))
                        .map(currentByKey::get)
                        .filter(removeAllowed)
                        .forEach(removeAction);
            }

            desiredByKey.keySet().stream()
                    .filter(key -> !currentByKey.containsKey(key))
                    .map(desiredByKey::get)
                    .forEach(addAction);
        }
    }
}
