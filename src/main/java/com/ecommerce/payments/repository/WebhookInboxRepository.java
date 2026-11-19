package com.ecommerce.payments.repository;

import com.ecommerce.payments.domain.WebhookInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface WebhookInboxRepository extends JpaRepository<WebhookInbox, UUID> {

    boolean existsByProviderAndProviderEventId(String provider, String providerEventId);

    @Modifying
    @Query(value = """
            INSERT INTO webhook_inbox (id, provider, provider_event_id, provider_reference, event_type, payload, processing_status, received_at)
            VALUES (:id, :provider, :eventId, :providerRef, :eventType, :payload, 'RECEIVED', NOW())
            ON CONFLICT (provider, provider_event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfNotExists(@Param("id") UUID id,
                          @Param("provider") String provider,
                          @Param("eventId") String eventId,
                          @Param("providerRef") String providerRef,
                          @Param("eventType") String eventType,
                          @Param("payload") String payload);
}