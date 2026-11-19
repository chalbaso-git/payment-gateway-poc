package com.ecommerce.payments.web;

import com.ecommerce.payments.domain.PaymentStatus;
import com.ecommerce.payments.service.HmacSignatureValidator;
import com.ecommerce.payments.service.WebhookProcessingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
public class PaymentWebhookController {

    private final HmacSignatureValidator signatureValidator;
    private final WebhookProcessingService processingService;
    private final ObjectMapper objectMapper;

    public PaymentWebhookController(HmacSignatureValidator signatureValidator,
                                    WebhookProcessingService processingService,
                                    ObjectMapper objectMapper) {
        this.signatureValidator = signatureValidator;
        this.processingService = processingService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<String> receiveWebhook(
            @PathVariable String provider,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String rawPayload) {

        // 1. Verificar autenticidad e integridad antes de tocar la base de datos
        if (!signatureValidator.isValid(rawPayload, timestamp, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Firma o timestamp inválido");
        }

        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String eventId = root.path("eventId").asText();
            String providerRef = root.path("providerReference").asText();
            PaymentStatus status = PaymentStatus.valueOf(root.path("status").asText());

            var result = processingService.processWebhook(provider, eventId, providerRef, status, rawPayload);
            return ResponseEntity.ok(result.name());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Payload inválido: " + e.getMessage());
        }
    }
}