# ADR-0002: Use managed dependencies in the free cloud topology

## Status
Accepted for the academic environment.

## Decision
Use managed PostgreSQL, Redis and RabbitMQ providers in cloud while Docker Compose runs equivalent open-source services locally.

## Rationale
Students learn the difference between owning a container locally and consuming a managed service in cloud. The application only depends on URLs/credentials, so adapters remain unchanged.

## Consequences
- Provider quotas and sleep policies must be understood.
- Credentials are injected as environment variables.
- The same source code runs locally and remotely.
