# Integración Asíncrona con Pasarela de Pagos — Diseño Técnico y PoC

Solución de arquitectura y prueba de concepto (*Proof of Concept*) en **Java 21 + Spring Boot 3 + PostgreSQL 16** para el procesamiento asíncrono, resiliente e idempotente de pagos en plataformas de comercio electrónico.

## Contenido del Repositorio

* **`docs/diseno-tecnico.md`**: Documento completo de arquitectura, modelo de datos, políticas de resiliencia, concurrencia, seguridad, observabilidad y declaración de supuestos.
* **`docs/diagramas/`**: Archivos fuente editables en formato Mermaid (`.mmd`) para componentes, modelo entidad-relación, máquina de estados y diagramas de secuencia.
* **`src/main/`**: Implementación de referencia (*PoC*) del núcleo crítico:
  * Máquina de estados estricta (`PaymentStatus`, `PaymentAttempt`) resistente a eventos fuera de orden.
  * Patrón **Inbox** (`WebhookInbox`) con deduplicación atómica (`INSERT ... ON CONFLICT DO NOTHING`).
  * Control de concurrencia mediante bloqueo pesimista (`SELECT ... FOR UPDATE`) y candados a nivel de motor SQL (`Flyway`).
  * Patrón **Transactional Outbox** (`OutboxEvent`) para garantizar publicación atómica de eventos de negocio.
  * Validación criptográfica de Webhooks (`HMAC-SHA256` en tiempo constante + ventana anti-replay).
* **`src/test/`**: Prueba de integración concurrente con **Testcontainers (PostgreSQL 16)** simulando 10 peticiones simultáneas idénticas y eventos tardíos fuera de orden.

## Requisitos para Ejecutar la PoC

* Java 21 (o superior)
* Maven 3.9+
* Docker Desktop en ejecución (para levantar PostgreSQL automáticamente vía Testcontainers)

## Ejecución de Pruebas de Concurrencia

```bash
mvn test