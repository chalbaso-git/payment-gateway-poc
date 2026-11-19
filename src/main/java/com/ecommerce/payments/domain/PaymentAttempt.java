package com.ecommerce.payments.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_reference", unique = true)
    private String providerReference;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "checkout_url")
    private String checkoutUrl;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PaymentAttempt() {}

    public PaymentAttempt(UUID id, Order order, String provider, String idempotencyKey, BigDecimal amount) {
        this.id = id;
        this.order = order;
        this.provider = provider;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.status = PaymentStatus.CREATED;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Aplica un cambio de estado validando la máquina de estados.
     * Retorna true si el cambio se aplicó, o false si fue ignorado por ser duplicado o fuera de orden.
     */
    public boolean transitionTo(PaymentStatus newStatus, String providerRef) {
        if (this.status == newStatus) {
            return false; // Evento duplicado
        }
        if (!this.status.canTransitionTo(newStatus)) {
            return false; // Transición inválida o fuera de orden (ej. APPROVED -> PENDING)
        }
        this.status = newStatus;
        if (providerRef != null) {
            this.providerReference = providerRef;
        }
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        return true;
    }

    public UUID getId() { return id; }
    public Order getOrder() { return order; }
    public PaymentStatus getStatus() { return status; }
    public String getProviderReference() { return providerReference; }
}