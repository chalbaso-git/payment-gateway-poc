package com.ecommerce.payments;

import com.ecommerce.payments.domain.*;
import com.ecommerce.payments.repository.*;
import com.ecommerce.payments.service.WebhookProcessingService;
import com.ecommerce.payments.service.WebhookProcessingService.WebhookResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class WebhookConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private WebhookProcessingService webhookService;

    @Test
    void shouldProcessConcurrentDuplicateWebhooksOnlyOnceAndProtectOrderState() throws Exception {
        // 1. Preparar datos: Orden y un intento de pago en estado PENDING
        UUID orderId = UUID.randomUUID();
        Order order = orderRepository.save(new Order(orderId, "CUST-001", new BigDecimal("150000.00"), "COP"));

        PaymentAttempt attempt = new PaymentAttempt(
                UUID.randomUUID(), order, "WOMPI_MOCK", "idem-key-1", new BigDecimal("150000.00")
        );
        attempt.transitionTo(PaymentStatus.INITIATING, null);
        attempt.transitionTo(PaymentStatus.PENDING, "tx-ref-999");
        paymentAttemptRepository.save(attempt);

        // 2. Simular 10 hilos simultáneos enviando el MISMO evento APPROVED en el mismo milisegundo
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<WebhookResult>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                startGate.await(); // Todos los hilos esperan la señal de salida simultánea
                return webhookService.processWebhook(
                        "WOMPI_MOCK", "evt-unique-001", "tx-ref-999",
                        PaymentStatus.APPROVED, "{\"status\":\"APPROVED\"}"
                );
            }));
        }

        startGate.countDown(); // ¡Disparo simultáneo!

        long processedCount = 0;
        long duplicateCount = 0;
        for (Future<WebhookResult> future : futures) {
            WebhookResult res = future.get(10, TimeUnit.SECONDS);
            if (res == WebhookResult.PROCESSED) processedCount++;
            if (res == WebhookResult.DUPLICATE_IGNORED) duplicateCount++;
        }
        executor.shutdown();

        // 3. Verificar garantías de consistencia
        assertThat(processedCount).isEqualTo(1);
        assertThat(duplicateCount).isEqualTo(9);

        Order updatedOrder = orderRepository.findById(Objects.requireNonNull(orderId)).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        // Solo debe haberse creado 1 evento en Outbox (no 10)
        assertThat(outboxEventRepository.count()).isEqualTo(1);

        // 4. Simular notificación tardía fuera de orden (llega PENDING después de APPROVED)
        WebhookResult lateResult = webhookService.processWebhook(
                "WOMPI_MOCK", "evt-late-002", "tx-ref-999",
                PaymentStatus.PENDING, "{\"status\":\"PENDING\"}"
        );
        assertThat(lateResult).isEqualTo(WebhookResult.OUT_OF_ORDER_IGNORED);
    }
}