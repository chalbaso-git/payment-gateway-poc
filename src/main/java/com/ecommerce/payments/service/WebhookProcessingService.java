package com.ecommerce.payments.service;

import com.ecommerce.payments.domain.*;
import com.ecommerce.payments.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

import java.util.UUID;

@Service
public class WebhookProcessingService {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessingService.class);

    private final WebhookInboxRepository inboxRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final OutboxEventRepository outboxRepository;

    public WebhookProcessingService(WebhookInboxRepository inboxRepository,
                                    PaymentAttemptRepository paymentAttemptRepository,
                                    OutboxEventRepository outboxRepository) {
        this.inboxRepository = inboxRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Procesa una notificación de la pasarela garantizando:
     * 1. Deduplicación estricta por eventId (Idempotencia).
     * 2. Bloqueo pesimista sobre el pago para evitar carreras concurrentes.
     * 3. Protección contra eventos fuera de orden vía Máquina de Estados.
     * 4. Registro atómico en Outbox si el pago fue aprobado.
     */
    @Transactional
    public WebhookResult processWebhook(String provider, String eventId, String providerRef,
                                        PaymentStatus incomingStatus, String rawPayload) {
// 1. Inserción atómica idempotente (ON CONFLICT DO NOTHING evita envenenar la transacción)
        UUID inboxId = UUID.randomUUID();
        int insertedRows = inboxRepository.insertIfNotExists(
                inboxId, provider, eventId, providerRef, incomingStatus.name(), rawPayload
        );
        if (insertedRows == 0) {
            log.info("Webhook duplicado ignorado atómicamente: provider={}, eventId={}", provider, eventId);
            return WebhookResult.DUPLICATE_IGNORED;
        }

        // 3. Buscar el intento de pago con Bloqueo Pesimista (SELECT ... FOR UPDATE)
        PaymentAttempt attempt = paymentAttemptRepository.findByProviderReferenceForUpdate(providerRef)
                .orElseThrow(() -> new IllegalArgumentException("Referencia de pago no encontrada: " + providerRef));

        // 4. Aplicar transición segura según la Máquina de Estados
        boolean transitioned = attempt.transitionTo(incomingStatus, providerRef);
        if (!transitioned) {
            log.warn("Transición fuera de orden o redundante ignorada: current={}, incoming={}, attemptId={}",
                    attempt.getStatus(), incomingStatus, attempt.getId());
            inboxRepository.findById(Objects.requireNonNull(inboxId)).ifPresent(i -> i.markProcessed("IGNORED"));
            return WebhookResult.OUT_OF_ORDER_IGNORED;
        }

        // 5. Si el pago fue aprobado, actualizar orden y registrar evento en Outbox en la misma transacción
        if (incomingStatus == PaymentStatus.APPROVED) {
            Order order = attempt.getOrder();
            order.markAsPaid();

            OutboxEvent outboxEvent = new OutboxEvent(
                    UUID.randomUUID(),
                    "ORDER",
                    order.getId(),
                    "ORDER_PAID",
                    "{\"orderId\":\"" + order.getId() + "\",\"attemptId\":\"" + attempt.getId() + "\"}"
            );
            outboxRepository.save(outboxEvent);
            log.info("Pago aprobado y evento Outbox registrado: orderId={}, attemptId={}", order.getId(), attempt.getId());
        }
         
        inboxRepository.findById(Objects.requireNonNull(inboxId)).ifPresent(i -> i.markProcessed("IGNORED"));
        return WebhookResult.PROCESSED;
    }

    public enum WebhookResult {
        PROCESSED,
        DUPLICATE_IGNORED,
        OUT_OF_ORDER_IGNORED
    }
}