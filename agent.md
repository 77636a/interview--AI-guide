# InterviewGuide agent guide

## Scope

This repository rebuilds the InterviewGuide main path only:

```text
resume upload and AI analysis
  -> text interview and evaluation
  -> knowledge-base vectorization and RAG answer
  -> streaming RAG chat with history
```

Do not add interview schedules, voice interview, WebSocket audio, ASR, TTS, or voice-provider configuration. The functional-chain document includes those topics for reference, while `project-rebuild-order-1-8.md` explicitly excludes them from this rebuild.

## Repository layout

```text
app/                          Spring Boot backend (Java 21, Maven)
docker-compose.yml            Local PostgreSQL, Redis, and MinIO
docker/postgresql/init/       PostgreSQL initialization scripts
docs/                         Product requirements and rebuild order
```

Backend package ownership:

```text
interview.guide.common        Shared result, errors, config, and audit code
interview.guide.infrastructure Adapters for persistence, storage, Redis, export
interview.guide.modules       Product modules: resume, interview, knowledgebase, rag chat
```

Keep controllers thin. Controllers validate transport input and delegate to services. Services own transactions and business decisions. Infrastructure owns third-party SDKs and database probes. Business modules must not construct S3, Redis, or AI clients directly.

## Current milestone

Stage 1 is implemented:

- Spring Boot with Web, Validation, JPA, PostgreSQL, Actuator, Swagger, and Testcontainers.
- PostgreSQL uses `pgvector/pgvector:pg16`; the `vector` extension is created at initialization.
- Normal JSON uses `Result<T>` with `{ code, message, data }`.
- `BusinessException` and `GlobalExceptionHandler` return stable, module-ready error codes.
- `GET /api/resumes/health` checks the application and database. Swagger UI is `/swagger-ui.html`.
- Each request has an `X-Trace-Id` response header and MDC log context.
- `AuditableEntity` provides `createdAt` and `updatedAt` for future persistent entities.

## API and error conventions

- Normal JSON endpoints return `Result.success(data)`.
- Use `BusinessException` for expected business failures. Numeric ranges: base `10xxx`, resume `20xxx`, interview `30xxx`, knowledge base `40xxx`, RAG chat `50xxx`.
- Validation failures must be HTTP 400 and use the common response shape.
- Do not expose database URLs, SQL, API keys, storage credentials, stack traces, or provider responses in client errors.
- File downloads return bytes directly. SSE endpoints return events directly. Neither is wrapped in `Result`.
- Preserve or generate a safe `X-Trace-Id`; propagate it to async messages when Redis Streams are added.

## Configuration and local run

All credentials are environment variables. Copy `.env.example` to `.env`, replace placeholders, then run from the repository root:

```powershell
docker compose up -d
Set-Location app
mvn spring-boot:run
```

Required variable names: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET`, and `APP_AI_CONFIG_ENCRYPTION_KEY`. Never commit `.env` or real credentials.

The local backend is `http://localhost:8080`; PostgreSQL is `5432`; Redis is `6379`; MinIO API and console are `9000` and `9001`.

## Implementation order

Follow the design document in sequence. Complete the acceptance checks for each step before starting the next one.

1. Foundation: completed.
2. Frontend shell: React 18, TypeScript, Vite, routing, request layer.
3. File infrastructure: safe filename cleanup, size/type/Tika validation, SHA-256, S3 abstraction.
4. Resume: upload, deduplication, storage transaction compensation, Redis Stream analysis stub, list/detail/reanalyze/delete/export.
5. LLM provider: encrypted provider configuration, separate chat and embedding clients, structured output for real resume analysis.
6. Text interview: skills, persisted sessions/answers, Redis cache, async evaluation reports.
7. Knowledge base: document ingestion, chunking, embedding, pgvector retrieval, non-stream RAG answer.
8. RAG chat: persisted sessions/messages and SSE streaming with historical context.

## Persistence and async rules

- Store instants in UTC and use `Instant`; map future enums with `@Enumerated(EnumType.STRING)`.
- Use JPA auditing through `AuditableEntity` for business tables.
- Object storage keys are server-generated, such as `resumes/{yyyy}/{MM}/{uuid}.{ext}`. Persist keys, not expiring URLs.
- For uploads: validate, hash/deduplicate, extract text, store the object, persist metadata, then enqueue the task. If persistence fails after upload, compensate by deleting the object.
- Redis Stream messages contain identifiers, task IDs, retry count, and trace IDs, never full resume text or secrets.
- Consumers must be idempotent, acknowledge only after durable state changes, retry at most three times, and record sanitized failure details.

## Testing

Run `mvn test` from `app`. The repository integration test uses Testcontainers PostgreSQL, so Docker must be running. Add controller tests for response/error contracts and integration tests for every persistent or external-resource boundary. Do not require real AI credentials in the default test suite; mock provider adapters.

