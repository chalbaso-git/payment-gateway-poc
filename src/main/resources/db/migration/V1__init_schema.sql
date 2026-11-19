-- 1. Tabla principal de Órdenes
CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Tabla de Intentos de Pago (1 Orden -> N Intentos)
CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    provider VARCHAR(50) NOT NULL,
    provider_reference VARCHAR(120) UNIQUE,
    idempotency_key VARCHAR(120) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    checkout_url TEXT,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- CANDADO CRÍTICO 1: Impide a nivel de motor de BD que una orden tenga 2 pagos aprobados
CREATE UNIQUE INDEX uq_single_approved_payment_per_order
    ON payment_attempts (order_id)
    WHERE status = 'APPROVED';

-- Índice parcial para acelerar el Job de Conciliación de pagos inciertos
CREATE INDEX idx_payment_attempts_reconciliation
    ON payment_attempts (status, updated_at)
    WHERE status IN ('UNKNOWN', 'PENDING', 'INITIATING');

-- 3. Bandeja de Entrada de Webhooks (Patrón Inbox para deduplicación)
CREATE TABLE webhook_inbox (
    id UUID PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    provider_event_id VARCHAR(120) NOT NULL,
    provider_reference VARCHAR(120) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    processing_status VARCHAR(30) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    -- CANDADO CRÍTICO 2: Si la pasarela envía 2 veces el mismo evento, PostgreSQL rechaza el duplicado
    CONSTRAINT uq_provider_event UNIQUE (provider, provider_event_id)
);

-- 4. Bandeja de Salida Transaccional (Patrón Outbox)
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE status = 'PENDING';