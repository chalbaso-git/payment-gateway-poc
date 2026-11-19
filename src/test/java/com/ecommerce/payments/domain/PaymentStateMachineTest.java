package com.ecommerce.payments.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStateMachineTest {

    private PaymentAttempt attemptInStatus(PaymentStatus targetInitialStatus) {
        PaymentAttempt attempt = new PaymentAttempt(
                UUID.randomUUID(),
                null,
                "MOCK_GATEWAY",
                "idem-" + UUID.randomUUID(),
                new BigDecimal("150000.00")
        );
        // Avanza el intento hasta el estado inicial deseado para la prueba
        switch (targetInitialStatus) {
            case CREATED -> { /* Ya inicia en CREATED */ }
            case INITIATING -> attempt.transitionTo(PaymentStatus.INITIATING, null);
            case PENDING -> {
                attempt.transitionTo(PaymentStatus.INITIATING, null);
                attempt.transitionTo(PaymentStatus.PENDING, "tx-init");
            }
            case UNKNOWN -> {
                attempt.transitionTo(PaymentStatus.INITIATING, null);
                attempt.transitionTo(PaymentStatus.UNKNOWN, null);
            }
            case APPROVED -> {
                attempt.transitionTo(PaymentStatus.INITIATING, null);
                attempt.transitionTo(PaymentStatus.APPROVED, "tx-ok");
            }
            case REJECTED -> attempt.transitionTo(PaymentStatus.REJECTED, "tx-rej");
        }
        return attempt;
    }

    @ParameterizedTest(name = "Transición válida: {0} -> {1}")
    @CsvSource({
            "CREATED, INITIATING",
            "CREATED, REJECTED",
            "INITIATING, PENDING",
            "INITIATING, APPROVED",
            "INITIATING, REJECTED",
            "INITIATING, UNKNOWN",
            "PENDING, APPROVED",
            "PENDING, REJECTED",
            "PENDING, UNKNOWN",
            "UNKNOWN, APPROVED",
            "UNKNOWN, REJECTED"
    })
    @DisplayName("Debe permitir todas las transiciones válidas definidas en la máquina de estados")
    void shouldAllowValidTransitions(PaymentStatus current, PaymentStatus target) {
        assertThat(current.canTransitionTo(target)).isTrue();

        PaymentAttempt attempt = attemptInStatus(current);
        boolean transitioned = attempt.transitionTo(target, "ref-999");

        assertThat(transitioned).isTrue();
        assertThat(attempt.getStatus()).isEqualTo(target);
        assertThat(attempt.getProviderReference()).isEqualTo("ref-999");
    }

    @ParameterizedTest(name = "Transición bloqueada (duplicada / fuera de orden): {0} -> {1}")
    @CsvSource({
            "APPROVED, PENDING",
            "APPROVED, REJECTED",
            "APPROVED, UNKNOWN",
            "APPROVED, INITIATING",
            "APPROVED, APPROVED",
            "REJECTED, APPROVED",
            "REJECTED, PENDING",
            "REJECTED, UNKNOWN",
            "REJECTED, REJECTED",
            "PENDING, INITIATING",
            "PENDING, PENDING",
            "CREATED, APPROVED",
            "CREATED, PENDING"
    })
    @DisplayName("Debe rechazar eventos duplicados, estados terminales o fuera de orden sin mutar estado ni referencia")
    void shouldRejectInvalidOrOutOfOrderTransitions(PaymentStatus current, PaymentStatus invalidTarget) {
        assertThat(current.canTransitionTo(invalidTarget)).isFalse();

        PaymentAttempt attempt = attemptInStatus(current);
        String previousRef = attempt.getProviderReference();

        boolean transitioned = attempt.transitionTo(invalidTarget, "ref-late");

        assertThat(transitioned).isFalse();
        assertThat(attempt.getStatus()).isEqualTo(current);
        assertThat(attempt.getProviderReference()).isEqualTo(previousRef);
    }

    @Test
    @DisplayName("Los estados terminales APPROVED y REJECTED no deben tener transiciones salientes")
    void shouldHaveNoOutgoingTransitionsFromTerminalStates() {
        assertThat(PaymentStatus.APPROVED.allowedTransitions()).isEmpty();
        assertThat(PaymentStatus.REJECTED.allowedTransitions()).isEmpty();
    }
}