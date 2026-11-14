package com.danielfrak.code.keycloak.providers.rest.remote.usermodel;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconcileTest {

    @Nested
    class OneWay {

        @Test
        void shouldAddMissingItems() {
            Set<String> current = Set.of("A", "B");
            Set<String> desired = Set.of("A", "C", "D");
            List<String> added = new ArrayList<>();

            Reconcile.from(current)
                    .towards(desired)
                    .byKey(Function.identity())
                    .oneWay()
                    .addWith(added::add)
                    .apply();

            assertThat(added).containsExactlyInAnyOrder("C", "D");
        }

        @Test
        void shouldDoNothingInOneWayGivenNoMissingItems() {
            Set<String> current = Set.of("A", "B", "C");
            Set<String> desired = Set.of("A", "B");
            List<String> added = new ArrayList<>();

            Reconcile.from(current)
                    .towards(desired)
                    .byKey(Function.identity())
                    .oneWay()
                    .addWith(added::add)
                    .apply();

            assertThat(added).isEmpty();
        }
    }

    @Nested
    class TwoWay {

        @Test
        void shouldAddMissingAndRemoveAllowedItems() {
            Set<String> current = Set.of("A", "B");
            Set<String> desired = Set.of("A", "C");
            List<String> added = new ArrayList<>();
            List<String> removed = new ArrayList<>();

            Reconcile.from(current)
                    .towards(desired)
                    .byKey(Function.identity())
                    .twoWay()
                    .removeAllowedIf(item -> item.equals("B"))
                    .removeWith(removed::add)
                    .addWith(added::add)
                    .apply();

            assertThat(added).containsExactly("C");
            assertThat(removed).containsExactly("B");
        }

        @Test
        void shouldNotRemoveItemsGivenNotAllowed() {
            Set<String> current = Set.of("A", "B");
            Set<String> desired = Set.of("A");
            List<String> removed = new ArrayList<>();

            Reconcile.from(current)
                    .towards(desired)
                    .byKey(Function.identity())
                    .twoWay()
                    .removeAllowedIf(item -> false)
                    .removeWith(removed::add)
                    .addWith(item -> {
                    })
                    .apply();

            assertThat(removed).isEmpty();
        }
    }

    @Nested
    class InvalidConfiguration {

        @Test
        void shouldThrowExceptionGivenCurrentItemIsNull() {
            Reconcile.TerminalStep terminalStep = Reconcile.from(null)
                    .towards(emptySet())
                    .byKey(i -> "")
                    .oneWay()
                    .addWith(item -> {
                    });

            assertThatThrownBy(terminalStep::apply)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Missing current items.");
        }

        @Test
        void shouldThrowExceptionGivenDesiredItemIsNull() {
            Reconcile.TerminalStep terminalStep = Reconcile.from(emptySet())
                    .towards(null)
                    .byKey(i -> "")
                    .oneWay()
                    .addWith(item -> {
                    });

            assertThatThrownBy(terminalStep::apply)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Missing desired items.");
        }

        @Test
        void shouldThrowExceptionGivenKeyMapperIsNull() {
            Reconcile.TerminalStep terminalStep = Reconcile.from(emptySet())
                    .towards(emptySet())
                    .byKey(null)
                    .oneWay()
                    .addWith(item -> {
                    });

            assertThatThrownBy(terminalStep::apply)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Missing key mapper.");
        }

        @Test
        void shouldThrowExceptionGivenAddActionIsNull() {
            Reconcile.TerminalStep terminalStep = Reconcile.from(emptySet())
                    .towards(emptySet())
                    .byKey(i -> "")
                    .oneWay()
                    .addWith(null);

            assertThatThrownBy(terminalStep::apply)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Missing add action.");
        }
    }
}