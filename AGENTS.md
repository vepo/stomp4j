# Agent instructions (Stomp4J)

Read these before changing code or tests:

| Document | Purpose |
|----------|---------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Modules, patterns, SPI, testing, CI, feature workflow |
| [docs/README.md](docs/README.md) | User documentation index and learning paths |
| [docs/features.md](docs/features.md) | Supported capabilities checklist — keep in sync with behaviour changes |
| [docs/domain-specification.md](docs/domain-specification.md) | STOMP ubiquitous language and invariants |
| [README.md](README.md) | Client usage examples |
| [resources/roteiros/](resources/roteiros/) | Design rationale (Portuguese) |
| [.cursor/rules/](.cursor/rules/) | Cursor rules (always-on + file-scoped) |

**Workflow:** read domain spec → place code in correct module → update SPI/`module-info` if needed → **tiered tests during development** → `mvn verify` once → update [docs/features.md](docs/features.md) and other docs when public API changes → update ARCHITECTURE.md when architecture changes.

**Tests:** integration tests use Testcontainers (Docker required). Reuse `StompContainer`, `StompTestSupport`, and existing test infra; do not bypass with raw sockets when the DSL exists. Run module tests early — see `stomp4j-test-during-development.mdc`.

**Rules index:**

| Rule | Scope |
|------|-------|
| `architecture.mdc` | Always read/update ARCHITECTURE.md |
| `stomp4j-core.mdc` | Core conventions |
| `stomp4j-module-architecture.mdc` | Module layers and dependencies |
| `stomp4j-module-boundaries.mdc` | JPMS export/import rules |
| `stomp4j-java.mdc` | Java style (`**/*.java`) |
| `stomp4j-in-code-documentation.mdc` | Non-obvious comments — STOMP spec, architecture, rejected alternatives (`**/*.java`) |
| `stomp4j-format-imports.mdc` | Import hygiene |
| `stomp4j-strings.mdc` | String building |
| `stomp4j-tell-dont-ask.mdc` | Intent methods |
| `stomp4j-law-of-demeter.mdc` | No train wrecks |
| `stomp4j-no-method-bypass-allowed.mdc` | No pass-through wrappers |
| `stomp4j-tests.mdc` | Test conventions (+ production paths that affect tests) |
| `stomp4j-test-during-development.mdc` | Tiered tests during development — catch regressions early |
| `stomp4j-test-failure-diagnosis.mdc` | Diagnose failing tests (logs, root cause, STOMP alignment) |
| `stomp4j-test-docker.mdc` | Docker for Testcontainers |
| `development-experience.mdc` | Local broker and test infra |
| `domain-model.mdc` | Domain language alignment |
| `stomp-protocol-compliance.mdc` | Normative STOMP spec compliance |
| `documentation.mdc` | Keep README, features page, and docs/ in sync with API |
| `stomp4j-kafka-bridge.mdc` | Keep Kafka bridge code, config, and docs in sync |
| `stomp4j-tooling-languages.mdc` | Scripts: bash/JBang only |
| `static-analysis.mdc` | Finish gate: `mvn verify` |
