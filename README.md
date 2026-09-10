DocFlow

DocFlow is a Java/Spring Boot backend for registering and tracking documents, with binary content persisted in object storage (MinIO) and metadata/state tracked in PostgreSQL.

The project follows a spec-driven development process: every feature starts with a written spec, an ADR for architecturally significant decisions, and acceptance criteria, before implementation — documented under specs/.

What it does
Registers a document's metadata (originalFilename, contentType, sizeBytes) via a REST endpoint.
Tracks each document through an explicit state machine: PENDING → PROCESSING → COMPLETED, or FAILED on a known failure.
Persists document binary content in MinIO (S3-compatible object storage) through a provider-neutral DocumentStorage interface, so the business logic never depends on the MinIO SDK directly.
Never reports a document as successfully stored unless the object storage confirms the write and a stable reference is persisted — partial or inconclusive results are treated as non-success, not silently upgraded.
Architecture
Domain (Document, DocumentStatus) — enforces its own invariants (no blank filename/content type, no negative size) and only exposes valid state transitions (startProcessing, markCompleted, markFailed).
Service (DocumentService) — orchestrates registration and lookup, using Spring's @Transactional boundaries.
Controller (DocumentController) — REST layer (POST /documents, GET /documents/{id}), delegating to the service and mapping to DTOs.
Storage port/adapter — DocumentStorage is the domain-facing port; MinioDocumentStorage is the MinIO adapter, translating SDK-specific failures into a small set of domain-meaningful failure types (UNAVAILABLE, REJECTED, RESULT_UNKNOWN).
Persistence — PostgreSQL via Spring Data JPA, with schema managed through Flyway migrations (V1__initial_schema.sql, V2__create_documents_table.sql, V3__add_document_storage_metadata.sql).

This separation was a deliberate decision (see specs/01-decisions/ADR/ADR-001-document-binary-storage.md): PostgreSQL and MinIO are separate systems with no distributed transaction between them, so the design leans on explicit state and reconciliation instead of pretending atomicity exists.

Stack
Backend: Java, Spring Boot (Web MVC, Data JPA, Validation, Actuator, Flyway)
Database: PostgreSQL
Object storage: MinIO
Testing: JUnit, Testcontainers (PostgreSQL) for integration tests
Infra: Docker Compose (infra/docker-compose.yml) for local Postgres + MinIO
Frontend: scaffolded, not yet implemented
Project status

Backend: document registration, status lifecycle, and MinIO-backed binary storage are implemented and tested (unit + integration). RabbitMQ and async processing are explicitly out of scope for the current feature set. Frontend has not been started yet.

Running locally

```bash

start Postgres + MinIO

cd infra && docker compose up -d

run the backend

cd backend ./mvnw spring-boot:run ```

Configure `infra/.env` (see `infra/.env.example`) with your Postgres and MinIO credentials before starting.

Documentation
`docs/architecture.md` — architecture overview
`docs/api.md` — API reference
`docs/database.md` — database schema
`specs/` — feature specs, ADRs, contracts, security, and verification docs per feature
