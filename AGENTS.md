# Krypton-Ecommerce — Agent Guide

Backend: **Spring Boot + Java + Maven**. Base package `pe.com.krypton`. Arquitectura de
**capas con interfaces**. Persistencia MySQL 8 + Flyway. Strict TDD activo.

Reglas compactas y contexto: ver [`.atl/skill-registry.md`](.atl/skill-registry.md).
Modelo de datos y arquitectura: ver [`docs/`](docs/).

## Skills

| Skill | Descripción | Archivo |
|-------|-------------|---------|
| `krypton-backend` | Capas con interfaces + JPA + Flyway + REST/DTO. Reglas del modelo (snapshot de precio, stock cacheado + kardex, surrogate vs natural key). | [SKILL.md](.claude/skills/krypton-backend/SKILL.md) |
| `krypton-tdd` | Strict TDD: JUnit 5 + Mockito (unit), `@WebMvcTest` (web), Testcontainers MySQL (integración). | [SKILL.md](.claude/skills/krypton-tdd/SKILL.md) |

## Convenciones

- Conventional commits únicamente. Sin atribución a IA.
- **Comentarios en español.** Identificadores (variables, métodos, campos, clases) en inglés — interoperabilidad con Spring/JPA y el contrato JSON/BD.
- Migraciones Flyway desde `V1`. `ddl-auto: validate`.
- Tests: `mvn test`. RED → GREEN → REFACTOR.
- DB local: `docker compose up -d` antes de correr la app.
