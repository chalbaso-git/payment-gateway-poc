package com.ecommerce.payments.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Order() {} // Requerido por JPA

    public Order(UUID id, String customerId, BigDecimal amount, String currency) {
        this.id = id;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.status = OrderStatus.CREATED;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markAsPaid() {
        if (this.status == OrderStatus.PAID) {
            return; // Idempotente: si ya estaba pagada, no hacemos nada
        }
        this.status = OrderStatus.PAID;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC  );
    }

    public UUID getId() { return id; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
}