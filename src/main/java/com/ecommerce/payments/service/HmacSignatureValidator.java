package com.ecommerce.payments.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class HmacSignatureValidator {

    private final byte[] secretBytes;
    private final long maxAgeSeconds;

    public HmacSignatureValidator(
            @Value("${webhook.security.hmac-secret}") String secret,
            @Value("${webhook.security.max-age-seconds:300}") long maxAgeSeconds) {
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.maxAgeSeconds = maxAgeSeconds;
    }

    public boolean isValid(String rawPayload, String timestampHeader, String signatureHeader) {
        if (rawPayload == null || timestampHeader == null || signatureHeader == null) {
            return false;
        }
        try {
            // 1. Protección contra Replay Attacks (rechaza eventos viejos > 5 minutos)
            long eventEpochSeconds = Long.parseLong(timestampHeader);
            long age = Math.abs(Instant.now().getEpochSecond() - eventEpochSeconds);
            if (age > maxAgeSeconds) {
                return false;
            }

            // 2. Calcula HMAC-SHA256 sobre "timestamp.payload"
            String signedData = timestampHeader + "." + rawPayload;
            String expectedSignature = computeHmacHex(signedData);

            // 3. Comparación en tiempo constante (previene Timing Attacks)
            return MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.toLowerCase().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception _) {
            return false;
        }
    }

    public String computeHmacHex(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Error calculando firma HMAC", e);
        }
    }
}