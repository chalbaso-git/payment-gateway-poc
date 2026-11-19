package com.ecommerce.payments.domain;

public enum OrderStatus {
    CREATED,          // Orden recién creada, sin pago confirmado
    PAYMENT_PENDING,  // Hay un intento de pago en proceso
    PAID,             // Orden pagada exitosamente (Estado terminal)
    CANCELLED         // Orden cancelada o expirada
}