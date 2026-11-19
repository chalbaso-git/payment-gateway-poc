package com.ecommerce.payments.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignatureValidatorTest {

    private static final String SECRET = "test-webhook-secret-key-2026";
    private static final long MAX_AGE_SECONDS = 300L;

    private HmacSignatureValidator validator;

    @BeforeEach
    void setUp() {
        validator = new HmacSignatureValidator(SECRET, MAX_AGE_SECONDS);
    }

    @Test
    @DisplayName("Debe validar correctamente una firma HMAC-SHA256 auténtica dentro de la ventana de tiempo")
    void shouldValidateAuthenticSignature() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String payload = "{\"event_id\":\"evt_123\",\"status\":\"APPROVED\"}";
        String signature = validator.computeHmacHex(timestamp + "." + payload);

        assertThat(validator.isValid(payload, timestamp, signature)).isTrue();
    }

    @Test
    @DisplayName("Debe aceptar firmas en mayúsculas (normalización case-insensitive)")
    void shouldAcceptUppercaseSignature() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String payload = "{\"event_id\":\"evt_123\",\"status\":\"APPROVED\"}";
        String signature = validator.computeHmacHex(timestamp + "." + payload).toUpperCase();

        assertThat(validator.isValid(payload, timestamp, signature)).isTrue();
    }

    @Test
    @DisplayName("Debe rechazar eventos antiguos fuera de la ventana permitida (protección Replay Attack)")
    void shouldRejectExpiredTimestamp() {
        String expiredTimestamp = String.valueOf(Instant.now().getEpochSecond() - 301L);
        String payload = "{\"event_id\":\"evt_123\",\"status\":\"APPROVED\"}";
        String signature = validator.computeHmacHex(expiredTimestamp + "." + payload);

        assertThat(validator.isValid(payload, expiredTimestamp, signature)).isFalse();
    }

    @Test
    @DisplayName("Debe rechazar cuando el payload es alterado después de firmarse")
    void shouldRejectTamperedPayload() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String originalPayload = "{\"event_id\":\"evt_123\",\"amount\":10000}";
        String tamperedPayload = "{\"event_id\":\"evt_123\",\"amount\":999999}";
        String signature = validator.computeHmacHex(timestamp + "." + originalPayload);

        assertThat(validator.isValid(tamperedPayload, timestamp, signature)).isFalse();
    }

    @Test
    @DisplayName("Debe rechazar encabezados nulos, vacíos o timestamp no numérico")
    void shouldRejectNullOrMalformedInputs() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String payload = "{\"event_id\":\"evt_123\"}";

        assertThat(validator.isValid(null, timestamp, "abc")).isFalse();
        assertThat(validator.isValid(payload, null, "abc")).isFalse();
        assertThat(validator.isValid(payload, timestamp, null)).isFalse();
        assertThat(validator.isValid(payload, "no-numerico", "abc")).isFalse();
        assertThat(validator.isValid(payload, timestamp, "firma-invalida")).isFalse();
    }
}