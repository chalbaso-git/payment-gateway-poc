package com.ecommerce.payments.repository;

import com.ecommerce.payments.domain.PaymentAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {

    // Bloqueo pesimista (SELECT ... FOR UPDATE): serializa actualizaciones concurrentes
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentAttempt p JOIN FETCH p.order WHERE p.providerReference = :providerRef")
    Optional<PaymentAttempt> findByProviderReferenceForUpdate(@Param("providerRef") String providerReference);
}