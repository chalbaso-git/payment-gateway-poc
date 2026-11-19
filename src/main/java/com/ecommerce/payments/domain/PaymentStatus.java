package com.ecommerce.payments.domain;

import java.util.Set;

public enum PaymentStatus {
    CREATED,     // Intento creado en nuestra BD antes de contactar a la pasarela
    INITIATING,  // Llamando actualmente a la pasarela externa
    PENDING,     // La pasarela respondió y espera que el usuario pague (redirección)
    APPROVED,    // Pago confirmado y aprobado (TERMINAL)
    REJECTED,    // Pago rechazado por fondos, banco o antifraude (TERMINAL)
    UNKNOWN;     // Hubo un timeout o caída de red; resultado incierto

    // Máquina de Estados: define exactamente qué transiciones son válidas
    public Set<PaymentStatus> allowedTransitions() {
        return switch (this) {
            case CREATED -> Set.of(INITIATING, REJECTED);
            case INITIATING -> Set.of(PENDING, APPROVED, REJECTED, UNKNOWN);
            case PENDING -> Set.of(APPROVED, REJECTED, UNKNOWN);
            case UNKNOWN -> Set.of(APPROVED, REJECTED);
            case APPROVED, REJECTED -> Set.of(); // Estados terminales: inmutables
        };
    }

    public boolean canTransitionTo(PaymentStatus target) {
        return allowedTransitions().contains(target);
    }
}