package com.ecommerce.payments.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "webhook_inbox")
public class WebhookInbox {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_event_id", nullable = false)
    private String providerEventId;

    @Column(name = "provider_reference", nullable = false)
    private String providerReference;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "processing_status", nullable = false)
    private String processingStatus; // RECEIVED, PROCESSED, IGNORED

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    protected WebhookInbox() {}

    public WebhookInbox(UUID id, String provider, String providerEventId, String providerReference, String eventType, String payload) {
        this.id = id;
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.providerReference = providerReference;
        this.eventType = eventType;
        this.payload = payload;
        this.processingStatus = "RECEIVED";
        this.receivedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markProcessed(String finalStatus) {
        this.processingStatus = finalStatus;
        this.processedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }
    public String getProcessingStatus() { return processingStatus; }
}