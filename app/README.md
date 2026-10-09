# InterviewGuide backend

## Prerequisites

- Java 21
- Docker Desktop (for PostgreSQL, Redis, and MinIO)
- Maven 3.9+

## Local startup

1. Copy the root `.env.example` to `.env` and replace every placeholder secret.
2. Start dependencies with `docker compose up -d` from the repository root.
3. Run `mvn spring-boot:run` from `app`.

The application listens on `http://localhost:8080`. Swagger UI is available at
`http://localhost:8080/swagger-ui.html` and the application health endpoint is
`GET /api/resumes/health`.

## Verification

Run `mvn test` from `app`. The repository integration test starts PostgreSQL through Testcontainers, so Docker must be available.

