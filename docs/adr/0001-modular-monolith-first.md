# ADR-0001: Start as a modular monolith

## Status
Accepted.

## Context
The training goal is to learn boundaries, ports, events and deployment without introducing network distribution before the domain boundaries are understood.

## Decision
Start with one Spring Boot deployable using hexagonal boundaries. RabbitMQ is used to teach asynchronous integration. A bounded context may be extracted later when there is a concrete reason.

## Consequences
- Local development remains simple.
- Transactions remain simpler inside the initial boundary.
- Students still practice asynchronous events.
- Microservice extraction becomes an architectural exercise instead of a starting assumption.
